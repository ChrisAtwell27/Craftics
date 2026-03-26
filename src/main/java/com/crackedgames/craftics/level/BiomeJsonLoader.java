package com.crackedgames.craftics.level;

import com.crackedgames.craftics.CrafticsMod;
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
 * JSON Schema:
 * <pre>{@code
 * {
 *   "id": "my_biome",
 *   "name": "My Custom Biome",
 *   "order": 10,
 *   "levels": 5,
 *   "grid": {
 *     "base_width": 8,
 *     "base_height": 8,
 *     "width_growth": 1,
 *     "height_growth": 0
 *   },
 *   "floor_blocks": ["minecraft:grass_block", "minecraft:dirt"],
 *   "obstacle_blocks": ["minecraft:stone"],
 *   "obstacle_density": 0.05,
 *   "obstacle_density_growth": 0.02,
 *   "environment": "plains",
 *   "night": false,
 *   "enemies": {
 *     "passive": [
 *       {"type": "minecraft:cow", "weight": 5, "hp": 4, "attack": 0, "defense": 0, "range": 1}
 *     ],
 *     "hostile": [
 *       {"type": "minecraft:zombie", "weight": 8, "hp": 6, "attack": 2, "defense": 0, "range": 1}
 *     ],
 *     "boss": {"type": "minecraft:zombie", "hp": 15, "attack": 3, "defense": 1, "range": 1}
 *   },
 *   "loot": [
 *     {"item": "minecraft:oak_planks", "weight": 10},
 *     {"item": "minecraft:stick", "weight": 6}
 *   ]
 * }
 * }</pre>
 */
public class BiomeJsonLoader {

    private static final Gson GSON = new Gson();
    private static final String BIOME_PATH = "craftics/biomes";

    /**
     * Load all biome JSON files from all datapacks.
     * Called during server resource reload.
     */
    public static List<BiomeTemplate> loadFromResources(ResourceManager resourceManager) {
        List<BiomeTemplate> loaded = new ArrayList<>();
        Identifier prefix = Identifier.of(CrafticsMod.MOD_ID, BIOME_PATH);

        // Find all JSON files under craftics/biomes/ in all namespaces
        Map<Identifier, net.minecraft.resource.Resource> resources =
            resourceManager.findResources(BIOME_PATH, id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, net.minecraft.resource.Resource> entry : resources.entrySet()) {
            try (InputStream stream = entry.getValue().getInputStream()) {
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
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

            // Grid
            JsonObject grid = json.getAsJsonObject("grid");
            int baseWidth = grid.get("base_width").getAsInt();
            int baseHeight = grid.get("base_height").getAsInt();
            int widthGrowth = grid.has("width_growth") ? grid.get("width_growth").getAsInt() : 0;
            int heightGrowth = grid.has("height_growth") ? grid.get("height_growth").getAsInt() : 0;

            // Blocks
            Block[] floorBlocks = parseBlockArray(json.getAsJsonArray("floor_blocks"));
            Block[] obstacleBlocks = json.has("obstacle_blocks")
                ? parseBlockArray(json.getAsJsonArray("obstacle_blocks"))
                : new Block[0];

            float obstacleDensity = json.has("obstacle_density")
                ? json.get("obstacle_density").getAsFloat() : 0f;
            float obstacleDensityGrowth = json.has("obstacle_density_growth")
                ? json.get("obstacle_density_growth").getAsFloat() : 0f;

            // Environment
            String envStr = json.has("environment") ? json.get("environment").getAsString() : "PLAINS";
            EnvironmentStyle envStyle;
            try {
                envStyle = EnvironmentStyle.valueOf(envStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                CrafticsMod.LOGGER.warn("Unknown environment style '{}' in {}, defaulting to PLAINS", envStr, source);
                envStyle = EnvironmentStyle.PLAINS;
            }
            boolean night = json.has("night") && json.get("night").getAsBoolean();

            // Enemies
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

            // Loot
            JsonArray lootArray = json.getAsJsonArray("loot");
            Item[] lootItems = new Item[lootArray.size()];
            int[] lootWeights = new int[lootArray.size()];
            for (int i = 0; i < lootArray.size(); i++) {
                JsonObject lootEntry = lootArray.get(i).getAsJsonObject();
                lootItems[i] = Registries.ITEM.get(Identifier.of(lootEntry.get("item").getAsString()));
                lootWeights[i] = lootEntry.has("weight") ? lootEntry.get("weight").getAsInt() : 5;
            }

            return new BiomeTemplate(
                id, name, order, levels,
                baseWidth, baseHeight, widthGrowth, heightGrowth,
                floorBlocks, obstacleBlocks,
                obstacleDensity, obstacleDensityGrowth,
                passive, hostile, boss,
                lootItems, lootWeights,
                night, envStyle
            );
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("Error parsing biome JSON {}: {}", source, e.getMessage());
            return null;
        }
    }

    private static Block[] parseBlockArray(JsonArray arr) {
        Block[] blocks = new Block[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            blocks[i] = Registries.BLOCK.get(Identifier.of(arr.get(i).getAsString()));
        }
        return blocks;
    }

    private static MobPoolEntry[] parseMobPool(JsonArray arr, boolean passive) {
        MobPoolEntry[] entries = new MobPoolEntry[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            entries[i] = parseSingleMob(arr.get(i).getAsJsonObject(), passive);
        }
        return entries;
    }

    private static MobPoolEntry parseSingleMob(JsonObject obj, boolean passive) {
        return new MobPoolEntry(
            obj.get("type").getAsString(),
            obj.has("weight") ? obj.get("weight").getAsInt() : 1,
            obj.has("hp") ? obj.get("hp").getAsInt() : 6,
            obj.has("attack") ? obj.get("attack").getAsInt() : 2,
            obj.has("defense") ? obj.get("defense").getAsInt() : 0,
            obj.has("range") ? obj.get("range").getAsInt() : 1,
            passive
        );
    }
}
