package com.crackedgames.craftics.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * On-demand sweep of the fixed per-world event rooms for stray trader NPCs - piglins,
 * zombified piglins, villagers, wandering traders - left behind by interrupted events
 * or by pre-immunity piglin zombification. Backs the {@code /craftics cleanup_events}
 * admin command so existing worlds can be fixed immediately instead of waiting for the
 * next event to build at the room (which clears it via {@code CombatManager.clearStrayEventNpcs}).
 *
 * <p>Event-room chunks are normally unloaded, and a freshly force-loaded chunk does not
 * expose its entities the same tick. So a request force-loads each idle room's chunks,
 * waits a few ticks for the entity sections to load, then sweeps and releases them.
 * Rooms whose chunks are already force-loaded (an event is live there) are skipped so a
 * running event's NPC is never yanked out from under it.
 */
public final class EventRoomCleanup {
    private EventRoomCleanup() {}

    private static final int LOAD_DELAY_TICKS = 4;

    private static final class Request {
        final ServerWorld world;
        final List<Box> boxes;
        final List<long[]> chunksToRelease; // {cx, cz} we force-loaded and must release
        int ticksLeft = LOAD_DELAY_TICKS;
        final IntConsumer onDone;
        Request(ServerWorld world, List<Box> boxes, List<long[]> chunksToRelease, IntConsumer onDone) {
            this.world = world;
            this.boxes = boxes;
            this.chunksToRelease = chunksToRelease;
            this.onDone = onDone;
        }
    }

    private static final List<Request> PENDING = new ArrayList<>();

    /**
     * Force-load the idle event rooms at {@code origins} in {@code world} and schedule a
     * sweep a few ticks later. {@code onDone} receives the total stray count removed.
     */
    public static void request(ServerWorld world, List<BlockPos> origins, IntConsumer onDone) {
        List<Box> boxes = new ArrayList<>();
        List<long[]> toRelease = new ArrayList<>();
        var alreadyForced = world.getForcedChunks();
        for (BlockPos o : origins) {
            if (o == null) continue;
            int minCX = (o.getX() - 2) >> 4, maxCX = (o.getX() + 11) >> 4;
            int minCZ = (o.getZ() - 11) >> 4, maxCZ = (o.getZ() + 11) >> 4;
            // Skip rooms with a live event (their chunks are already force-loaded) so we
            // never discard an in-progress event's NPC.
            boolean active = false;
            for (int cx = minCX; cx <= maxCX && !active; cx++) {
                for (int cz = minCZ; cz <= maxCZ && !active; cz++) {
                    if (alreadyForced.contains(ChunkPos.toLong(cx, cz))) active = true;
                }
            }
            if (active) continue;

            boxes.add(new Box(o.getX() - 2, o.getY() - 1, o.getZ() - 11,
                              o.getX() + 11, o.getY() + 8, o.getZ() + 11));
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    world.setChunkForced(cx, cz, true);
                    toRelease.add(new long[]{cx, cz});
                }
            }
        }
        PENDING.add(new Request(world, boxes, toRelease, onDone));
    }

    /** Process pending cleanup requests. Call once per server tick. */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        var it = PENDING.iterator();
        while (it.hasNext()) {
            Request r = it.next();
            if (--r.ticksLeft > 0) continue;
            int removed = 0;
            for (Box box : r.boxes) {
                for (Entity e : r.world.getOtherEntities(null, box, EventRoomCleanup::isStray)) {
                    e.discard();
                    removed++;
                }
            }
            for (long[] c : r.chunksToRelease) {
                r.world.setChunkForced((int) c[0], (int) c[1], false);
            }
            if (r.onDone != null) r.onDone.accept(removed);
            it.remove();
        }
    }

    private static boolean isStray(Entity e) {
        return e instanceof AbstractPiglinEntity        // piglin / brute
            || e instanceof ZombifiedPiglinEntity       // converted in the overworld
            || e instanceof VillagerEntity
            || e instanceof WanderingTraderEntity;
    }
}
