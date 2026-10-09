package com.crackedgames.craftics.vfx;

import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Blocks that are only shown, never placed.
 *
 * <p>A spell that turns the floor to magma, raises a spike of stone or sends a wall of water
 * across the arena wants the block to be SEEN for a second and then be gone. Placing it for
 * real is the expensive way to get that: real water flows, real sand falls, a real cactus
 * snaps off its stalk, and - the one that has bitten this codebase repeatedly - any real block
 * still standing when the fight ends is read back as part of the arena the next time it is
 * visited, and is there for good.
 *
 * <p>So these are told to the players and to nobody else. The server's world never changes:
 * every player in it is sent "this position now looks like that", and when the hold runs out
 * they are sent what is really there. Nothing can leak, because there was never anything to
 * clean up - a ghost that outlives its fight is corrected by the next real update to that
 * position, and {@link #clearAll} corrects the rest as the fight ends.
 *
 * <p>What it costs: a ghost is not solid to anything the server moves, and the game rules
 * never see it. That is the point. A block that should change the fight - a wall, a pit, a
 * pool - is terrain, and belongs to the arena.
 */
public final class GhostBlocks {

    private static final Map<ServerWorld, GhostBlocks> INSTANCES = new WeakHashMap<>();

    public static GhostBlocks of(ServerWorld world) {
        return INSTANCES.computeIfAbsent(world, w -> new GhostBlocks());
    }

    /** Put back every ghost whose time is up, in every world. Once a server tick. */
    public static void tickAll() {
        for (Map.Entry<ServerWorld, GhostBlocks> entry : INSTANCES.entrySet()) {
            entry.getValue().tick(entry.getKey());
        }
    }

    private final GhostLedger ledger = new GhostLedger();

    /** A ghost that is not shown yet. See {@link #showAfterChange}. */
    private record Waiting(BlockPos pos, BlockState state, long from, int ticks) {}

    private final List<Waiting> waiting = new ArrayList<>();

    /** Show {@code state} at {@code pos} to everyone in the world for {@code ticks}. */
    public void show(ServerWorld world, BlockPos pos, BlockState state, int ticks) {
        if (state == null || ticks <= 0) return;
        send(world, pos, state);
        ledger.hold(pos, world.getTime() + ticks);
    }

    /**
     * Go on showing {@code state} at {@code pos} for {@code ticks}, though the block there
     * has just been changed for real.
     *
     * <p>For a block that is gone as far as the game is concerned and has yet to be seen to
     * go: something broken by a blow that has not landed yet. Showing it at once would not
     * work. The real change is sent to everyone at the start of the next tick, after whatever
     * was sent now, and would wipe the ghost off again. So this one waits a tick and goes out
     * behind it. Use {@link #release} to end it early, when the blow lands.
     */
    public void showAfterChange(ServerWorld world, BlockPos pos, BlockState state, int ticks) {
        if (state == null || ticks <= 0) return;
        waiting.add(new Waiting(pos.toImmutable(), state, world.getTime() + 1, ticks));
    }

    /** Stop showing a ghost at {@code pos} now, and show what is really there. */
    public void release(ServerWorld world, BlockPos pos) {
        boolean pending = waiting.removeIf(ghost -> ghost.pos().equals(pos));
        if (ledger.drop(pos) || pending) send(world, pos, world.getBlockState(pos));
    }

    private void tick(ServerWorld world) {
        long now = world.getTime();
        if (!waiting.isEmpty()) {
            var later = waiting.iterator();
            while (later.hasNext()) {
                Waiting ghost = later.next();
                if (ghost.from() > now) continue;
                later.remove();
                show(world, ghost.pos(), ghost.state(), ghost.ticks());
            }
        }
        if (ledger.isEmpty()) return;
        for (BlockPos pos : ledger.release(now)) {
            send(world, pos, world.getBlockState(pos));
        }
    }

    /** Show the truth everywhere, now. Called as a fight ends. */
    public void clearAll(ServerWorld world) {
        waiting.clear();
        for (BlockPos pos : ledger.releaseAll()) {
            send(world, pos, world.getBlockState(pos));
        }
    }

    private static void send(ServerWorld world, BlockPos pos, BlockState state) {
        BlockUpdateS2CPacket packet = new BlockUpdateS2CPacket(pos, state);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.networkHandler != null) player.networkHandler.sendPacket(packet);
        }
    }
}
