package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/**
 * Decides which arena tiles catch fire. A tile is flammable when it is tall
 * grass / fern, a cactus, or an obstacle whose block is a flammable material
 * (logs, planks, leaves, wool, etc.). Fire attacks ignite these into temporary
 * {@link TileType#FIRE} tiles, which then spread to adjacent flammable tiles
 * each turn (see CombatManager fire-spread tick).
 *
 * <p>Flammability is decided from the tile's block via a small material check
 * plus MC's own fire-spread chance where available, so any reasonable placed
 * wood/plant block burns without an exhaustive hardcoded list.
 */
public final class FlammableTiles {

    private FlammableTiles() {}

    /** How long a freshly-ignited tile burns before turning to ash (NORMAL). */
    public static final int FIRE_BURN_TURNS = 3;

    /** True if this tile can catch fire (and isn't already fire/lava/water). */
    public static boolean isFlammable(GridTile tile) {
        if (tile == null) return false;
        TileType t = tile.getType();
        // Already burning or non-flammable terrain types.
        if (t == TileType.FIRE || t == TileType.LAVA || t == TileType.WATER
            || t == TileType.DEEP_WATER || t == TileType.VOID) {
            return false;
        }
        // Stealth plants always burn.
        if (t == TileType.TALL_GRASS || t == TileType.TALL_FERN) return true;
        // Obstacles (and any tile) made of a flammable block burn.
        return isFlammableBlock(tile.getBlockType());
    }

    /** True if a raw block is a flammable material. */
    public static boolean isFlammableBlock(Block block) {
        if (block == null) return false;
        if (block == Blocks.CACTUS) return true; // cactus burns in our combat fiction
        // MC tags / instanceof cover the bulk: logs, planks, leaves, wool,
        // plants, wood-family blocks all register fire spread.
        net.minecraft.block.BlockState state = block.getDefaultState();
        if (state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return true;
        if (state.isIn(net.minecraft.registry.tag.BlockTags.PLANKS)) return true;
        if (state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) return true;
        if (state.isIn(net.minecraft.registry.tag.BlockTags.WOOL)) return true;
        if (state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_FENCES)) return true;
        if (state.isIn(net.minecraft.registry.tag.BlockTags.SAPLINGS)) return true;
        if (state.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)) return true;
        if (state.isIn(net.minecraft.registry.tag.BlockTags.WOOL_CARPETS)) return true;
        // A few common flammable blocks not covered by the tags above.
        return block == Blocks.HAY_BLOCK || block == Blocks.BOOKSHELF
            || block == Blocks.SCAFFOLDING || block == Blocks.BAMBOO
            || block == Blocks.DRIED_KELP_BLOCK || block == Blocks.TARGET
            || block == Blocks.COBWEB;
    }
}
