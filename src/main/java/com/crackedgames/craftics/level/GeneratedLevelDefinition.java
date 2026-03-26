package com.crackedgames.craftics.level;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * A procedurally generated level definition created by LevelGenerator.
 * Holds precomputed values instead of hardcoding them.
 */
public class GeneratedLevelDefinition extends LevelDefinition {
    private final int levelNumber;
    private final String name;
    private final int width, height;
    private final GridPos playerStart;
    private final Block floorBlock;
    private final GridTile[][] tiles;
    private final EnemySpawn[] enemySpawns;
    private final List<ItemStack> loot;
    private final boolean nightLevel;
    private final BiomeTemplate biomeTemplate;

    public GeneratedLevelDefinition(int levelNumber, String name, int width, int height,
                                     GridPos playerStart, Block floorBlock, GridTile[][] tiles,
                                     EnemySpawn[] enemySpawns, List<ItemStack> loot,
                                     boolean nightLevel, BiomeTemplate biomeTemplate) {
        this.levelNumber = levelNumber;
        this.name = name;
        this.width = width;
        this.height = height;
        this.playerStart = playerStart;
        this.floorBlock = floorBlock;
        this.tiles = tiles;
        this.enemySpawns = enemySpawns;
        this.loot = loot;
        this.nightLevel = nightLevel;
        this.biomeTemplate = biomeTemplate;
    }

    @Override public int getLevelNumber() { return levelNumber; }
    @Override public String getName() { return name; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public GridPos getPlayerStart() { return playerStart; }
    @Override public Block getFloorBlock() { return floorBlock; }
    @Override public GridTile[][] buildTiles() { return tiles; }
    @Override public EnemySpawn[] getEnemySpawns() { return enemySpawns; }
    @Override public boolean isNightLevel() { return nightLevel; }

    @Override
    public List<ItemStack> rollCompletionLoot() { return loot; }

    public BiomeTemplate getBiomeTemplate() { return biomeTemplate; }
}
