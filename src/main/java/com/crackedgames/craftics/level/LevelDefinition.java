package com.crackedgames.craftics.level;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import net.minecraft.block.Block;

public abstract class LevelDefinition {
    public abstract int getLevelNumber();
    public abstract String getName();
    public abstract int getWidth();
    public abstract int getHeight();
    public abstract GridPos getPlayerStart();
    public abstract Block getFloorBlock();

    /** Override to add obstacles, hazards, etc */
    public GridTile[][] buildTiles() {
        int w = getWidth();
        int h = getHeight();
        GridTile[][] tiles = new GridTile[w][h];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                tiles[x][z] = new GridTile(
                    com.crackedgames.craftics.core.TileType.NORMAL,
                    getFloorBlock()
                );
            }
        }
        return tiles;
    }

    public abstract EnemySpawn[] getEnemySpawns();

    public java.util.List<net.minecraft.item.ItemStack> rollCompletionLoot() {
        return java.util.List.of();
    }

    public boolean isNightLevel() { return false; }

    /** Override to force a biome for schematic selection. Null = auto-detect */
    public String getArenaBiomeId() { return null; }

    public record EnemySpawn(String entityTypeId, GridPos position,
                              int hp, int attack, int defense, int range) {}
}
