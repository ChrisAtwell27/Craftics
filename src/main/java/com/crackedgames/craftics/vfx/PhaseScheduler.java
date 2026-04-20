package com.crackedgames.craftics.vfx;

import net.minecraft.server.world.ServerWorld;

import java.util.*;

/**
 * Per-ServerWorld registry of pending VFX phases. Ticked from a single
 * ServerTickEvents.END_SERVER_TICK listener registered in CrafticsMod.
 */
public final class PhaseScheduler {

    private static final Map<ServerWorld, PhaseScheduler> INSTANCES = new WeakHashMap<>();

    public static PhaseScheduler of(ServerWorld world) {
        return INSTANCES.computeIfAbsent(world, w -> new PhaseScheduler());
    }

    public static void tickAll() {
        for (Map.Entry<ServerWorld, PhaseScheduler> e : INSTANCES.entrySet()) {
            e.getValue().tick(e.getKey());
        }
    }

    private final List<Scheduled> pending = new ArrayList<>();

    record Scheduled(UUID runId, long fireTick, VfxContext ctx, VfxPhase phase) {}

    public UUID schedule(VfxDescriptor descriptor, VfxContext ctx, long currentTick) {
        UUID runId = UUID.randomUUID();
        for (VfxPhase p : descriptor.phases()) {
            pending.add(new Scheduled(runId, currentTick + p.delayTicks(), ctx, p));
        }
        return runId;
    }

    public void cancel(UUID runId) {
        pending.removeIf(s -> s.runId().equals(runId));
    }

    public void clearAll() {
        pending.clear();
    }

    private void tick(ServerWorld world) {
        long now = world.getTime();
        Iterator<Scheduled> it = pending.iterator();
        while (it.hasNext()) {
            Scheduled s = it.next();
            if (s.fireTick() <= now) {
                try {
                    VfxRunner.runPhase(world, s.ctx(), s.phase(), s.runId());
                } catch (Throwable t) {
                    com.crackedgames.craftics.CrafticsMod.LOGGER.error(
                        "VFX phase crashed; dropping", t);
                }
                it.remove();
            }
        }
    }
}
