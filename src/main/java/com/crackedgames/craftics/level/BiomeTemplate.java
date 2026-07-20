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
}
