package com.crackedgames.craftics.level;

import com.crackedgames.craftics.network.BiomeAtlasPayload;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds and pushes the biome atlas - the guide book's record of where a run can go and what
 * each place drops.
 *
 * <h2>Nothing here is authored</h2>
 *
 * <p>Every page is derived from the live {@link BiomeTemplate} the game is actually running, at
 * the moment it is sent. Retune a loot weight, swap a mob into a pool, add a biome, ship a
 * compat override, edit the JSON in a datapack - the guide book says the new thing, with no
 * second copy of the content to update and no chance of the two disagreeing. That is the whole
 * reason the atlas is synced rather than written out as guide book pages like the rest of the
 * book: hand-written pages about numbers that move are pages that go stale.
 *
 * <p>{@link #entryFor} is the derivation, kept free of the registry and the save file so the
 * "it follows the template" property is a tested one rather than a claim.
 *
 * <p>Discovery is read from the <em>island's</em> progression, the record
 * {@code getEffectiveWorldOwner} resolves to, so party members share one atlas. Anything else
 * would be strange to play: two people who just cleared the same biome together should not
 * disagree about whether it has been visited.
 */
public final class BiomeAtlasSync {
    private BiomeAtlasSync() {}

    /**
     * Loot pools can be long, and the guide shows drops in weight order. Past a couple of dozen
     * rows the page stops being a reference and starts being a spreadsheet, so the tail - the
     * rarest, least interesting entries - is dropped rather than sent and then hidden.
     */
    static final int MAX_DROPS = 24;

    /** Send the atlas as this player currently sees it. */
    public static void send(ServerPlayerEntity player) {
        if (player == null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        UUID island = data.getEffectiveWorldOwner(player.getUuid());
        CrafticsSavedData.PlayerData pd = data.getPlayerData(island);

        List<BiomeAtlasCodec.Entry> entries = new ArrayList<>();
        for (BiomeTemplate biome : BiomeRegistry.getAllBiomes()) {
            entries.add(entryFor(biome, pd.isBiomeDiscovered(biome.biomeId),
                itemIds(biome.lootItems), BiomeAtlasSync::liveMobDrops));
        }
        ServerPlayNetworking.send(player, new BiomeAtlasPayload(BiomeAtlasCodec.encode(entries)));
    }

    /**
     * Re-send to everyone. Called after a datapack reload, which can add, remove or retune every
     * biome in the game - leaving clients showing the previous pack's loot tables until they
     * next log in would be a quietly wrong guide, which is worse than no guide.
     */
    public static void sendToAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            send(player);
        }
    }

    /**
     * Derive one atlas entry from a biome template.
     *
     * <p>Takes the loot item ids and the per-mob drop lookup rather than resolving either
     * itself, so the derivation can be exercised without a loaded item registry. That is not
     * test scaffolding for its own sake: "the atlas reports whatever the game says" is the
     * property this whole feature rests on, and it is only worth anything if something checks
     * it.
     *
     * @param lootItemIds registry ids for {@code biome.lootItems}, positionally aligned with
     *                    {@code biome.lootWeights}
     * @param mobDropLookup entity type id to that mob's drop table, or null for a mob that
     *                      drops nothing
     */
    public static BiomeAtlasCodec.Entry entryFor(BiomeTemplate biome, boolean discovered,
                                                 String[] lootItemIds,
                                                 Function<String, List<BiomeAtlasCodec.Drop>> mobDropLookup) {
        // An undiscovered biome ships as a name and nothing else. Sending its contents and
        // asking the client to hide them would put the answer on the machine of the person it
        // is being hidden from, which is not hiding it.
        if (!discovered) {
            return new BiomeAtlasCodec.Entry(biome.biomeId, biome.displayName, biome.levelCount,
                false, false, "", List.of(), List.of(), List.of(), List.of(), "", 0, List.of());
        }

        List<String> hostiles = typeIds(biome.hostileMobs);
        List<String> passives = typeIds(biome.passiveMobs);
        String bossId = biome.boss != null ? biome.boss.entityTypeId() : "";

        return new BiomeAtlasCodec.Entry(
            biome.biomeId,
            biome.displayName,
            biome.levelCount,
            true,
            biome.nightLevel,
            bossId,
            hostiles,
            passives,
            drops(lootItemIds, biome.lootWeights),
            drops(biome.enchantmentLootIds, biome.enchantmentLootWeights),
            biome.biomeEffectId == null ? "" : biome.biomeEffectId,
            biome.biomeEffectStartLevel,
            mobDrops(hostiles, passives, bossId, mobDropLookup));
    }

    /**
     * Drop tables for every enemy that appears in this biome, boss included.
     *
     * <p>This is the half of a run's rewards that the biome's own loot pool says nothing about.
     * A biome pool is rolled once when a level is cleared; these are rolled once per kill, and
     * they are where the bones, the rotten flesh and the wool actually come from. A guide book
     * that listed only the biome pool would be describing a minority of what a player walks out
     * with, which is worse than describing nothing.
     *
     * <p>Order is bosses last: the roster reads hostiles, then passives, then the thing at the
     * end of the biome.
     */
    private static List<BiomeAtlasCodec.MobDrops> mobDrops(
            List<String> hostiles, List<String> passives, String bossId,
            Function<String, List<BiomeAtlasCodec.Drop>> lookup) {
        List<BiomeAtlasCodec.MobDrops> out = new ArrayList<>();
        if (lookup == null) return out;

        List<String> ordered = new ArrayList<>(hostiles);
        for (String id : passives) if (!ordered.contains(id)) ordered.add(id);
        if (bossId != null && !bossId.isEmpty() && !ordered.contains(bossId)) ordered.add(bossId);

        for (String mobId : ordered) {
            List<BiomeAtlasCodec.Drop> drops = lookup.apply(mobId);
            if (drops == null || drops.isEmpty()) continue;
            List<BiomeAtlasCodec.Drop> sorted = new ArrayList<>(drops);
            sorted.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
            out.add(new BiomeAtlasCodec.MobDrops(mobId, sorted));
        }
        return out;
    }

    /**
     * The live drop table for one enemy type, as atlas drops.
     *
     * <p>Reads {@code CombatManager.mobLootPool} - the same table the game rolls on a kill -
     * so a change to what a mob drops is a change to what the book says it drops.
     */
    private static List<BiomeAtlasCodec.Drop> liveMobDrops(String entityTypeId) {
        com.crackedgames.craftics.combat.LootPool pool;
        try {
            pool = com.crackedgames.craftics.combat.CombatManager.mobLootPool(entityTypeId);
        } catch (Exception e) {
            // A compat module resolves the modded half of that switch and can be absent or
            // unhappy. A guide book page is not worth failing a player's join over.
            return List.of();
        }
        if (pool == null) return List.of();
        List<BiomeAtlasCodec.Drop> out = new ArrayList<>();
        for (com.crackedgames.craftics.combat.LootPool.Entry e : pool.getEntries()) {
            out.add(new BiomeAtlasCodec.Drop(Registries.ITEM.getId(e.item()).toString(), e.weight()));
        }
        return out;
    }

    /** Distinct entity type ids from a spawn pool, in pool order. */
    private static List<String> typeIds(MobPoolEntry[] pool) {
        List<String> out = new ArrayList<>();
        if (pool == null) return out;
        for (MobPoolEntry mob : pool) {
            if (mob == null || mob.entityTypeId() == null) continue;
            if (!out.contains(mob.entityTypeId())) out.add(mob.entityTypeId());
        }
        return out;
    }

    /** Registry ids for a loot item array, positionally aligned with its weights. */
    private static String[] itemIds(net.minecraft.item.Item[] items) {
        if (items == null) return new String[0];
        String[] ids = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            ids[i] = items[i] == null ? "" : Registries.ITEM.getId(items[i]).toString();
        }
        return ids;
    }

    /**
     * Pair ids with weights, heaviest first, capped at {@link #MAX_DROPS}.
     *
     * <p>Tolerates the two arrays disagreeing in length rather than assuming they match. They
     * are separate fields on {@link BiomeTemplate} filled in by a datapack loader, so a
     * malformed pack can absolutely deliver ten items and nine weights, and the guide book is
     * not the right place to find that out via an exception.
     */
    static List<BiomeAtlasCodec.Drop> drops(String[] ids, int[] weights) {
        List<BiomeAtlasCodec.Drop> out = new ArrayList<>();
        if (ids == null || weights == null) return out;
        int n = Math.min(ids.length, weights.length);
        for (int i = 0; i < n; i++) {
            if (ids[i] == null || ids[i].isEmpty() || weights[i] <= 0) continue;
            out.add(new BiomeAtlasCodec.Drop(ids[i], weights[i]));
        }
        out.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        return out.size() > MAX_DROPS ? new ArrayList<>(out.subList(0, MAX_DROPS)) : out;
    }
}
