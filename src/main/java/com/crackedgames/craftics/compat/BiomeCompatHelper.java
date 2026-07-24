package com.crackedgames.craftics.compat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.level.BiomeRegistry;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.MobPoolEntry;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for compat modules that need to mutate a biome's hostile mob pool
 * after {@code BiomeRegistry.loadFromDatapacks} finishes. Each mutation rebuilds
 * the {@link BiomeTemplate} with a new hostile array and re-registers it, which
 * {@link BiomeRegistry#register} handles by swapping the old entry in place
 * (preserving level numbers).
 * <p>
 * All mutations are skipped if the new entity type isn't actually registered
 * (i.e. the compat mod is present in deps but its entity wasn't loaded), so
 * the compat modules never have to gate their own calls.
 */
public final class BiomeCompatHelper {

    private BiomeCompatHelper() {}

    /**
     * Returns true if the given entity id exists in the live registry.
     * Used as a fast "mod is actually loaded + entity registered" check.
     */
    public static boolean entityExists(String entityTypeId) {
        if (entityTypeId == null) return false;
        try {
            Identifier id = Identifier.of(entityTypeId);
            return Registries.ENTITY_TYPE.containsId(id);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Rebuild {@code biome} with the given new hostile pool and re-register it. */
    private static void replaceBiomeHostile(BiomeTemplate biome, MobPoolEntry[] newPool) {
        BiomeTemplate replaced = new BiomeTemplate(
            biome.biomeId, biome.displayName, biome.startLevel, biome.levelCount,
            biome.baseWidth, biome.baseHeight, biome.widthGrowth, biome.heightGrowth,
            biome.floorBlocks, biome.obstacleBlocks,
            biome.baseObstacleDensity, biome.obstacleDensityGrowth,
            biome.passiveMobs, newPool, biome.boss,
            biome.lootItems, biome.lootWeights,
            biome.enchantmentLootIds, biome.enchantmentLootWeights,
            biome.nightLevel, biome.environmentId,
            // Carry biome-effect fields so a compat mob-pool swap doesn't drop the weather.
            biome.biomeEffectId, biome.biomeEffectStartLevel);
        BiomeRegistry.register(replaced);
    }

    /**
     * In the given biome, replace any hostile entry whose type matches
     * {@code oldEntityTypeId} with a fresh {@link MobPoolEntry} pointing at
     * {@code newEntityTypeId}. Preserves the original weight and stats so the
     * swap doesn't silently buff/nerf anything. Does nothing if the new entity
     * isn't registered or the biome has no matching entry.
     *
     * @return true if a replacement actually happened
     */
    public static boolean replaceHostileMob(String biomeId, String oldEntityTypeId, String newEntityTypeId) {
        if (!entityExists(newEntityTypeId)) return false;
        BiomeTemplate biome = findBiome(biomeId);
        if (biome == null) return false;

        MobPoolEntry[] pool = biome.hostileMobs;
        if (pool == null) return false;

        boolean changed = false;
        MobPoolEntry[] updated = new MobPoolEntry[pool.length];
        for (int i = 0; i < pool.length; i++) {
            MobPoolEntry entry = pool[i];
            if (entry != null && oldEntityTypeId.equals(entry.entityTypeId())) {
                updated[i] = new MobPoolEntry(
                    newEntityTypeId, entry.weight(), entry.baseHp(),
                    entry.baseAttack(), entry.baseDefense(),
                    entry.range(), entry.passive());
                changed = true;
            } else {
                updated[i] = entry;
            }
        }
        if (!changed) return false;

        replaceBiomeHostile(biome, updated);
        CrafticsMod.LOGGER.info("[Compat] {}: {} → {}", biomeId, oldEntityTypeId, newEntityTypeId);
        return true;
    }

    /**
     * Append a new hostile mob to a biome's pool. Skips the add if an entry
     * with the same entity id already exists (so re-running compat is safe)
     * or if the entity isn't registered.
     *
     * @return true if the mob was appended
     */
    public static boolean appendHostileMob(String biomeId, MobPoolEntry newEntry) {
        if (newEntry == null) return false;
        if (!entityExists(newEntry.entityTypeId())) return false;
        BiomeTemplate biome = findBiome(biomeId);
        if (biome == null) return false;

        MobPoolEntry[] pool = biome.hostileMobs != null ? biome.hostileMobs : new MobPoolEntry[0];
        for (MobPoolEntry existing : pool) {
            if (existing != null && newEntry.entityTypeId().equals(existing.entityTypeId())) {
                return false; // already present - don't double-add
            }
        }

        MobPoolEntry[] appended = new MobPoolEntry[pool.length + 1];
        System.arraycopy(pool, 0, appended, 0, pool.length);
        appended[pool.length] = newEntry;
        replaceBiomeHostile(biome, appended);
        CrafticsMod.LOGGER.info("[Compat] {}: added {}", biomeId, newEntry.entityTypeId());
        return true;
    }

    /** Convenience wrapper for the common "add a hostile mob with these stats" case. */
    public static boolean appendHostileMob(String biomeId, String entityTypeId, int weight,
                                             int baseHp, int baseAttack, int baseDefense, int range) {
        return appendHostileMob(biomeId,
            new MobPoolEntry(entityTypeId, weight, baseHp, baseAttack, baseDefense, range, false));
    }

    /**
     * Completely replace a biome's hostile pool with {@code newPool}. Entries
     * whose entity id isn't registered are dropped, so a full-replacement compat
     * (e.g. Deeper and Darker taking over the Deep Dark roster) only lands the
     * mobs the mod actually provides. If NOTHING in {@code newPool} is registered
     * the original pool is left untouched (never blank out a biome). Any per-mob
     * swaps a lower-priority compat already made are superseded, since this
     * rebuilds the array wholesale.
     *
     * @return true if the pool was replaced
     */
    public static boolean replaceAllHostile(String biomeId, MobPoolEntry[] newPool) {
        BiomeTemplate biome = findBiome(biomeId);
        if (biome == null || newPool == null) return false;

        List<MobPoolEntry> kept = new ArrayList<>(newPool.length);
        for (MobPoolEntry e : newPool) {
            if (e != null && entityExists(e.entityTypeId())) kept.add(e);
        }
        if (kept.isEmpty()) return false; // mod present but no entities registered - don't wipe the biome

        replaceBiomeHostile(biome, kept.toArray(new MobPoolEntry[0]));
        CrafticsMod.LOGGER.info("[Compat] {}: hostile pool fully replaced ({} mobs)", biomeId, kept.size());
        return true;
    }

    /** Rebuild {@code biome} with the given new passive pool and re-register it. */
    private static void replaceBiomePassive(BiomeTemplate biome, MobPoolEntry[] newPool) {
        BiomeTemplate replaced = new BiomeTemplate(
            biome.biomeId, biome.displayName, biome.startLevel, biome.levelCount,
            biome.baseWidth, biome.baseHeight, biome.widthGrowth, biome.heightGrowth,
            biome.floorBlocks, biome.obstacleBlocks,
            biome.baseObstacleDensity, biome.obstacleDensityGrowth,
            newPool, biome.hostileMobs, biome.boss,
            biome.lootItems, biome.lootWeights,
            biome.enchantmentLootIds, biome.enchantmentLootWeights,
            biome.nightLevel, biome.environmentId,
            // Carry biome-effect fields so a compat mob-pool swap doesn't drop the weather.
            biome.biomeEffectId, biome.biomeEffectStartLevel);
        BiomeRegistry.register(replaced);
    }

    /**
     * Append a new passive mob to a biome's pool. Skips the add if an entry
     * with the same entity id already exists or if the entity isn't registered.
     */
    public static boolean appendPassiveMob(String biomeId, MobPoolEntry newEntry) {
        if (newEntry == null) return false;
        if (!entityExists(newEntry.entityTypeId())) return false;
        BiomeTemplate biome = findBiome(biomeId);
        if (biome == null) return false;

        MobPoolEntry[] pool = biome.passiveMobs != null ? biome.passiveMobs : new MobPoolEntry[0];
        for (MobPoolEntry existing : pool) {
            if (existing != null && newEntry.entityTypeId().equals(existing.entityTypeId())) {
                return false; // already present - don't double-add
            }
        }

        MobPoolEntry[] appended = new MobPoolEntry[pool.length + 1];
        System.arraycopy(pool, 0, appended, 0, pool.length);
        appended[pool.length] = newEntry;
        replaceBiomePassive(biome, appended);
        CrafticsMod.LOGGER.info("[Compat] {}: added passive {}", biomeId, newEntry.entityTypeId());
        return true;
    }

    /** Convenience wrapper for the common "add a passive mob with these stats" case. */
    public static boolean appendPassiveMob(String biomeId, String entityTypeId, int weight,
                                             int baseHp, int baseAttack, int baseDefense, int range) {
        return appendPassiveMob(biomeId,
            new MobPoolEntry(entityTypeId, weight, baseHp, baseAttack, baseDefense, range, true));
    }

    /**
     * Remove all passive entries matching the given entity id from a biome's pool.
     *
     * @return true if at least one entry was removed
     */
    public static boolean removePassiveMob(String biomeId, String entityTypeId) {
        BiomeTemplate biome = findBiome(biomeId);
        if (biome == null) return false;
        MobPoolEntry[] pool = biome.passiveMobs;
        if (pool == null || pool.length == 0) return false;

        List<MobPoolEntry> kept = new ArrayList<>(pool.length);
        boolean removed = false;
        for (MobPoolEntry entry : pool) {
            if (entry != null && entityTypeId.equals(entry.entityTypeId())) {
                removed = true;
            } else {
                kept.add(entry);
            }
        }
        if (!removed) return false;

        replaceBiomePassive(biome, kept.toArray(new MobPoolEntry[0]));
        CrafticsMod.LOGGER.info("[Compat] {}: removed passive {}", biomeId, entityTypeId);
        return true;
    }

    /** Rebuild {@code biome} with a new completion-loot pool and re-register it. */
    private static void replaceBiomeLoot(BiomeTemplate biome, Item[] items, int[] weights) {
        BiomeTemplate replaced = new BiomeTemplate(
            biome.biomeId, biome.displayName, biome.startLevel, biome.levelCount,
            biome.baseWidth, biome.baseHeight, biome.widthGrowth, biome.heightGrowth,
            biome.floorBlocks, biome.obstacleBlocks,
            biome.baseObstacleDensity, biome.obstacleDensityGrowth,
            biome.passiveMobs, biome.hostileMobs, biome.boss,
            items, weights,
            biome.enchantmentLootIds, biome.enchantmentLootWeights,
            biome.nightLevel, biome.environmentId,
            biome.biomeEffectId, biome.biomeEffectStartLevel);
        BiomeRegistry.register(replaced);
    }

    /**
     * Append an item to a biome's level-completion loot pool, by item id so callers
     * never need a hard reference to an optional mod's class. The id is resolved
     * against the live registry and the call is skipped when it isn't there, which
     * is what lets a compat module list modded loot unconditionally.
     *
     * <p>This is the loot counterpart to {@link #appendHostileMob}: biome JSON can
     * only name items that always exist (an unknown id there is dropped with a load
     * warning for every user, mod or no mod), so mod-gated loot has to be added at
     * runtime instead.
     *
     * <p>Re-running is safe: an item already in the pool is left alone rather than
     * added twice, so a datapack reload can't inflate its weight.
     *
     * @return true if the item was appended
     */
    public static boolean appendLoot(String biomeId, String itemId, int weight) {
        if (itemId == null || weight <= 0) return false;
        Item item;
        try {
            Identifier id = Identifier.of(itemId);
            if (!Registries.ITEM.containsId(id)) return false;
            item = Registries.ITEM.get(id);
        } catch (Throwable t) {
            return false;
        }
        if (item == null) return false;

        BiomeTemplate biome = findBiome(biomeId);
        if (biome == null) return false;

        Item[] items = biome.lootItems != null ? biome.lootItems : new Item[0];
        int[] weights = biome.lootWeights != null ? biome.lootWeights : new int[0];
        for (Item existing : items) {
            if (existing == item) return false;
        }

        Item[] newItems = new Item[items.length + 1];
        int[] newWeights = new int[items.length + 1];
        System.arraycopy(items, 0, newItems, 0, items.length);
        // Weights may be shorter than items if the JSON omitted some; copy what's
        // there and leave the rest at 0 rather than reading off the end.
        System.arraycopy(weights, 0, newWeights, 0, Math.min(weights.length, items.length));
        newItems[items.length] = item;
        newWeights[items.length] = weight;

        replaceBiomeLoot(biome, newItems, newWeights);
        CrafticsMod.LOGGER.info("[Compat] {}: +loot {} (weight {})", biomeId, itemId, weight);
        return true;
    }

    private static BiomeTemplate findBiome(String biomeId) {
        if (biomeId == null) return null;
        List<BiomeTemplate> all = new ArrayList<>(BiomeRegistry.getAllBiomes());
        for (BiomeTemplate b : all) {
            if (biomeId.equals(b.biomeId)) return b;
        }
        return null;
    }
}
