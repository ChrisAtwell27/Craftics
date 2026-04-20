package com.crackedgames.craftics.vfx;

import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Executes a single VfxPhase against a ServerWorld.
 * World-space primitives emit particles/sounds/entities directly.
 * Client-side primitives are collected and sent via VfxClientPayload by {@link Vfx}.
 */
public final class VfxRunner {
    private VfxRunner() {}

    public static void runPhase(ServerWorld world, VfxContext ctx, VfxPhase phase, UUID runId) {
        VfxAnchorResolver resolver = new VfxAnchorResolver(ctx, world);
        float intensity = Math.max(0f, com.crackedgames.craftics.CrafticsMod.CONFIG.vfxIntensity());
        boolean hitPauseOk = com.crackedgames.craftics.CrafticsMod.CONFIG.hitPauseEnabled();
        List<VfxPrimitive> clientBucket = new ArrayList<>();
        for (VfxPrimitive p : phase.primitives()) {
            // Intensity scaling / accessibility gates for client-side primitives.
            if (p instanceof VfxPrimitive.Shake sh) {
                if (intensity < 0.01f) continue;
                clientBucket.add(new VfxPrimitive.Shake(sh.intensity() * intensity, sh.durationTicks()));
                continue;
            }
            if (p instanceof VfxPrimitive.HitPause hp) {
                if (!hitPauseOk) continue;
                clientBucket.add(hp);
                continue;
            }
            switch (p) {
                case VfxPrimitive.SpawnParticles s -> runSpawnParticlesScaled(world, resolver, s, intensity);
                case VfxPrimitive.Trail t -> runTrailScaled(world, resolver, t, intensity);
                case VfxPrimitive.Ring r -> runRingScaled(world, resolver, r, intensity);
                case VfxPrimitive.Converge c -> runConvergeScaled(world, resolver, c, intensity);
                case VfxPrimitive.PlaySound s -> runSound(world, resolver, s);
                case VfxPrimitive.LaunchBlock l -> VfxBlockTracker.of(world).launchInto(world, resolver.resolve(l.origin()), l.velocity(), l.state(), l.lifetimeTicks(), ctx.arena());
                case VfxPrimitive.LaunchFloorBlock l -> {
                    net.minecraft.util.math.Vec3d pos = resolver.resolve(l.anchor());
                    net.minecraft.util.math.BlockPos floorPos = net.minecraft.util.math.BlockPos.ofFloored(pos.x, pos.y - 1.0, pos.z);
                    net.minecraft.block.BlockState floorState = world.getBlockState(floorPos);
                    if (floorState.isAir()) {
                        // No floor block to sample → fallback to cobblestone so the slam still has debris
                        floorState = net.minecraft.block.Blocks.COBBLESTONE.getDefaultState();
                    }
                    VfxBlockTracker.of(world).launchInto(world, pos, l.velocity(), floorState, l.lifetimeTicks(), ctx.arena());
                }
                case VfxPrimitive.DebrisParticles d -> runDebrisScaled(world, resolver, d, intensity);

                // Client-side (non-scaled): pass through
                case VfxPrimitive.ScreenFlash s -> clientBucket.add(s);
                case VfxPrimitive.FloatingText s -> clientBucket.add(s);
                case VfxPrimitive.Vignette s -> clientBucket.add(s);

                // Shake and HitPause handled above; switch must still cover them for the sealed interface.
                case VfxPrimitive.Shake s -> { /* handled by early `continue` above */ }
                case VfxPrimitive.HitPause s -> { /* handled by early `continue` above */ }
            }
        }
        if (!clientBucket.isEmpty()) {
            Vfx.dispatchClientBucket(world, ctx, runId, clientBucket);
        }
    }

    private static void runSpawnParticlesScaled(ServerWorld w, VfxAnchorResolver r, VfxPrimitive.SpawnParticles s, float intensity) {
        if (intensity < 0.01f) return;
        int count = Math.max(1, Math.round(s.count() * intensity));
        net.minecraft.util.math.Vec3d p = r.resolve(s.anchor());
        w.spawnParticles(s.type(), p.x, p.y, p.z, count, s.spread().x, s.spread().y, s.spread().z, s.speed());
    }

    private static void runTrailScaled(ServerWorld w, VfxAnchorResolver r, VfxPrimitive.Trail t, float intensity) {
        if (intensity < 0.01f) return;
        int count = Math.max(1, Math.round(t.count() * intensity));
        net.minecraft.util.math.Vec3d from = r.resolve(t.from());
        net.minecraft.util.math.Vec3d to = r.resolve(t.to());
        for (int i = 0; i < count; i++) {
            double tt = (double) i / count;
            double x = from.x + (to.x - from.x) * tt;
            double y = from.y + (to.y - from.y) * tt + Math.sin(tt * Math.PI) * t.arcHeight();
            double z = from.z + (to.z - from.z) * tt;
            w.spawnParticles(t.primary(), x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            if (t.secondary() != null && i % 2 == 0) {
                w.spawnParticles(t.secondary(), x, y + 0.1, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private static void runRingScaled(ServerWorld w, VfxAnchorResolver r, VfxPrimitive.Ring ring, float intensity) {
        if (intensity < 0.01f) return;
        int count = Math.max(1, Math.round(ring.count() * intensity));
        net.minecraft.util.math.Vec3d c = r.resolve(ring.center());
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = c.x + Math.cos(angle) * ring.radius();
            double z = c.z + Math.sin(angle) * ring.radius();
            w.spawnParticles(ring.type(), x, c.y, z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private static void runConvergeScaled(ServerWorld w, VfxAnchorResolver r, VfxPrimitive.Converge cv, float intensity) {
        if (intensity < 0.01f) return;
        int count = Math.max(1, Math.round(cv.count() * intensity));
        net.minecraft.util.math.Vec3d c = r.resolve(cv.center());
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = c.x + Math.cos(angle) * cv.radius();
            double z = c.z + Math.sin(angle) * cv.radius();
            double vx = (c.x - x) * 0.1;
            double vz = (c.z - z) * 0.1;
            w.spawnParticles(cv.type(), x, c.y, z, 1, vx, 0.02, vz, 0.05);
        }
    }

    private static void runSound(ServerWorld w, VfxAnchorResolver r, VfxPrimitive.PlaySound s) {
        Vec3d p = r.resolve(s.at());
        w.playSound(null, p.x, p.y, p.z, s.sound(), SoundCategory.PLAYERS, s.volume(), s.pitch());
    }

    private static void runDebrisScaled(ServerWorld w, VfxAnchorResolver r, VfxPrimitive.DebrisParticles d, float intensity) {
        if (intensity < 0.01f) return;
        int count = Math.max(1, Math.round(d.count() * intensity));
        net.minecraft.util.math.Vec3d p = r.resolve(d.at());
        net.minecraft.particle.BlockStateParticleEffect effect = new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK, d.state());
        w.spawnParticles(effect, p.x, p.y, p.z, count, 0.3, 0.2, 0.3, d.speed());
    }
}
