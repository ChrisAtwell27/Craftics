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

    /** World-aware overload — defaults to the no-arg version so existing
     *  overrides keep working. Levels that need the registry (e.g. the ominous
     *  trial's heavy-enchanted loot) override this to roll with world context. */
    public java.util.List<net.minecraft.item.ItemStack> rollCompletionLoot(
            net.minecraft.server.world.ServerWorld world) {
        return rollCompletionLoot();
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

    /**
     * One enemy to place when building a level.
     *
     * <p>{@code aiKey} is the {@code AIRegistry} lookup key — normally equal to
     * {@code entityTypeId}, differing only when an {@code EnemyEntry} pairs an
     * appearance with a non-matching AI strategy.
     *
     * <p>{@code speed} is the combat move speed in tiles per turn; {@code 0} uses
     * the entity type's default speed.
     */
    public record EnemySpawn(String entityTypeId, GridPos position,
                              int hp, int attack, int defense, int range,
                              String aiKey, int speed) {
        /** Spawn whose AI matches its entity type, at the entity type's default speed. */
        public EnemySpawn(String entityTypeId, GridPos position,
                          int hp, int attack, int defense, int range) {
            this(entityTypeId, position, hp, attack, defense, range, entityTypeId, 0);
        }

        /** Spawn with an explicit AI key, at the entity type's default speed. */
        public EnemySpawn(String entityTypeId, GridPos position,
                          int hp, int attack, int defense, int range, String aiKey) {
            this(entityTypeId, position, hp, attack, defense, range, aiKey, 0);
        }
    }
}
