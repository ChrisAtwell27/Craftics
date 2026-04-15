package com.crackedgames.craftics.compat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.level.BiomeRegistry;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.MobPoolEntry;
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
            biome.nightLevel, biome.environmentStyle);
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
                return false; // already present — don't double-add
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
            biome.nightLevel, biome.environmentStyle);
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
                return false; // already present — don't double-add
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

    private static BiomeTemplate findBiome(String biomeId) {
        if (biomeId == null) return null;
        List<BiomeTemplate> all = new ArrayList<>(BiomeRegistry.getAllBiomes());
        for (BiomeTemplate b : all) {
            if (biomeId.equals(b.biomeId)) return b;
        }
        return null;
    }
}
