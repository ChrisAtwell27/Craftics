package com.crackedgames.craftics.combat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Shared eligibility check for using a held block as a temporary wall obstacle
 * during combat. A block is eligible only when it is:
 * <ul>
 *   <li>A {@link BlockItem} (it places a block in vanilla),</li>
 *   <li>A full opaque cube (rejects slabs, stairs, fences, panes, carpets, etc.),</li>
 *   <li>Not a {@link BlockEntityProvider} (rejects chests, hoppers, beds, signs, banners),</li>
 *   <li>Not multi-tile or hinged (rejects doors, tall plants, trapdoors, fence gates),</li>
 *   <li>Not on the explicit Craftics block-list of items that already have a combat use.</li>
 * </ul>
 *
 * <p>Server uses this to validate placement; client uses it to pick the
 * {@code USE_ITEM} action mode when the player is holding an eligible block.
 */
public final class WallBlocks {

    private WallBlocks() {}

    /** Block defaults the wall sticks around for. */
    public static final int WALL_DURATION_TURNS = 4;

    /** True if the stack is an eligible wall-block. */
    public static boolean isEligible(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return isEligibleItem(stack.getItem());
    }

    public static boolean isEligibleItem(Item item) {
        if (!(item instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block == null) return false;
        // Block entities (chests, beds, hoppers, signs, banners, brewing stands…)
        if (block instanceof BlockEntityProvider) return false;
        // Doors, tall plants, trapdoors, fence gates, beds, two-tall flowers,
        // anything that occupies multiple tiles or has a hinged interaction.
        if (isTallOrHinged(block)) return false;
        BlockState state = block.getDefaultState();
        // Full opaque cubes only — slabs, stairs, fences, panes, walls, carpets,
        // saplings, sugar cane, etc. all fail this check. The full-cube check
        // signature differs between MC versions, so route through stonecutter.
        if (!isOpaqueFullCube(state)) return false;
        if (BLOCK_LIST_ITEMS.contains(item)) return false;
        return true;
    }

    private static boolean isOpaqueFullCube(BlockState state) {
        //? if <=1.21.1 {
        return state.isOpaqueFullCube(net.minecraft.world.EmptyBlockView.INSTANCE, net.minecraft.util.math.BlockPos.ORIGIN);
        //?} else {
        /*return state.isOpaqueFullCube();
        *///?}
    }

    private static boolean isTallOrHinged(Block block) {
        return block instanceof net.minecraft.block.DoorBlock
            || block instanceof net.minecraft.block.TrapdoorBlock
            || block instanceof net.minecraft.block.FenceGateBlock
            || block instanceof net.minecraft.block.TallPlantBlock
            || block instanceof net.minecraft.block.BedBlock
            || block instanceof net.minecraft.block.PistonBlock;
    }

    /**
     * Items that pass the full-cube + non-block-entity gate but already have
     * a dedicated combat use (or shouldn't be placeable for balance reasons).
     */
    private static final java.util.Set<Item> BLOCK_LIST_ITEMS = java.util.Set.of(
        // Combat items the player might inadvertently fling as walls
        net.minecraft.item.Items.TNT,
        net.minecraft.item.Items.SLIME_BLOCK,
        net.minecraft.item.Items.HONEY_BLOCK,
        net.minecraft.item.Items.MAGMA_BLOCK,
        net.minecraft.item.Items.SOUL_SAND,
        net.minecraft.item.Items.SOUL_SOIL,
        net.minecraft.item.Items.POWDER_SNOW_BUCKET,
        net.minecraft.item.Items.BEACON,
        net.minecraft.item.Items.CONDUIT,
        net.minecraft.item.Items.OBSERVER,
        net.minecraft.item.Items.DISPENSER,
        net.minecraft.item.Items.DROPPER,
        net.minecraft.item.Items.NOTE_BLOCK,
        net.minecraft.item.Items.JUKEBOX,
        net.minecraft.item.Items.SPAWNER,
        net.minecraft.item.Items.CRAFTING_TABLE,
        net.minecraft.item.Items.FURNACE,
        net.minecraft.item.Items.BLAST_FURNACE,
        net.minecraft.item.Items.SMOKER,
        net.minecraft.item.Items.LOOM,
        net.minecraft.item.Items.STONECUTTER,
        net.minecraft.item.Items.GRINDSTONE,
        net.minecraft.item.Items.SMITHING_TABLE,
        net.minecraft.item.Items.FLETCHING_TABLE,
        net.minecraft.item.Items.CARTOGRAPHY_TABLE,
        net.minecraft.item.Items.CAULDRON,
        net.minecraft.item.Items.COMPOSTER,
        net.minecraft.item.Items.BARREL,
        net.minecraft.item.Items.LECTERN,
        net.minecraft.item.Items.BELL,
        net.minecraft.item.Items.RESPAWN_ANCHOR,
        net.minecraft.item.Items.ENCHANTING_TABLE,
        net.minecraft.item.Items.END_PORTAL_FRAME,
        net.minecraft.item.Items.BEDROCK
    );
}
