package com.crackedgames.craftics.core;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class GridTile {
    private TileType type;
    private Block blockType;
    private int turnsRemaining; // for temporary tiles like fire

    public GridTile(TileType type, Block blockType) {
        this.type = type;
        this.blockType = blockType;
        this.turnsRemaining = -1; // permanent by default
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
    }

    public Block getBlockType() {
        return blockType;
    }

    public boolean isWalkable() {
        return type.walkable;
    }

    /** Check walkability considering boat access for water tiles. */
    public boolean isWalkable(boolean hasBoat) {
        if (type.requiresBoat) return hasBoat;
        return type.walkable;
    }

    public boolean isWater() {
        return type == TileType.WATER;
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
                setType(TileType.NORMAL);
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
            case WATER -> Blocks.WATER;
        };
    }
}
