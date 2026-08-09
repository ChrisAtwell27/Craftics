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

    /** World-aware overload - defaults to the no-arg version so existing
     *  overrides keep working. Levels that need the registry (e.g. the ominous
     *  trial's heavy-enchanted loot) override this to roll with world context. */
    public java.util.List<net.minecraft.item.ItemStack> rollCompletionLoot(
            net.minecraft.server.world.ServerWorld world) {
        return rollCompletionLoot();
    }

    public boolean isNightLevel() { return false; }

    /**
     * Campaign biome ordinal that should drive spawn-time progression for synthetic levels.
     * Generated campaign levels derive this from their biome template; event levels override it.
     */
    public int getProgressionBiomeOrdinal() { return -1; }

    /**
     * Whether one synthetic spawn deliberately exceeds the ordinary non-boss damage ceiling.
     * Events use this for authored elite encounters without changing their boss/loot behavior.
     */
    public boolean bypassesEnemyDamageCap(int spawnIndex) { return false; }

    /** Override to force a biome for schematic selection. Null = auto-detect */
    public String getArenaBiomeId() { return null; }

    /**
     * Which variant of the arena schem set to use, or -1 to keep the default
     * (the biome level index, which is 0 for anything that is not a
     * GeneratedLevelDefinition and so always lands on the first variant).
     * Event levels that reuse one shared schem folder set this to spread across
     * the available files.
     */
    public int getArenaVariantIndex() { return -1; }

    /**
     * EnvironmentRegistry id for fog, ambience and theming, or null to derive it
     * from the biome template (and fall back to plains when there is none).
     * Synthetic levels have no biome template, so this is their only way to be
     * themed.
     */
    public String getArenaEnvironmentId() { return null; }

    /**
     * Index into {@link #getEnemySpawns()} that should get the full boss
     * treatment (boss AI lookup, nameplate, boss setup), or -1 for none. Only
     * consulted when the level has no biome template to derive a boss from.
     */
    public int getBossSpawnIndex() { return -1; }

    /**
     * Pseudo-biome id used to build the boss AI registry key
     * ({@code "boss:" + id}) when {@link #getBossSpawnIndex()} is set. Null when
     * there is no boss.
     */
    public String getBossAiBiomeId() { return null; }

    /**
     * Level numbers at or above this are synthetic: event levels (trial chambers,
     * ambushes, raids, addon fights) that exist outside the real biome-registry
     * range. A synthetic number is a stable id for logging and metadata only -
     * it must never be fed to the numbered-arena origin formulas, which multiply
     * the level number into a world coordinate.
     */
    public static final int SYNTHETIC_LEVEL_BASE = 9000;

    /**
     * True when this definition is meant to be placed at {@link #getOverrideOrigin}.
     * Lets callers tell "no override defined" (numbered-arena origin is correct)
     * apart from "override defined but currently unavailable (returned null)",
     * which must abort the build rather than fall through to the numbered
     * formula with a synthetic level number.
     */
    public boolean hasOverrideOrigin() { return false; }

    /**
     * Override to place this arena at a specific world origin instead of the
     * level-number-derived origin. Used by addon event levels (e.g. the
     * Artifacts abandoned-campsite mimic fight) that need to spawn an arena
     * without corresponding to any real biome-registry level - passing a
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
     * <p>{@code aiKey} is the {@code AIRegistry} lookup key - normally equal to
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
