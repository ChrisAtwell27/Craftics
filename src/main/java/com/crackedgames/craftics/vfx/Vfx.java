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
                default -> { /* non-client primitive slipped in — ignore */ }
            }
        }
        if (out.isEmpty()) return;
        com.crackedgames.craftics.network.VfxClientPayload payload =
            new com.crackedgames.craftics.network.VfxClientPayload(runId, 0, out);
        for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }
}
