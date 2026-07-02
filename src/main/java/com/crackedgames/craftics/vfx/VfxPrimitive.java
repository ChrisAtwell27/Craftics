package com.crackedgames.craftics.vfx;

import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The alphabet of VFX effects. World-space primitives execute on the server
 * (which auto-broadcasts particles to nearby clients). Client-side primitives
 * are packeted to arena observers via VfxClientPayload.
 */
public sealed interface VfxPrimitive {

    // ---------- World-space (server) ----------

    /** Generic particle emission at an anchor. */
    record SpawnParticles(ParticleEffect type, VfxAnchor anchor,
                          int count, Vec3d spread, double speed) implements VfxPrimitive {}

    /** Arcing particle trail from {@code from} to {@code to}. {@code secondary} may be null. */
    record Trail(VfxAnchor from, VfxAnchor to,
                 ParticleEffect primary, @Nullable ParticleEffect secondary,
                 int count, double arcHeight) implements VfxPrimitive {}

    /** Expanding ring of particles on the horizontal plane around the anchor. */
    record Ring(VfxAnchor center, double radius,
                ParticleEffect type, int count) implements VfxPrimitive {}

    /** Particles converging inward toward the anchor - used for spell windups. */
    record Converge(VfxAnchor center, double radius,
                    ParticleEffect type, int count) implements VfxPrimitive {}

    /**
     * Particles sprayed from {@code at} along the context's origin→target
     * direction, so the burst visibly "carries through" the hit instead of
     * puffing uniformly. {@code spreadDegrees} fans the spray in a cone around
     * the hit direction; {@code upwardBias} tilts it skyward (0 = flat).
     * Resolved at fire time, so it follows moving attackers/targets.
     */
    record DirectionalBurst(VfxAnchor at, ParticleEffect type, int count,
                            double speed, double spreadDegrees, double upwardBias) implements VfxPrimitive {}

    /**
     * Multi-tick ground shockwave rippling outward from the anchor: one ring of
     * particles per tile radius, each {@code ticksPerRing} apart, with an
     * optional grid-tile flash ({@code flashColor} 0 disables), an optional
     * per-ring thump ({@code ringSound} null disables), and an optional visual
     * knock-up of arena mobs the wave passes under ({@code bounceAmplitude} 0
     * disables). Expanded into scheduled phases at fire time so a single
     * primitive produces the whole traveling wave.
     */
    record Shockwave(VfxAnchor center, int maxRadiusTiles, int ticksPerRing,
                     ParticleEffect ringParticle, @Nullable ParticleEffect dustParticle,
                     int flashColor, int flashDurationTicks,
                     float bounceAmplitude, int bounceDurationTicks,
                     @Nullable SoundEvent ringSound) implements VfxPrimitive {}

    /** Flash the ring of arena tiles at exactly {@code radiusTiles} (Chebyshev)
     *  around the anchor's grid tile. Radius 0 flashes just the center tile.
     *  No-op without an arena in context. */
    record TileRingFlash(VfxAnchor center, int radiusTiles,
                         int color, int durationTicks) implements VfxPrimitive {}

    /**
     * Visually bounce living arena mobs whose Chebyshev tile distance from the
     * anchor lies in {@code [minRadiusTiles, maxRadiusTiles]}. The bounce is a
     * client-side render offset (a parabolic hop with a small rebound) - no
     * gameplay position changes. Flying mobs are skipped.
     */
    record BounceEntities(VfxAnchor center, int minRadiusTiles, int maxRadiusTiles,
                          float amplitude, int durationTicks) implements VfxPrimitive {}

    /** One sound played in the world. */
    record PlaySound(VfxAnchor at, SoundEvent sound,
                     float volume, float pitch) implements VfxPrimitive {}

    // ---------- Block physics (server) ----------

    /** Spawns a FallingBlockEntity with velocity. If the entity lands inside an
     *  arena before {@code lifetimeTicks} elapse, vanilla places the block and
     *  VfxBlockTracker marks the tile as a VFX obstacle. If the lifetime expires
     *  in the air first, the entity is discarded with a poof - no block placed. */
    record LaunchBlock(VfxAnchor origin, Vec3d velocity,
                       BlockState state, int lifetimeTicks) implements VfxPrimitive {}

    /** Like LaunchBlock but the block state is read from the world floor at the anchor
     *  at fire time - debris matches the actual terrain the target is standing on. */
    record LaunchFloorBlock(VfxAnchor anchor, Vec3d velocity,
                             int lifetimeTicks) implements VfxPrimitive {}

    /** Lightweight block-shatter debris via ParticleTypes.BLOCK. No entity spawned. */
    record DebrisParticles(VfxAnchor at, BlockState state,
                           int count, double speed) implements VfxPrimitive {}

    // ---------- Client-side feedback ----------

    /** Camera shake for arena observers. Intensity 0.0-1.5. */
    record Shake(float intensity, int durationTicks) implements VfxPrimitive {}

    /** Full-screen flash overlay (ARGB) for the duration. */
    record ScreenFlash(int argb, int durationTicks) implements VfxPrimitive {}

    /** Freeze client-side animation/effect ticks for N ticks. Does not affect gameplay. */
    record HitPause(int freezeTicks) implements VfxPrimitive {}

    /** World-anchored floating text that rises and fades. */
    record FloatingText(VfxAnchor at, String text,
                        int color, int lifetimeTicks) implements VfxPrimitive {}

    /** Vignette pulse - extends the existing poison/burning/blindness vignette slots. */
    record Vignette(VignetteType type, int level, int durationTicks) implements VfxPrimitive {}

    enum VignetteType { EXECUTE, POISON, BURNING, BLINDNESS, FROST }
}
