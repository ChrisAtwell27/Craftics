package com.crackedgames.craftics.level;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public enum EnvironmentStyle {
    PLAINS,
    FOREST,
    SNOWY,
    MOUNTAIN,
    RIVER,
    DESERT,
    JUNGLE,
    CAVE,
    DEEP_DARK,
    NETHER,
    CRIMSON_FOREST,
    WARPED_FOREST,
    END;

    /** Return the biome-appropriate floor block for NORMAL tiles. */
    public Block getFloorBlock() {
        return switch (this) {
            case FOREST -> Blocks.PODZOL;
            case SNOWY -> Blocks.SNOW_BLOCK;
            case MOUNTAIN -> Blocks.STONE;
            case RIVER -> Blocks.GRASS_BLOCK;
            case DESERT -> Blocks.SAND;
            case JUNGLE -> Blocks.MOSS_BLOCK;
            case CAVE -> Blocks.STONE;
            case DEEP_DARK -> Blocks.SCULK;
            case NETHER -> Blocks.NETHERRACK;
            case CRIMSON_FOREST -> Blocks.CRIMSON_NYLIUM;
            case WARPED_FOREST -> Blocks.WARPED_NYLIUM;
            case END -> Blocks.END_STONE;
            default -> Blocks.GRASS_BLOCK; // PLAINS and fallback
        };
    }
}
