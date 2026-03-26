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

    /**
     * Build the tile grid for this level.
     * Default implementation fills with normal tiles using the floor block.
     * Override to add obstacles, hazards, etc.
     */
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

    /**
     * Enemy spawn definitions for this level.
     * Returns array of (EntityType ID string, GridPos) pairs.
     * Actual spawning happens in Phase 5.
     */
    public abstract EnemySpawn[] getEnemySpawns();

    /**
     * Environment loot pool — randomly rolled on level completion.
     * Override per level to give theme-appropriate materials.
     * @param rolls Number of items to roll from the pool
     */
    public java.util.List<net.minecraft.item.ItemStack> rollCompletionLoot() {
        return java.util.List.of();
    }

    /**
     * Whether this level should be nighttime (caves, underground).
     * Surface levels return false (perma day), underground returns true (perma night).
     */
    public boolean isNightLevel() { return false; }

    public record EnemySpawn(String entityTypeId, GridPos position,
                              int hp, int attack, int defense, int range) {}
}
