package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.combat.ProjectileSpawner;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * How a cast looks and sounds.
 *
 * <p>Roughly half of every old sherd method was particle code, and it was the half that made
 * the game-state changes hard to find. Worse, it was the half that varied least: nearly all of
 * them play a sound and gather particles at the caster, streak a trail to the target on a short
 * delay, and burst at the impact on a longer one. That shape is captured here, so the particle
 * and colour of a sherd are two fields rather than forty lines.
 *
 * <p>The genuinely bespoke choreography - Heart's rising helix, Shelter's stone dome, War Cry's
 * orbiting ring - stays expressible through {@link Builder#extraCast} and
 * {@link Builder#extraImpact}. Inventing a particle DSL rich enough to describe a helix would
 * be a worse trade than letting three sherds keep a lambda, and the escape hatch means no sherd
 * has to be flattened to fit the common shape.
 */
public final class SpellVisuals {

    /** A bespoke flourish, given the world and the position it should draw around. */
    @FunctionalInterface
    public interface Flourish {
        void draw(ServerWorld world, BlockPos pos);
    }

    private final ParticleEffect castParticle;
    private final int castCount;
    private final double convergeRadius;
    private final SoundEvent castSound;
    private final float castVolume;
    private final float castPitch;

    private final ParticleEffect trailParticle;
    private final ParticleEffect trailSecondary;
    private final int trailCount;
    private final double trailArc;
    private final int trailDelay;

    private final ParticleEffect impactParticle;
    private final int impactCount;
    private final double impactRingRadius;
    private final SoundEvent impactSound;
    private final float impactVolume;
    private final float impactPitch;
    private final int impactDelay;

    private final Flourish extraCast;
    private final Flourish extraImpact;

    private SpellVisuals(Builder b) {
        this.castParticle = b.castParticle;
        this.castCount = b.castCount;
        this.convergeRadius = b.convergeRadius;
        this.castSound = b.castSound;
        this.castVolume = b.castVolume;
        this.castPitch = b.castPitch;
        this.trailParticle = b.trailParticle;
        this.trailSecondary = b.trailSecondary;
        this.trailCount = b.trailCount;
        this.trailArc = b.trailArc;
        this.trailDelay = b.trailDelay;
        this.impactParticle = b.impactParticle;
        this.impactCount = b.impactCount;
        this.impactRingRadius = b.impactRingRadius;
        this.impactSound = b.impactSound;
        this.impactVolume = b.impactVolume;
        this.impactPitch = b.impactPitch;
        this.impactDelay = b.impactDelay;
        this.extraCast = b.extraCast;
        this.extraImpact = b.extraImpact;
    }

    public static Builder builder() { return new Builder(); }

    /** An empty visual set, for a step that should draw nothing of its own. */
    public static SpellVisuals none() { return builder().build(); }

    public int trailDelay() { return trailDelay; }
    public int impactDelay() { return impactDelay; }

    /** Draw the gather-at-the-caster phase. Runs immediately, at cast time. */
    public void playCast(ServerWorld world, BlockPos casterBlock) {
        if (castSound != null) {
            world.playSound(null, casterBlock, castSound, SoundCategory.PLAYERS, castVolume, castPitch);
        }
        if (castParticle != null && castCount > 0) {
            world.spawnParticles(castParticle, casterBlock.getX() + 0.5, casterBlock.getY() + 1.1,
                casterBlock.getZ() + 0.5, castCount, 0.25, 0.35, 0.25, 0.08);
        }
        if (castParticle != null && convergeRadius > 0) {
            ProjectileSpawner.spawnConverging(world, casterBlock, convergeRadius, castParticle, 8);
        }
        if (extraCast != null) extraCast.draw(world, casterBlock);
    }

    /** Streak from the caster to each place the spell landed. */
    public void playTrail(ServerWorld world, BlockPos from, List<BlockPos> targets) {
        if (trailParticle == null) return;
        for (BlockPos to : targets) {
            ProjectileSpawner.spawnSpellTrail(world, from, to, trailParticle, trailSecondary,
                trailCount, trailArc);
        }
    }

    /** Burst at one impact site. */
    public void playImpact(ServerWorld world, BlockPos at) {
        if (impactParticle != null && impactCount > 0) {
            world.spawnParticles(impactParticle, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
                impactCount, 0.45, 0.5, 0.45, 0.14);
        }
        if (impactParticle != null && impactRingRadius > 0) {
            ProjectileSpawner.spawnExpandingRing(world, at, impactRingRadius, impactParticle, 10);
        }
        if (impactSound != null) {
            world.playSound(null, at, impactSound, SoundCategory.PLAYERS, impactVolume, impactPitch);
        }
        if (extraImpact != null) extraImpact.draw(world, at);
    }

    public static final class Builder {
        private ParticleEffect castParticle;
        private int castCount = 10;
        private double convergeRadius = 1.0;
        private SoundEvent castSound;
        private float castVolume = 1.0f;
        private float castPitch = 1.0f;

        private ParticleEffect trailParticle;
        private ParticleEffect trailSecondary;
        private int trailCount = 12;
        private double trailArc = 0.8;
        private int trailDelay = 3;

        private ParticleEffect impactParticle;
        private int impactCount = 15;
        private double impactRingRadius = 0.6;
        private SoundEvent impactSound;
        private float impactVolume = 0.8f;
        private float impactPitch = 1.0f;
        private int impactDelay = 7;

        private Flourish extraCast;
        private Flourish extraImpact;

        public Builder cast(ParticleEffect p, SoundEvent s) {
            this.castParticle = p; this.castSound = s; return this;
        }
        public Builder castPitch(float volume, float pitch) {
            this.castVolume = volume; this.castPitch = pitch; return this;
        }
        public Builder castCount(int n) { this.castCount = n; return this; }
        public Builder converge(double radius) { this.convergeRadius = radius; return this; }

        public Builder trail(ParticleEffect p) { this.trailParticle = p; return this; }
        public Builder trail(ParticleEffect p, ParticleEffect secondary) {
            this.trailParticle = p; this.trailSecondary = secondary; return this;
        }
        public Builder trailShape(int count, double arc) {
            this.trailCount = count; this.trailArc = arc; return this;
        }
        public Builder trailDelay(int ticks) { this.trailDelay = ticks; return this; }

        public Builder impact(ParticleEffect p) { this.impactParticle = p; return this; }
        public Builder impact(ParticleEffect p, SoundEvent s) {
            this.impactParticle = p; this.impactSound = s; return this;
        }
        public Builder impactCount(int n) { this.impactCount = n; return this; }
        public Builder impactRing(double radius) { this.impactRingRadius = radius; return this; }
        public Builder impactPitch(float volume, float pitch) {
            this.impactVolume = volume; this.impactPitch = pitch; return this;
        }
        public Builder impactDelay(int ticks) { this.impactDelay = ticks; return this; }

        /** A bespoke flourish drawn at the caster, on top of the standard cast phase. */
        public Builder extraCast(Flourish f) { this.extraCast = f; return this; }
        /** A bespoke flourish drawn at each impact, on top of the standard burst. */
        public Builder extraImpact(Flourish f) { this.extraImpact = f; return this; }

        public SpellVisuals build() { return new SpellVisuals(this); }
    }
}
