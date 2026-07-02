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
                case VfxPrimitive.DirectionalBurst d -> runDirectionalBurstScaled(world, resolver, d, intensity);
                case VfxPrimitive.Shockwave s -> runShockwave(world, ctx, s);
                case VfxPrimitive.TileRingFlash t -> runTileRingFlash(world, resolver, ctx, t);
                case VfxPrimitive.BounceEntities b -> {
                    // Resolved against the arena at dispatch time (needs ctx) - route
                    // through the client bucket so it ships in the same payload batch.
                    if (intensity >= 0.01f) clientBucket.add(b);
                }
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

    /**
     * Spray particles along the context's origin→target direction. Uses the
     * count=0 velocity form of {@code spawnParticles} per particle so each one
     * actually travels along the hit direction instead of jittering in place.
     */
    private static void runDirectionalBurstScaled(ServerWorld w, VfxAnchorResolver r,
                                                  VfxPrimitive.DirectionalBurst d, float intensity) {
        if (intensity < 0.01f) return;
        Vec3d at = r.resolve(d.at());
        Vec3d origin = r.resolve(VfxAnchor.ORIGIN);
        Vec3d target = r.resolve(VfxAnchor.TARGET);
        double dx = target.x - origin.x, dz = target.z - origin.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        // Self-targeted / zero-length hits fall back to a flat +z spray.
        double baseYaw = len > 0.001 ? Math.atan2(dz, dx) : Math.PI / 2;
        int count = Math.max(1, Math.round(d.count() * intensity));
        double halfSpread = Math.toRadians(Math.max(0, d.spreadDegrees())) / 2.0;
        java.util.Random rng = new java.util.Random();
        for (int i = 0; i < count; i++) {
            double yaw = baseYaw + (rng.nextDouble() * 2 - 1) * halfSpread;
            double up = d.upwardBias() * (0.5 + rng.nextDouble());
            double vx = Math.cos(yaw);
            double vz = Math.sin(yaw);
            // count=0: offsets act as the velocity direction, speed as the multiplier.
            w.spawnParticles(d.type(), at.x, at.y + 0.1, at.z, 0,
                vx, up, vz, d.speed() * (0.6 + rng.nextDouble() * 0.8));
        }
    }

    /**
     * Expand a Shockwave into one scheduled phase per tile ring so the wave
     * physically travels outward over ticks: ring particles + dust, a tile
     * flash under the wave front, a pitch-falling thump, and a visual knock-up
     * of the mobs that ring passes under.
     *
     * <p>The epicenter is resolved ONCE, here at impact time, and frozen as a
     * ground-level AtPos: a wave must not chase a target that gets discarded
     * or knocked away mid-ripple, and its rings must hug the floor rather than
     * hover at the anchor entity's chest height.
     */
    private static void runShockwave(ServerWorld world, VfxContext ctx, VfxPrimitive.Shockwave s) {
        Vec3d resolved = new VfxAnchorResolver(ctx, world).resolve(s.center());
        double groundY = ctx.arena() != null
            ? ctx.arena().getOrigin().getY() + 1.1   // floor top surface (feet plane)
            : resolved.y - 0.9;
        VfxAnchor epicenter = new VfxAnchor.AtPos(new Vec3d(resolved.x, groundY, resolved.z));
        VfxDescriptor.Builder b = VfxDescriptor.builder();
        int max = Math.max(1, s.maxRadiusTiles());
        for (int radius = 1; radius <= max; radius++) {
            // Falloff: outer rings are visibly weaker so the wave reads as dissipating.
            float falloff = 1.0f - (radius - 1) / (float) Math.max(1, max) * 0.5f;
            VfxDescriptor.PhaseBuilder pb = b.phase(radius * Math.max(1, s.ticksPerRing()));
            pb.ring(epicenter, radius + 0.35, s.ringParticle(), 8 + radius * 8);
            if (s.dustParticle() != null) {
                pb.ring(epicenter, radius - 0.15, s.dustParticle(), 6 + radius * 5);
            }
            if (s.flashColor() != 0) {
                pb.tileRingFlash(epicenter, radius, s.flashColor(), s.flashDurationTicks());
            }
            if (s.bounceAmplitude() > 0.01f) {
                pb.bounce(epicenter, radius, radius,
                    s.bounceAmplitude() * falloff, s.bounceDurationTicks());
            }
            if (s.ringSound() != null) {
                pb.sound(epicenter, s.ringSound(), 0.7f * falloff, 1.25f - radius * 0.12f);
            }
        }
        PhaseScheduler.of(world).schedule(b.build(), ctx, world.getTime());
    }

    /** Flash the Chebyshev tile ring at {@code radiusTiles} around the anchor's grid tile. */
    private static void runTileRingFlash(ServerWorld world, VfxAnchorResolver r,
                                         VfxContext ctx, VfxPrimitive.TileRingFlash t) {
        com.crackedgames.craftics.core.GridArena arena = ctx.arena();
        if (arena == null) return;
        Vec3d pos = r.resolve(t.center());
        com.crackedgames.craftics.core.GridPos center = worldToGrid(arena, pos);
        java.util.List<Integer> packed = new java.util.ArrayList<>();
        int radius = Math.max(0, t.radiusTiles());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue; // ring band only
                int gx = center.x() + dx, gz = center.z() + dz;
                if (!arena.isInBounds(gx, gz)) continue;
                packed.add(gx);
                packed.add(gz);
            }
        }
        if (packed.isEmpty()) return;
        int[] arr = new int[packed.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = packed.get(i);
        com.crackedgames.craftics.network.TileFlashPayload payload =
            new com.crackedgames.craftics.network.TileFlashPayload(arr, t.color(), t.durationTicks());
        for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }

    /** Inverse of {@link com.crackedgames.craftics.core.GridArena#gridToBlockPos}. */
    static com.crackedgames.craftics.core.GridPos worldToGrid(
            com.crackedgames.craftics.core.GridArena arena, Vec3d pos) {
        return new com.crackedgames.craftics.core.GridPos(
            (int) Math.floor(pos.x) - arena.getOrigin().getX(),
            (int) Math.floor(pos.z) - arena.getOrigin().getZ());
    }

    private static void runDebrisScaled(ServerWorld w, VfxAnchorResolver r, VfxPrimitive.DebrisParticles d, float intensity) {
        if (intensity < 0.01f) return;
        int count = Math.max(1, Math.round(d.count() * intensity));
        net.minecraft.util.math.Vec3d p = r.resolve(d.at());
        net.minecraft.particle.BlockStateParticleEffect effect = new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK, d.state());
        w.spawnParticles(effect, p.x, p.y, p.z, count, 0.3, 0.2, 0.3, d.speed());
    }
}
