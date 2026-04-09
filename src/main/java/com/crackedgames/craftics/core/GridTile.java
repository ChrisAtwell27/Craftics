package com.crackedgames.craftics.core;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class GridTile {
    private TileType type;
    private TileType restoreType;
    private Block blockType;
    private Block restoreBlockType; // original block before temporary terrain override
    private int turnsRemaining; // for temporary tiles like fire
    private boolean permanent; // permanent obstacles cannot be broken by pickaxes

    public GridTile(TileType type, Block blockType) {
        this.type = type;
        this.restoreType = type;
        this.blockType = blockType;
        this.turnsRemaining = -1; // permanent by default
        this.permanent = false;
    }

    public GridTile(TileType type, Block blockType, boolean permanent) {
        this.type = type;
        this.restoreType = type;
        this.blockType = blockType;
        this.turnsRemaining = -1;
        this.permanent = permanent;
    }

    public GridTile(TileType type) {
        this(type, defaultBlockFor(type));
    }

    public TileType getType() {
        return type;
    }

    public void setType(TileType type) {
        this.type = type;
        this.blockType = defaultBlockFor(type);
        if (turnsRemaining <= 0) {
            this.restoreType = type;
        }
    }

    /** Apply a temporary terrain override and restore the previous type when it expires. */
    public void setTemporaryType(TileType tempType, int turns) {
        if (turns <= 0) {
            setType(tempType);
            return;
        }
        if (turnsRemaining <= 0) {
            restoreType = type;
            restoreBlockType = blockType;
        }
        type = tempType;
        blockType = defaultBlockFor(tempType);
        turnsRemaining = turns;
    }

    public Block getBlockType() {
        return blockType;
    }

    public void setBlockType(Block block) {
        this.blockType = block;
    }

    public boolean isWalkable() {
        return type.walkable;
    }

    /** Check walkability considering boat access for water tiles. */
    public boolean isWalkable(boolean hasBoat) {
        return isWalkableEx(hasBoat, false);
    }

    /** Check walkability with optional obstacle ignoring (Pathfinder set bonus). */
    public boolean isWalkableEx(boolean hasBoat, boolean ignoreObstacles) {
        return isWalkableEx(hasBoat, ignoreObstacles, false);
    }

    /** Check walkability with aquatic flag — aquatic entities can traverse DEEP_WATER. */
    public boolean isWalkableEx(boolean hasBoat, boolean ignoreObstacles, boolean aquatic) {
        if (ignoreObstacles && type == TileType.OBSTACLE) return true;
        if (aquatic && (type == TileType.WATER || type == TileType.DEEP_WATER)) return true;
        if (type.requiresBoat) return hasBoat;
        return type.walkable;
    }

    /** Walkable and not hazardous — safe for spawning entities. */
    public boolean isSafeForSpawn() {
        return type.walkable && type.damageOnStep <= 0;
    }

    /**
     * Movement cost for pathfinding. Normal tiles cost 1.
     * Hazardous tiles (LAVA, FIRE) cost much more so AI avoids them.
     */
    public int getMoveCost() {
        if (type.damageOnStep > 0) return 50;
        return 1;
    }

    public boolean isWater() {
        return type == TileType.WATER || type == TileType.DEEP_WATER;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }

    public int getDamageOnStep() {
        return type.damageOnStep;
    }

    public int getTurnsRemaining() {
        return turnsRemaining;
    }

    public void setTurnsRemaining(int turns) {
        this.turnsRemaining = turns;
    }

    public void tickTurn() {
        if (turnsRemaining > 0) {
            turnsRemaining--;
            if (turnsRemaining == 0) {
                TileType fallback = restoreType != null ? restoreType : TileType.NORMAL;
                type = fallback;
                // Restore the original biome-specific block, not the generic default
                blockType = restoreBlockType != null ? restoreBlockType : defaultBlockFor(fallback);
                restoreType = fallback;
                restoreBlockType = null;
                turnsRemaining = -1;
            }
        }
    }

    private static Block defaultBlockFor(TileType type) {
        return switch (type) {
            case NORMAL -> Blocks.GRASS_BLOCK;
            case OBSTACLE -> Blocks.STONE;
            case LAVA -> Blocks.LAVA;
            case FIRE -> Blocks.MAGMA_BLOCK;
            case VOID -> Blocks.AIR;
            case EXIT -> Blocks.LADDER;
            case WATER, DEEP_WATER -> Blocks.WATER;
            case LOW_GROUND -> Blocks.STONE;
            case POWDER_SNOW -> Blocks.POWDER_SNOW;
        };
    }
}
