package com.crackedgames.craftics.level;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.EnemyEntry;
import com.crackedgames.craftics.api.registry.EnemyRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads biome definitions from JSON datapacks.
 * Path: data/{namespace}/craftics/biomes/{biome_id}.json
 *
 * Datapack modders: drop a JSON file at that path to add custom biomes.
 * See the built-in biomes for the full schema. Quick reference:
 * <pre>{@code
 * {
 *   "id": "my_biome", "name": "My Biome", "order": 10, "levels": 5,
 *   "grid": { "base_width": 8, "base_height": 8, "width_growth": 1, "height_growth": 0 },
 *   "floor_blocks": ["minecraft:grass_block"],
 *   "obstacle_blocks": ["minecraft:stone"],
 *   "obstacle_density": 0.05, "obstacle_density_growth": 0.02,
 *   "environment": "plains", "night": false,
 *   "enemies": {
 *     "passive": [{"type": "minecraft:cow", "weight": 5, "hp": 4, "attack": 0, "defense": 0, "range": 1}],
 *     "hostile": [
 *       {"type": "minecraft:zombie", "weight": 8, "hp": 6, "attack": 2, "defense": 0, "range": 1, "speed": 2},
 *       {"enemy": "mymod:elite_zombie", "weight": 4}
 *     ],
 *     "boss": {"type": "minecraft:zombie", "hp": 15, "attack": 3, "defense": 1, "range": 1}
 *   },
 *   "loot": [{"item": "minecraft:oak_planks", "weight": 10}]
 * }
 * }</pre>
 *
 * Each enemy slot ({@code passive}, {@code hostile}, {@code boss}) accepts either
 * the inline form shown above ({@code "type"} plus stats) or a reference form,
 * {@code {"enemy": "<id>", "weight": N}}, that points at an {@code EnemyEntry}
 * registered from a {@code craftics/enemies/} datapack file or via the API. With
 * the reference form the template supplies appearance, AI, and stats; {@code weight}
 * stays biome-local.
 */
public class BiomeJsonLoader {

    private static final Gson GSON = new Gson();
    private static final String BIOME_PATH = "craftics/biomes";

    public static List<BiomeTemplate> loadFromResources(ResourceManager resourceManager) {
        return loadFromResources(resourceManager, BIOME_PATH);
    }

    /**
     * Load biome JSON from a directory other than the standard one.
     *
     * <p>For biomes that must only exist when something else does. Everything under
     * {@code craftics/biomes} loads on every install, so a compat module's biomes cannot live
     * there: without the mod they are for, they would still be registered - listed in the
     * atlas, counted among the biomes - with every block and mob in them missing. Kept in a
     * directory of their own, they load only when the compat asks.
     */
    public static List<BiomeTemplate> loadFromResources(ResourceManager resourceManager, String directory) {
        List<BiomeTemplate> loaded = new ArrayList<>();

        Map<Identifier, net.minecraft.resource.Resource> resources =
            resourceManager.findResources(directory, id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, net.minecraft.resource.Resource> entry : resources.entrySet()) {
            try (InputStream stream = entry.getValue().getInputStream();
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                BiomeTemplate template = parseBiome(json, entry.getKey().toString());
                if (template != null) {
                    loaded.add(template);
                    CrafticsMod.LOGGER.info("Loaded biome from datapack: {} ({})",
                        template.displayName, entry.getKey());
                }
            } catch (Exception e) {
                CrafticsMod.LOGGER.error("Failed to load biome JSON: {}", entry.getKey(), e);
            }
        }

        return loaded;
    }

    private static BiomeTemplate parseBiome(JsonObject json, String source) {
        try {
            String id = json.get("id").getAsString();
            String name = json.get("name").getAsString();
            int order = json.get("order").getAsInt();
            int levels = json.get("levels").getAsInt();

            JsonObject grid = json.getAsJsonObject("grid");
            int baseWidth = grid.get("base_width").getAsInt();
            int baseHeight = grid.get("base_height").getAsInt();
            int widthGrowth = grid.has("width_growth") ? grid.get("width_growth").getAsInt() : 0;
            int heightGrowth = grid.has("height_growth") ? grid.get("height_growth").getAsInt() : 0;

            Block[] floorBlocks = parseBlockArray(json.getAsJsonArray("floor_blocks"));
            Block[] obstacleBlocks = json.has("obstacle_blocks")
                ? parseBlockArray(json.getAsJsonArray("obstacle_blocks"))
                : new Block[0];

            float obstacleDensity = json.has("obstacle_density")
                ? json.get("obstacle_density").getAsFloat() : 0f;
            float obstacleDensityGrowth = json.has("obstacle_density_growth")
                ? json.get("obstacle_density_growth").getAsFloat() : 0f;

            String environmentId = json.has("environment")
                ? json.get("environment").getAsString().toLowerCase()
                : "plains";
            if (!com.crackedgames.craftics.api.registry.EnvironmentRegistry.isRegistered(environmentId)) {
                CrafticsMod.LOGGER.warn("Unknown environment '{}' in {} - arena will use a default theme",
                    environmentId, source);
            }
            boolean night = json.has("night") && json.get("night").getAsBoolean();

            String biomeEffectId = null;
            int biomeEffectStartLevel = 0;
            if (json.has("biomeEffect")) {
                var effObj = json.getAsJsonObject("biomeEffect");
                biomeEffectId = effObj.get("id").getAsString();
                biomeEffectStartLevel = effObj.has("startLevel")
                    ? Math.max(1, effObj.get("startLevel").getAsInt()) : 1;
            }

            JsonObject enemies = json.getAsJsonObject("enemies");
            MobPoolEntry[] passive = enemies.has("passive")
                ? parseMobPool(enemies.getAsJsonArray("passive"), true)
                : new MobPoolEntry[0];
            MobPoolEntry[] hostile = enemies.has("hostile")
                ? parseMobPool(enemies.getAsJsonArray("hostile"), false)
                : new MobPoolEntry[0];
            MobPoolEntry boss = enemies.has("boss")
                ? parseSingleMob(enemies.getAsJsonObject("boss"), false)
                : null;

            JsonArray lootArray = json.getAsJsonArray("loot");
            List<Item> lootItemList = new ArrayList<>(lootArray.size());
            List<Integer> lootWeightList = new ArrayList<>(lootArray.size());
            for (int i = 0; i < lootArray.size(); i++) {
                JsonObject lootEntry = lootArray.get(i).getAsJsonObject();
                Identifier itemId = Identifier.of(lootEntry.get("item").getAsString());
                // Skip unknown items - Registries.ITEM.get() returns AIR for an
                // unregistered id, which would otherwise enter the loot pool and
                // get handed to the player as an empty "air" reward.
                if (!Registries.ITEM.containsId(itemId)) {
                    CrafticsMod.LOGGER.warn("Unknown item '{}' in biome {}, skipping loot entry", itemId, source);
                    continue;
                }
                lootItemList.add(Registries.ITEM.get(itemId));
                lootWeightList.add(lootEntry.has("weight") ? lootEntry.get("weight").getAsInt() : 5);
            }
            Item[] lootItems = lootItemList.toArray(new Item[0]);
            int[] lootWeights = new int[lootWeightList.size()];
            for (int i = 0; i < lootWeights.length; i++) lootWeights[i] = lootWeightList.get(i);

            // Optional: restrict enchantment book drops to specific enchantments
            String[] enchantmentLootIds;
            int[] enchantmentLootWeights;
            if (json.has("enchantment_loot")) {
                JsonArray enchArray = json.getAsJsonArray("enchantment_loot");
                enchantmentLootIds = new String[enchArray.size()];
                enchantmentLootWeights = new int[enchArray.size()];
                for (int i = 0; i < enchArray.size(); i++) {
                    JsonObject e = enchArray.get(i).getAsJsonObject();
                    enchantmentLootIds[i] = e.get("enchantment").getAsString();
                    enchantmentLootWeights[i] = e.has("weight") ? e.get("weight").getAsInt() : 1;
                }
            } else {
                enchantmentLootIds = new String[0];
                enchantmentLootWeights = new int[0];
            }

            BiomeTemplate template = new BiomeTemplate(
                id, name, order, levels,
                baseWidth, baseHeight, widthGrowth, heightGrowth,
                floorBlocks, obstacleBlocks,
                obstacleDensity, obstacleDensityGrowth,
                passive, hostile, boss,
                lootItems, lootWeights,
                enchantmentLootIds, enchantmentLootWeights,
                night, environmentId,
                biomeEffectId, biomeEffectStartLevel
            );
            if (json.has("prelude")) attachPrelude(template, json, source);
            // Optional: "boss_grid": {"width": 7, "height": 7} gives the boss level a room of
            // its own size, measured like base_width.
            if (json.has("boss_grid")) {
                JsonObject room = json.getAsJsonObject("boss_grid");
                template.withBossRoom(
                    room.has("width") ? room.get("width").getAsInt() : 0,
                    room.has("height") ? room.get("height").getAsInt() : 0);
            }
            // Optional: "boss_loot": [{"item": ..., "weight": ...}], a treasure table one item
            // of which is added to what clearing the boss level pays.
            if (json.has("boss_loot")) {
                List<Item> treasure = new ArrayList<>();
                List<Integer> treasureWeights = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray("boss_loot")) {
                    JsonObject entry = element.getAsJsonObject();
                    Identifier itemId = Identifier.of(entry.get("item").getAsString());
                    if (!Registries.ITEM.containsId(itemId)) {
                        CrafticsMod.LOGGER.warn("Unknown item '{}' in biome {}, skipping boss loot entry", itemId, source);
                        continue;
                    }
                    treasure.add(Registries.ITEM.get(itemId));
                    treasureWeights.add(entry.has("weight") ? entry.get("weight").getAsInt() : 1);
                }
                int[] weights = new int[treasureWeights.size()];
                for (int i = 0; i < weights.length; i++) weights[i] = treasureWeights.get(i);
                template.withBossLoot(treasure.toArray(new Item[0]), weights);
            }
            return template;
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("Error parsing biome JSON {}: {}", source, e.getMessage());
            return null;
        }
    }

    /**
     * Opening levels that are somewhere else (see {@link BiomeTemplate#withPrelude}):
     *
     * <pre>
     *   "prelude": {
     *     "levels": 3,
     *     "name": "Aether Highlands",
     *     "grid": {...}, "floor_blocks": [...], "obstacle_blocks": [...],
     *     "obstacle_density": 0.08, "environment": "...", "night": false,
     *     "enemies": {"passive": [...], "hostile": [...]},
     *     "loot": [...]
     *   }
     * </pre>
     *
     * Everything a biome file says about how a level looks and what is in it, read by the
     * same parser. What it cannot say is who it is: the id, the level range and the boss
     * are taken from the biome it belongs to, whatever the block itself contains.
     */
    private static void attachPrelude(BiomeTemplate biome, JsonObject json, String source) {
        JsonObject prelude = json.getAsJsonObject("prelude");
        int levels = prelude.has("levels") ? prelude.get("levels").getAsInt() : 0;
        if (levels <= 0) return;

        JsonObject asBiome = prelude.deepCopy();
        asBiome.remove("prelude");
        asBiome.add("id", json.get("id"));
        asBiome.add("order", json.get("order"));
        asBiome.add("levels", json.get("levels"));
        if (!asBiome.has("name")) asBiome.add("name", json.get("name"));
        JsonObject enemies = asBiome.has("enemies") ? asBiome.getAsJsonObject("enemies") : new JsonObject();
        enemies.remove("boss");
        JsonObject own = json.getAsJsonObject("enemies");
        if (own != null && own.has("boss")) enemies.add("boss", own.get("boss"));
        asBiome.add("enemies", enemies);

        BiomeTemplate look = parseBiome(asBiome, source + " (prelude)");
        if (look != null) biome.withPrelude(look, levels);
    }

    private static Block[] parseBlockArray(JsonArray arr) {
        Block[] blocks = new Block[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            Identifier blockId = Identifier.of(arr.get(i).getAsString());
            if (!Registries.BLOCK.containsId(blockId)) {
                CrafticsMod.LOGGER.warn("Unknown block '{}' in biome config", blockId);
            }
            blocks[i] = Registries.BLOCK.get(blockId);
        }
        return blocks;
    }

    private static MobPoolEntry[] parseMobPool(JsonArray arr, boolean passive) {
        List<MobPoolEntry> entries = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            MobPoolEntry entry = parseSingleMob(arr.get(i).getAsJsonObject(), passive);
            if (entry != null) entries.add(entry);
        }
        return entries.toArray(new MobPoolEntry[0]);
    }

    /**
     * Parse one enemy entry. Two forms are accepted:
     * <ul>
     *   <li><b>Reference</b> - {@code {"enemy": "<id>", "weight": N}} resolves a
     *       registered {@link EnemyEntry}. {@code weight} stays biome-local; the
     *       template supplies appearance, AI, and stats.</li>
     *   <li><b>Inline</b> - {@code {"type": "<entity>", "hp": ..., ...}} defines the
     *       enemy fully in the biome JSON (the original form).</li>
     * </ul>
     * Returns {@code null} (logged) when an {@code "enemy"} reference is unknown.
     */
    private static MobPoolEntry parseSingleMob(JsonObject obj, boolean passive) {
        if (obj.has("enemy")) {
            String ref = obj.get("enemy").getAsString();
            EnemyEntry entry = EnemyRegistry.getOrNull(ref);
            if (entry == null) {
                CrafticsMod.LOGGER.warn(
                    "Unknown enemy reference '{}' in biome JSON - skipping entry", ref);
                return null;
            }
            int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
            return new MobPoolEntry(
                entry.entityTypeId(), weight,
                entry.hp(), entry.attack(), entry.defense(), entry.range(),
                passive, entry.aiKey(), entry.speed(),
                // A pool entry may override the template's NBT; otherwise it inherits it,
                // so an enemy registered once with its tags can be dropped into any biome
                // without restating them.
                parseSpawnNbt(obj, entry.spawnNbt())
            );
        }
        String type = obj.get("type").getAsString();
        return new MobPoolEntry(
            type,
            obj.has("weight") ? obj.get("weight").getAsInt() : 1,
            obj.has("hp") ? obj.get("hp").getAsInt() : 6,
            obj.has("attack") ? obj.get("attack").getAsInt() : 2,
            obj.has("defense") ? obj.get("defense").getAsInt() : 0,
            obj.has("range") ? obj.get("range").getAsInt() : 1,
            passive,
            obj.has("ai") ? obj.get("ai").getAsString() : type,
            obj.has("speed") ? obj.get("speed").getAsInt() : 0,
            parseSpawnNbt(obj, null)
        );
    }

    /**
     * Optional {@code "nbt"} on a pool entry, written as an SNBT string - the same
     * syntax {@code /summon} takes, so it can be copied straight out of a command:
     *
     * <pre>{@code {"type": "mymod:creature", "nbt": "{Variant:3,Tame:1b}"}}</pre>
     *
     * <p>A malformed string is warned about and dropped rather than failing the biome:
     * one unparseable tag should cost that entry its extras, not take the whole level
     * definition down with it.
     *
     * @param fallback NBT inherited from an enemy template, used when the entry has none
     */
    private static net.minecraft.nbt.NbtCompound parseSpawnNbt(JsonObject obj,
                                                               net.minecraft.nbt.NbtCompound fallback) {
        if (!obj.has("nbt")) return fallback;
        String snbt = obj.get("nbt").getAsString();
        if (snbt == null || snbt.isBlank()) return fallback;
        try {
            //? if <=1.21.4 {
            return net.minecraft.nbt.StringNbtReader.parse(snbt);
            //?} else {
            /*return net.minecraft.nbt.StringNbtReader.readCompound(snbt);
            *///?}
        } catch (Exception e) {
            CrafticsMod.LOGGER.warn("Bad spawn NBT in biome JSON: {} - entry spawns without it", snbt);
            return fallback;
        }
    }
}
