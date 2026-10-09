package com.crackedgames.craftics.level;

import com.crackedgames.craftics.combat.LootPool;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

public class BiomeTemplate {
    public final String biomeId;
    public final String displayName;
    public final int startLevel;
    public final int levelCount;
    public final int baseWidth, baseHeight;
    public final int widthGrowth, heightGrowth;
    public final Block[] floorBlocks;
    public final Block[] obstacleBlocks;
    public final float baseObstacleDensity;
    public final float obstacleDensityGrowth;
    public final MobPoolEntry[] passiveMobs;
    public final MobPoolEntry[] hostileMobs;
    public final MobPoolEntry boss;
    public final Item[] lootItems;
    public final int[] lootWeights;
    // Empty = all enchantments allowed in book drops
    public final String[] enchantmentLootIds;
    public final int[] enchantmentLootWeights;
    public final boolean nightLevel;
    public final String environmentId;
    /** Optional biome weather effect (see combat/biomeeffect): the BiomeEffectRegistry id and
     *  the 1-based level within the biome it starts at, from the biome JSON's biomeEffect block. */
    public final String biomeEffectId;
    /** Optional biome weather effect (see combat/biomeeffect): the BiomeEffectRegistry id and
     *  the 1-based level within the biome it starts at, from the biome JSON's biomeEffect block. */
    public final int biomeEffectStartLevel;

    public BiomeTemplate(String biomeId, String displayName, int startLevel, int levelCount,
                          int baseWidth, int baseHeight, int widthGrowth, int heightGrowth,
                          Block[] floorBlocks, Block[] obstacleBlocks,
                          float baseObstacleDensity, float obstacleDensityGrowth,
                          MobPoolEntry[] passiveMobs, MobPoolEntry[] hostileMobs,
                          MobPoolEntry boss,
                          Item[] lootItems, int[] lootWeights,
                          String[] enchantmentLootIds, int[] enchantmentLootWeights,
                          boolean nightLevel, String environmentId) {
        this(biomeId, displayName, startLevel, levelCount,
            baseWidth, baseHeight, widthGrowth, heightGrowth,
            floorBlocks, obstacleBlocks,
            baseObstacleDensity, obstacleDensityGrowth,
            passiveMobs, hostileMobs,
            boss,
            lootItems, lootWeights,
            enchantmentLootIds, enchantmentLootWeights,
            nightLevel, environmentId,
            null, 0);
    }

    public BiomeTemplate(String biomeId, String displayName, int startLevel, int levelCount,
                          int baseWidth, int baseHeight, int widthGrowth, int heightGrowth,
                          Block[] floorBlocks, Block[] obstacleBlocks,
                          float baseObstacleDensity, float obstacleDensityGrowth,
                          MobPoolEntry[] passiveMobs, MobPoolEntry[] hostileMobs,
                          MobPoolEntry boss,
                          Item[] lootItems, int[] lootWeights,
                          String[] enchantmentLootIds, int[] enchantmentLootWeights,
                          boolean nightLevel, String environmentId,
                          String biomeEffectId, int biomeEffectStartLevel) {
        this.biomeId = biomeId;
        this.displayName = displayName;
        this.startLevel = startLevel;
        this.levelCount = levelCount;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.widthGrowth = widthGrowth;
        this.heightGrowth = heightGrowth;
        this.floorBlocks = floorBlocks;
        this.obstacleBlocks = obstacleBlocks;
        this.baseObstacleDensity = baseObstacleDensity;
        this.obstacleDensityGrowth = obstacleDensityGrowth;
        this.passiveMobs = passiveMobs;
        this.hostileMobs = hostileMobs;
        this.boss = boss;
        this.lootItems = lootItems;
        this.lootWeights = lootWeights;
        this.enchantmentLootIds = enchantmentLootIds;
        this.enchantmentLootWeights = enchantmentLootWeights;
        this.nightLevel = nightLevel;
        this.environmentId = environmentId;
        this.biomeEffectId = biomeEffectId;
        this.biomeEffectStartLevel = biomeEffectStartLevel;
    }

    public int getEndLevel() {
        return startLevel + levelCount - 1;
    }

    public boolean containsLevel(int level) {
        return level >= startLevel && level <= getEndLevel();
    }

    /**
     * The level's index within this biome, clamped to the biome's own range.
     *
     * <p>Clamped because an overshooting counter used to keep indexing off this biome's
     * startLevel straight into the NEXT biome's global range - the "Deep Dark 3 -> 6 landed me
     * in Nether Wastes I" bug. See {@link BiomeLevelMath}.
     */
    public int getBiomeLevelIndex(int globalLevel) {
        return BiomeLevelMath.biomeLevelIndex(globalLevel, startLevel, levelCount);
    }

    /**
     * Whether this is the biome's boss level - true at the boss AND anywhere past it, so a run
     * that somehow overshot still has to clear the boss before it can leave the biome.
     */
    public boolean isBossLevel(int globalLevel) {
        return BiomeLevelMath.isBossLevel(globalLevel, startLevel, levelCount);
    }

    public LootPool buildLootPool() {
        LootPool pool = new LootPool();
        for (int i = 0; i < lootItems.length; i++) {
            pool.add(lootItems[i], lootWeights[i]);
        }
        return pool;
    }

    /** Whether a biome effect starting at 1-based {@code startLevel} is active on 0-based
     *  {@code biomeIndex}. startLevel <= 0 means "no effect". Pure int math (unit-tested). */
    public static boolean effectActiveAt(int startLevel, int biomeIndex) {
        return startLevel > 0 && biomeIndex >= startLevel - 1;
    }

    // -- Prelude: opening levels that are somewhere else --

    private BiomeTemplate prelude;
    private int preludeLevels;

    /**
     * Give this biome a prelude: its first {@code levels} levels are generated from
     * {@code look} instead of from this template. Another floor, other obstacles, other
     * creatures and loot, and its own name, before the biome proper begins. For a dungeon
     * that is reached by crossing the country it stands in.
     *
     * <p>{@code look} has to carry this biome's own id, level range and boss, since a level
     * built from it is still a level of this biome to everything that asks: progress, the
     * boss key, where the run goes next. The JSON loader builds it that way. The boss level
     * is never part of a prelude, however many levels are asked for.
     */
    public BiomeTemplate withPrelude(BiomeTemplate look, int levels) {
        int most = Math.max(0, levelCount - 1);
        this.preludeLevels = look == null ? 0 : Math.max(0, Math.min(levels, most));
        this.prelude = this.preludeLevels > 0 ? look : null;
        return this;
    }

    /** The template this biome's opening levels are built from, or null when it has no prelude. */
    public BiomeTemplate getPrelude() {
        return prelude;
    }

    /** How many of this biome's levels its prelude covers. 0 with no prelude. */
    public int getPreludeLevels() {
        return preludeLevels;
    }

    /**
     * The template the level at this 0-based index within the biome is built from: the
     * prelude for the opening levels, this one from there on.
     */
    public BiomeTemplate themeAt(int biomeLevelIndex) {
        return prelude != null && biomeLevelIndex >= 0 && biomeLevelIndex < preludeLevels ? prelude : this;
    }

    /** This biome moved to begin at another level, its prelude with it. */
    public BiomeTemplate withStartLevel(int newStartLevel) {
        BiomeTemplate moved = new BiomeTemplate(
            biomeId, displayName, newStartLevel, levelCount,
            baseWidth, baseHeight, widthGrowth, heightGrowth,
            floorBlocks, obstacleBlocks,
            baseObstacleDensity, obstacleDensityGrowth,
            passiveMobs, hostileMobs, boss,
            lootItems, lootWeights,
            enchantmentLootIds, enchantmentLootWeights,
            nightLevel, environmentId,
            biomeEffectId, biomeEffectStartLevel);
        if (prelude != null) moved.withPrelude(prelude.withStartLevel(newStartLevel), preludeLevels);
        moved.withBossRoom(bossGridWidth, bossGridHeight).withBossLoot(bossLootItems, bossLootWeights);
        return moved;
    }

    // -- The boss's own room, and what it was guarding --

    private int bossGridWidth;
    private int bossGridHeight;
    private Item[] bossLootItems;
    private int[] bossLootWeights;

    /**
     * Give the boss level a grid of its own instead of the one the biome's growth arrives
     * at. Measured the way {@code baseWidth} is, before the arena builder's edge. Zero or
     * less on either side means no override.
     */
    public BiomeTemplate withBossRoom(int width, int height) {
        boolean set = width > 0 && height > 0;
        this.bossGridWidth = set ? width : 0;
        this.bossGridHeight = set ? height : 0;
        return this;
    }

    public boolean hasBossRoom() {
        return bossGridWidth > 0;
    }

    public int getBossGridWidth() {
        return bossGridWidth;
    }

    public int getBossGridHeight() {
        return bossGridHeight;
    }

    /**
     * A treasure table for the boss level: one item from it is added to what clearing that
     * level pays, on top of the biome's ordinary loot. For the things a dungeon is entered
     * for, which its corridors should not be handing out.
     */
    public BiomeTemplate withBossLoot(Item[] items, int[] weights) {
        boolean any = items != null && weights != null && items.length > 0;
        this.bossLootItems = any ? items : null;
        this.bossLootWeights = any ? weights : null;
        return this;
    }

    public boolean hasBossLoot() {
        return bossLootItems != null;
    }

    public LootPool buildBossLootPool() {
        LootPool pool = new LootPool();
        if (bossLootItems == null) return pool;
        for (int i = 0; i < bossLootItems.length; i++) {
            pool.add(bossLootItems[i], i < bossLootWeights.length ? bossLootWeights[i] : 1);
        }
        return pool;
    }

    /**
     * This biome with its prelude's creatures and loot folded into its own, for anything
     * that lists what a biome holds rather than building a level of it. The biome itself
     * when it has no prelude.
     */
    public BiomeTemplate withPreludeFolded() {
        if (this.prelude == null && bossLootItems == null) return this;
        // With no prelude there is only the treasure to fold in, and the biome stands in for both parts.
        BiomeTemplate prelude = this.prelude != null ? this.prelude : this;
        java.util.List<Item> items = new java.util.ArrayList<>();
        java.util.List<Integer> weights = new java.util.ArrayList<>();
        for (BiomeTemplate part : new BiomeTemplate[]{this, prelude}) {
            if (part.lootItems == null) continue;
            for (int i = 0; i < part.lootItems.length; i++) {
                if (items.contains(part.lootItems[i])) continue;
                items.add(part.lootItems[i]);
                weights.add(part.lootWeights != null && i < part.lootWeights.length ? part.lootWeights[i] : 1);
            }
        }
        if (bossLootItems != null) {
            for (int i = 0; i < bossLootItems.length; i++) {
                if (items.contains(bossLootItems[i])) continue;
                items.add(bossLootItems[i]);
                weights.add(i < bossLootWeights.length ? bossLootWeights[i] : 1);
            }
        }
        int[] foldedWeights = new int[weights.size()];
        for (int i = 0; i < foldedWeights.length; i++) foldedWeights[i] = weights.get(i);
        return new BiomeTemplate(
            biomeId, displayName, startLevel, levelCount,
            baseWidth, baseHeight, widthGrowth, heightGrowth,
            floorBlocks, obstacleBlocks,
            baseObstacleDensity, obstacleDensityGrowth,
            foldPools(passiveMobs, prelude.passiveMobs), foldPools(hostileMobs, prelude.hostileMobs), boss,
            items.isEmpty() && lootItems == null ? null : items.toArray(new Item[0]), foldedWeights,
            enchantmentLootIds, enchantmentLootWeights,
            nightLevel, environmentId,
            biomeEffectId, biomeEffectStartLevel);
    }

    private static MobPoolEntry[] foldPools(MobPoolEntry[] own, MobPoolEntry[] other) {
        java.util.List<MobPoolEntry> all = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MobPoolEntry[] pool : new MobPoolEntry[][]{own, other}) {
            if (pool == null) continue;
            for (MobPoolEntry entry : pool) {
                if (entry != null && seen.add(entry.entityTypeId())) all.add(entry);
            }
        }
        return all.toArray(new MobPoolEntry[0]);
    }
}
