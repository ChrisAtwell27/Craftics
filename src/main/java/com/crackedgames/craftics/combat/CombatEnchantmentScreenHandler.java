package com.crackedgames.craftics.combat;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * The enchanting table as opened mid-battle from a held item.
 *
 * <p>Vanilla derives the three enchant levels by scanning for real bookshelf blocks
 * in a ring around the table. A combat table has no block behind it and the arena
 * floor has no shelves, so the vanilla scan always finds zero power and the player
 * is stuck with level-1 offers forever.
 *
 * <p>Instead the power comes from the bookshelves the player is <em>carrying</em>:
 * every bookshelf item in the inventory is one point of power, capped at
 * {@link #MAX_POWER} (what a full vanilla ring supplies). The shelves are not
 * consumed - like a real ring, they only need to be present.
 *
 * <p>Rather than reimplement vanilla's level/offer math (it has moved between
 * Minecraft versions), this builds a real, throwaway bookshelf ring in unused air
 * far above the arena and points vanilla's own scan at it. Vanilla then fills in
 * the levels and the offer previews exactly as it would at a real table. The ring
 * is razed when the screen closes. {@code onClosed}'s item-return callback still
 * runs against the player's own position, so nothing drops in the sky.
 */
public class CombatEnchantmentScreenHandler extends EnchantmentScreenHandler {

    /** Power a full vanilla bookshelf ring supplies; also our carried-shelf cap. */
    public static final int MAX_POWER = 15;

    /**
     * Enchant power a combat table has before any bookshelf is carried. Vanilla's scan
     * at zero power leaves every offer row empty and unclickable, which reads as a broken
     * table rather than a weak one. One shelf's worth keeps the bottom row live.
     */
    public static final int MIN_POWER = 1;

    /** Height above the arena floor where the scratch ring is built. */
    private static final int SCRATCH_Y_OFFSET = 40;

    /** Every live handler's ring, so combat teardown can raze one the player never closed. */
    private static final java.util.Set<CombatEnchantmentScreenHandler> OPEN_HANDLERS =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private final SwitchableContext context;

    private CombatEnchantmentScreenHandler(int syncId, PlayerInventory inv, SwitchableContext ctx) {
        super(syncId, inv, ctx);
        this.context = ctx;
    }

    /**
     * Build the handler and raise the scratch ring that vanilla's scan will find.
     * {@code playerPos} is where on-close item returns land.
     */
    public static CombatEnchantmentScreenHandler create(int syncId, PlayerInventory inv,
                                                        World world, BlockPos playerPos) {
        int power = Math.max(MIN_POWER, carriedBookshelfPower(inv));
        BlockPos scratch = playerPos.up(SCRATCH_Y_OFFSET);
        SwitchableContext ctx = new SwitchableContext(world, playerPos, scratch);
        if (world instanceof ServerWorld sw) {
            ctx.ringBlocks = raiseRing(sw, scratch, power);
        }
        CombatEnchantmentScreenHandler handler = new CombatEnchantmentScreenHandler(syncId, inv, ctx);
        OPEN_HANDLERS.add(handler);
        return handler;
    }

    /** Raze every ring still standing. Called when combat ends, so none is left in the sky. */
    public static void razeAllRings() {
        for (CombatEnchantmentScreenHandler handler : OPEN_HANDLERS) {
            handler.razeRing();
        }
        OPEN_HANDLERS.clear();
    }

    /** Total bookshelf items carried, clamped to {@link #MAX_POWER}. */
    public static int carriedBookshelfPower(PlayerInventory inv) {
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(Items.BOOKSHELF)) count += stack.getCount();
            if (count >= MAX_POWER) return MAX_POWER;
        }
        return count;
    }

    /**
     * Place {@code power} bookshelves on the vanilla power-provider offsets around
     * {@code center}, clearing the pocket between so the table has line of sight to each.
     * The arena lives in a void dimension, so this space is air - but if something is
     * there anyway it is remembered and put back by {@link #razeRing}, rather than the
     * ring quietly refusing to build and leaving the player at zero power.
     */
    private static List<PlacedBlock> raiseRing(ServerWorld world, BlockPos center, int power) {
        List<PlacedBlock> placed = new ArrayList<>();
        // Clear the 5x5x3 pocket. Vanilla's scan needs air on the tile halfway to each
        // shelf, or the shelf does not count.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = center.add(dx, dy, dz);
                    var previous = world.getBlockState(p);
                    if (previous.isAir()) continue;
                    placed.add(new PlacedBlock(p, previous));
                    world.setBlockState(p, Blocks.AIR.getDefaultState());
                }
            }
        }
        int remaining = power;
        for (BlockPos offset : net.minecraft.block.EnchantingTableBlock.POWER_PROVIDER_OFFSETS) {
            if (remaining <= 0) break;
            BlockPos p = center.add(offset);
            placed.add(new PlacedBlock(p, world.getBlockState(p)));
            world.setBlockState(p, Blocks.BOOKSHELF.getDefaultState());
            remaining--;
        }
        return placed;
    }

    /**
     * Put back everything {@link #raiseRing} touched. Safe to call twice.
     *
     * <p>Undone in reverse. A position can appear twice in the log - once when the pocket
     * was cleared, once when a shelf went down on top - and only the earliest record holds
     * what was truly there. Replaying forward would restore that block and then bury it
     * under the air the shelf displaced.
     */
    private void razeRing() {
        OPEN_HANDLERS.remove(this);
        if (context.ringBlocks.isEmpty()) return;
        if (context.world instanceof ServerWorld sw) {
            List<PlacedBlock> log = context.ringBlocks;
            for (int i = log.size() - 1; i >= 0; i--) {
                sw.setBlockState(log.get(i).pos(), log.get(i).previous());
            }
        }
        context.ringBlocks = List.of();
    }

    /** A block the ring overwrote, and what stood there before. */
    private record PlacedBlock(BlockPos pos, net.minecraft.block.BlockState previous) {}

    @Override
    public void onContentChanged(Inventory inventory) {
        // Vanilla's bookshelf scan runs inside this call; aim it at the ring.
        context.useScratch = true;
        try {
            super.onContentChanged(inventory);
        } finally {
            context.useScratch = false;
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        // The enchant itself re-derives its offers from the same scan, so the ring
        // must still be standing while the button resolves.
        context.useScratch = true;
        try {
            return super.onButtonClick(player, id);
        } finally {
            context.useScratch = false;
        }
    }

    @Override
    public void onClosed(PlayerEntity player) {
        // Runs against the player's position (useScratch is false), so the lapis and
        // the item being enchanted return to the player's feet, not the sky.
        super.onClosed(player);
        razeRing();
    }

    /**
     * There is no real enchanting-table block to stand beside, so the vanilla
     * "is the block still there and in range" check can only ever fail. Always pass, or
     * the screen snaps shut the tick after it opens.
     */
    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    /**
     * A {@link ScreenHandlerContext} whose {@code run} target flips between the
     * player's real position and the scratch ring. {@code get} always returns the
     * caller's default, which makes vanilla's {@code canUse} block-presence check
     * pass so the screen doesn't snap shut (there is no real table block).
     */
    private static final class SwitchableContext implements ScreenHandlerContext {
        private final World world;
        private final BlockPos playerPos;
        private final BlockPos scratchPos;
        private boolean useScratch = false;
        private List<PlacedBlock> ringBlocks = List.of();

        SwitchableContext(World world, BlockPos playerPos, BlockPos scratchPos) {
            this.world = world;
            this.playerPos = playerPos;
            this.scratchPos = scratchPos;
        }

        @Override
        public <T> java.util.Optional<T> get(BiFunction<World, BlockPos, T> getter) {
            return java.util.Optional.empty();
        }

        @Override
        public <T> T get(BiFunction<World, BlockPos, T> getter, T defaultValue) {
            return defaultValue;
        }

        @Override
        public void run(BiConsumer<World, BlockPos> consumer) {
            consumer.accept(world, useScratch ? scratchPos : playerPos);
        }
    }
}
