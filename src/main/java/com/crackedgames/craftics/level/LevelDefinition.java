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

    /**
     * Override to place this arena at a specific world origin instead of the
     * level-number-derived origin. Used by addon event levels (e.g. the
     * Artifacts abandoned-campsite mimic fight) that need to spawn an arena
     * without corresponding to any real biome-registry level — passing a
     * synthetic level number would send the builder to a far-away unloaded
     * chunk because {@code CrafticsSavedData.getArenaOrigin} multiplies the
     * level number by 300 on the X axis.
     * <p>
     * Return null (default) to use the standard per-level origin.
     */
    public net.minecraft.util.math.BlockPos getOverrideOrigin(java.util.UUID worldOwner,
                                                                com.crackedgames.craftics.world.CrafticsSavedData data) {
        return null;
    }

    public record EnemySpawn(String entityTypeId, GridPos position,
                              int hp, int attack, int defense, int range) {}
}
