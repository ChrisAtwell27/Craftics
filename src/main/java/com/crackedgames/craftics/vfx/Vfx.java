package com.crackedgames.craftics.vfx;

import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.UUID;

/** Public entry point. `dispatchClientBucket` finished in Task 11 once VfxClientPayload exists. */
public final class Vfx {
    private Vfx() {}

    public static UUID play(ServerWorld world, VfxDescriptor descriptor, VfxContext ctx) {
        return PhaseScheduler.of(world).schedule(descriptor, ctx, world.getTime());
    }

    public static void cancel(ServerWorld world, UUID runId) {
        PhaseScheduler.of(world).cancel(runId);
    }

    static void dispatchClientBucket(ServerWorld world, VfxContext ctx, UUID runId, List<VfxPrimitive> clientPrimitives) {
        List<com.crackedgames.craftics.network.VfxClientPayload.ClientPrim> out = new java.util.ArrayList<>();
        VfxAnchorResolver resolver = new VfxAnchorResolver(ctx, world);
        for (VfxPrimitive p : clientPrimitives) {
            switch (p) {
                case VfxPrimitive.Shake s -> out.add(new com.crackedgames.craftics.network.VfxClientPayload.ClientPrim.Shake(s.intensity(), s.durationTicks()));
                case VfxPrimitive.ScreenFlash s -> out.add(new com.crackedgames.craftics.network.VfxClientPayload.ClientPrim.ScreenFlash(s.argb(), s.durationTicks()));
                case VfxPrimitive.HitPause s -> out.add(new com.crackedgames.craftics.network.VfxClientPayload.ClientPrim.HitPause(s.freezeTicks()));
                case VfxPrimitive.FloatingText s -> {
                    net.minecraft.util.math.Vec3d pos = resolver.resolve(s.at());
                    out.add(new com.crackedgames.craftics.network.VfxClientPayload.ClientPrim.FloatingText(
                        pos.x, pos.y, pos.z, s.text(), s.color(), s.lifetimeTicks()));
                }
                case VfxPrimitive.Vignette s -> out.add(new com.crackedgames.craftics.network.VfxClientPayload.ClientPrim.Vignette(s.type().ordinal(), s.level(), s.durationTicks()));
                case VfxPrimitive.BounceEntities b -> {
                    int[] ids = resolveBounceTargets(world, ctx, b, resolver);
                    if (ids.length > 0) {
                        out.add(new com.crackedgames.craftics.network.VfxClientPayload.ClientPrim.EntityBounce(
                            ids, b.amplitude(), b.durationTicks()));
                    }
                }
                default -> { /* non-client primitive slipped in - ignore */ }
            }
        }
        if (out.isEmpty()) return;
        com.crackedgames.craftics.network.VfxClientPayload payload =
            new com.crackedgames.craftics.network.VfxClientPayload(runId, 0, out);
        for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * Collect the entity ids of living, grounded arena mobs whose Chebyshev
     * tile distance from the anchor lies within the primitive's radius band.
     * Multi-tile mobs (bosses) are deduped by entity id.
     */
    private static int[] resolveBounceTargets(ServerWorld world, VfxContext ctx,
                                              VfxPrimitive.BounceEntities b,
                                              VfxAnchorResolver resolver) {
        com.crackedgames.craftics.core.GridArena arena = ctx.arena();
        if (arena == null) return new int[0];
        com.crackedgames.craftics.core.GridPos center =
            VfxRunner.worldToGrid(arena, resolver.resolve(b.center()));
        java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
        for (java.util.Map.Entry<com.crackedgames.craftics.core.GridPos,
                com.crackedgames.craftics.combat.CombatEntity> e : arena.getOccupants().entrySet()) {
            com.crackedgames.craftics.combat.CombatEntity occupant = e.getValue();
            if (occupant == null || !occupant.isAlive() || occupant.isFlying()) continue;
            if (occupant.getMobEntity() == null) continue;
            int dist = Math.max(Math.abs(e.getKey().x() - center.x()),
                                Math.abs(e.getKey().z() - center.z()));
            if (dist < b.minRadiusTiles() || dist > b.maxRadiusTiles()) continue;
            ids.add(occupant.getMobEntity().getId());
        }
        int[] arr = new int[ids.size()];
        int i = 0;
        for (int id : ids) arr[i++] = id;
        return arr;
    }
}
