package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/**
 * Decides which arena tiles catch fire. A tile is flammable when it is tall
 * grass / fern, a cactus, bare grass ground, netherrack, or an obstacle whose
 * block is a flammable material (logs, planks, leaves, wool, etc.).
 *
 * <p>Fire attacks ignite these into {@link TileType#FIRE} tiles - a real flame
 * block sitting at Y+1 above an intact floor, like a placed block. A lit tile
 * runs a three-stage burn driven by CombatManager's fire tick:
 * flames ({@link #FIRE_BURN_TURNS}) -&gt; magma ({@link #MAGMA_TURNS}) -&gt; burned
 * out. Flames spread to adjacent flammable tiles on the turn they collapse into
 * magma, so a forest arena burns outward one ring per turn.
 *
 * <p>Netherrack is the exception to "burned out = dirt": it comes back as
 * netherrack and is merely fireproof for {@link #NETHERRACK_COOLDOWN_TURNS}
 * turns afterwards, so the nether floor is never permanently scarred.
 *
 * <p>Flammability is decided from the tile's block via a small material check
 * plus MC's own fire-spread tags where available, so any reasonable placed
 * wood/plant block burns without an exhaustive hardcoded list.
 */
public final class FlammableTiles {

    private FlammableTiles() {}

    /** Turns a freshly-lit tile shows open flames before collapsing to magma. */
    public static final int FIRE_BURN_TURNS = 1;

    /**
     * Soul fire holds its flames one turn longer than ordinary fire. Ordinary fire spreads
     * and dies in the same breath - the ring it lit is already magma behind it. Soul fire
     * spreads AND stays up, so the burnt ground keeps biting while the front moves on.
     */
    public static final int SOUL_FIRE_BURN_TURNS = FIRE_BURN_TURNS + 1;

    /** Turns the burnt-out tile stays a magma block before it finishes burning. */
    public static final int MAGMA_TURNS = 1;

    /** Turns a burnt netherrack tile refuses to catch light again. */
    public static final int NETHERRACK_COOLDOWN_TURNS = 1;

    /** Burning level and duration applied to anything standing in a flame tile. */
    public static final int BURN_LEVEL = 2;
    public static final int BURN_TURNS = 2;

    /** Soul fire burns hotter: same duration, one level higher. */
    public static final int SOUL_BURN_LEVEL = 3;

    /** True if this tile can catch fire (and isn't already fire/lava/water). */
    public static boolean isFlammable(GridTile tile) {
        if (tile == null) return false;
        TileType t = tile.getType();
        // Already burning or non-flammable terrain types. EXIT is structural - burning
        // away the way out would be able to strand the party.
        if (t.isFlames() || t == TileType.LAVA || t == TileType.WATER
            || t == TileType.DEEP_WATER || t == TileType.VOID
            || t == TileType.POWDER_SNOW) {
            return false;
        }
        // Stealth plants always burn.
        if (t == TileType.TALL_GRASS || t == TileType.TALL_FERN) return true;
        // Obstacles (and any tile) made of a flammable block burn.
        return isFlammableBlock(tile.getBlockType());
    }

    /**
     * True if a burnt-out tile of this block comes back as itself instead of turning to ash.
     * The nether's own ground: scorching netherrack or soul sand down to overworld dirt would
     * leave a hole in the biome that never grows back.
     *
     * <p>Only fuel is asked this question. Ground that was never flammable - stone, sand,
     * gravel - can only be burnt by soul fire, and CombatManager restores that outright: a
     * tile with no fuel in it had nothing to turn into ash.
     */
    public static boolean restoresAfterBurning(Block block) {
        return NETHER_GROUND.contains(block);
    }

    private static final java.util.Set<Block> NETHER_GROUND = java.util.Set.of(
        Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.SOUL_SOIL);

    /**
     * What a burnt-out tile is left as, given what was burning on it.
     *
     * <p>Everything used to become dirt, on the reasoning that a burnt tile is ash. That reads
     * correctly under grass and leaves and utterly wrong under anything built: setting fire to
     * a plank floor or a fence line left a field of soil hanging where the boards had been, as
     * though the fire had grown a garden. Worked wood burns down to charcoal instead, which is
     * both what happens and what looks like it happened.
     *
     * <p>Ground keeps the old answer. Soil under burnt grass IS what is left.
     */
    public static Block residueFor(Block fuel) {
        if (fuel == null) return Blocks.DIRT;
        return isWoodenBlock(fuel) ? Blocks.COAL_BLOCK : Blocks.DIRT;
    }

    /**
     * Worked wood: anything a builder placed rather than anything that grew.
     *
     * <p>Leaves and saplings are deliberately NOT here - they are foliage, and foliage burning
     * to soil is right. This is planks, logs, fences, doors and the rest of the carpentry, which
     * is what the vanilla tags below already collect.
     */
    public static boolean isWoodenBlock(Block block) {
        if (block == null) return false;
        var state = block.getDefaultState();
        return state.isIn(net.minecraft.registry.tag.BlockTags.PLANKS)
            || state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
            || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_FENCES)
            || state.isIn(net.minecraft.registry.tag.BlockTags.FENCE_GATES)
            || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_SLABS)
            || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_STAIRS)
            || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_DOORS)
            || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_TRAPDOORS)
            || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_PRESSURE_PLATES);
    }

    /**
     * True if a fire lit on this floor burns as SOUL fire. Mirrors vanilla: flames on soul
     * sand or soul soil come up blue. An ordinary fire reaching a soul sand tile turns blue
     * on its own; soul fire, unlike ordinary fire, then carries itself onto whatever it
     * spreads to regardless of ground (see {@link #canSoulBurn}).
     */
    public static boolean burnsSoulFire(Block floor) {
        return floor == Blocks.SOUL_SAND || floor == Blocks.SOUL_SOIL;
    }

    /**
     * True if a flame can stand on this tile at all, fuel or no fuel. The only things that
     * turn one away are tiles with no block to burn or a block that beats fire outright:
     * open void, water, and lava. Tiles still cooling down from an earlier burn are refused
     * by the caller, which owns that timer.
     *
     * <p>Two things need this rather than {@link #isFlammable}. Soul fire, which needs no
     * fuel and only stops when it runs out of arena. And a player striking a light by hand:
     * you can set a torch to bare stone, it just won't go anywhere - fuel is what decides
     * whether a fire SPREADS, not whether it can be lit in the first place.
     */
    public static boolean canHoldFlame(GridTile tile) {
        if (tile == null) return false;
        TileType t = tile.getType();
        if (t.isFlames()) return false;           // already alight
        return t != TileType.LAVA && t != TileType.WATER
            && t != TileType.DEEP_WATER && t != TileType.VOID
            // Powder snow is not ground a flame can stand on. It is not fuel either, so
            // without this it fell through to "any tile that isn't water or void" and
            // accepted a light: the flame block could not survive above it so nothing was
            // ever visible, but the tile still entered the burn cycle and then happily
            // spread fire to its neighbours.
            && t != TileType.POWDER_SNOW;
    }

    /**
     * Ground blocks that carry a fire on their own, with nothing planted on them. This is
     * what lets a burn creep across an open field instead of only hopping between planted
     * obstacles. DIRT is deliberately absent - it is what a burnt tile becomes, and making
     * it fuel would let a fire re-light its own ashes.
     */
    private static final java.util.Set<Block> GROUND_FUEL = java.util.Set.of(
        // Living ground - the growth on top is the fuel, not the earth under it. Bare dirt
        // and its worked forms (coarse dirt, rooted dirt, dirt path, farmland, podzol) are
        // deliberately absent: they are what soil looks like once it has nothing left to
        // burn, which is also what a burnt tile becomes.
        Blocks.GRASS_BLOCK, Blocks.MYCELIUM, Blocks.MOSS_BLOCK,
        // Soul ground catches like the rest of the nether, and comes up blue - see
        // burnsSoulFire. Without these two a soul sand valley arena could not burn at all.
        Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.SOUL_SOIL);

    /**
     * Plant-family blocks vanilla doesn't collect under one tag. Anything green, leafy or
     * fungal that a fire would obviously eat: moss, vines, canes, crops, mushrooms, the
     * nether's roots and weeds.
     */
    private static final java.util.Set<Block> PLANT_FUEL = java.util.Set.of(
        Blocks.MOSS_CARPET, Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.HANGING_ROOTS,
        Blocks.SUGAR_CANE, Blocks.DEAD_BUSH, Blocks.SPORE_BLOSSOM, Blocks.LILY_PAD,
        Blocks.BIG_DRIPLEAF, Blocks.SMALL_DRIPLEAF, Blocks.SWEET_BERRY_BUSH,
        Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM,
        Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM,
        Blocks.CRIMSON_FUNGUS, Blocks.WARPED_FUNGUS,
        Blocks.CRIMSON_ROOTS, Blocks.WARPED_ROOTS, Blocks.NETHER_SPROUTS,
        Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT,
        Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT,
        Blocks.NETHER_WART_BLOCK, Blocks.WARPED_WART_BLOCK, Blocks.SHROOMLIGHT);

    /** True if a raw block is a flammable material. */
    public static boolean isFlammableBlock(Block block) {
        if (block == null) return false;
        if (block == Blocks.CACTUS) return true; // cactus burns in our combat fiction
        if (GROUND_FUEL.contains(block) || PLANT_FUEL.contains(block)) return true;
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
        if (state.isIn(net.minecraft.registry.tag.BlockTags.CROPS)) return true;
        if (state.isIn(net.minecraft.registry.tag.BlockTags.CAVE_VINES)) return true;
        // Ground cover MC itself treats as "a sapling could replace this": short grass,
        // ferns, vines, dead bushes - the exact set that varies by version, so read the
        // tag rather than naming blocks that got renamed between 1.21.1 and 1.21.5.
        if (state.isIn(net.minecraft.registry.tag.BlockTags.REPLACEABLE_BY_TREES)) return true;
        // A few common flammable blocks not covered by the tags above.
        return block == Blocks.HAY_BLOCK || block == Blocks.BOOKSHELF
            || block == Blocks.SCAFFOLDING || block == Blocks.BAMBOO
            || block == Blocks.DRIED_KELP_BLOCK || block == Blocks.TARGET
            || block == Blocks.COBWEB;
    }
}
