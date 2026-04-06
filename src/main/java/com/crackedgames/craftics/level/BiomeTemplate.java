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
    public final EnvironmentStyle environmentStyle;

    public BiomeTemplate(String biomeId, String displayName, int startLevel, int levelCount,
                          int baseWidth, int baseHeight, int widthGrowth, int heightGrowth,
                          Block[] floorBlocks, Block[] obstacleBlocks,
                          float baseObstacleDensity, float obstacleDensityGrowth,
                          MobPoolEntry[] passiveMobs, MobPoolEntry[] hostileMobs,
                          MobPoolEntry boss,
                          Item[] lootItems, int[] lootWeights,
                          String[] enchantmentLootIds, int[] enchantmentLootWeights,
                          boolean nightLevel, EnvironmentStyle environmentStyle) {
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
        this.environmentStyle = environmentStyle;
    }

    public int getEndLevel() {
        return startLevel + levelCount - 1;
    }

    public boolean containsLevel(int level) {
        return level >= startLevel && level <= getEndLevel();
    }

    public int getBiomeLevelIndex(int globalLevel) {
        return globalLevel - startLevel;
    }

    public boolean isBossLevel(int globalLevel) {
        return getBiomeLevelIndex(globalLevel) == levelCount - 1;
    }

    public LootPool buildLootPool() {
        LootPool pool = new LootPool();
        for (int i = 0; i < lootItems.length; i++) {
            pool.add(lootItems[i], lootWeights[i]);
        }
        return pool;
    }
}
