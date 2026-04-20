package com.crackedgames.craftics.vfx;

import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable multi-phase VFX effect. Build with {@link #builder()}.
 */
public record VfxDescriptor(List<VfxPhase> phases) {
    public VfxDescriptor {
        phases = List.copyOf(phases);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<VfxPhase> phases = new ArrayList<>();
        private PhaseBuilder current;

        public PhaseBuilder phase(int delayTicks) {
            flush();
            current = new PhaseBuilder(this, delayTicks);
            return current;
        }

        public VfxDescriptor build() {
            flush();
            return new VfxDescriptor(phases);
        }

        void flush() {
            if (current != null) {
                phases.add(current.toPhase());
                current = null;
            }
        }
    }

    public static final class PhaseBuilder {
        private final Builder parent;
        private final int delayTicks;
        private final List<VfxPrimitive> primitives = new ArrayList<>();

        PhaseBuilder(Builder parent, int delayTicks) {
            this.parent = parent;
            this.delayTicks = delayTicks;
        }

        public PhaseBuilder particles(ParticleEffect type, VfxAnchor anchor,
                                       int count, Vec3d spread, double speed) {
            primitives.add(new VfxPrimitive.SpawnParticles(type, anchor, count, spread, speed));
            return this;
        }

        public PhaseBuilder trail(VfxAnchor from, VfxAnchor to, ParticleEffect primary,
                                   @Nullable ParticleEffect secondary, int count, double arcHeight) {
            primitives.add(new VfxPrimitive.Trail(from, to, primary, secondary, count, arcHeight));
            return this;
        }

        public PhaseBuilder ring(VfxAnchor center, double radius, ParticleEffect type, int count) {
            primitives.add(new VfxPrimitive.Ring(center, radius, type, count));
            return this;
        }

        public PhaseBuilder converge(VfxAnchor center, double radius, ParticleEffect type, int count) {
            primitives.add(new VfxPrimitive.Converge(center, radius, type, count));
            return this;
        }

        public PhaseBuilder sound(VfxAnchor at, SoundEvent sound, float volume, float pitch) {
            primitives.add(new VfxPrimitive.PlaySound(at, sound, volume, pitch));
            return this;
        }

        public PhaseBuilder launchBlock(VfxAnchor origin, Vec3d velocity,
                                         BlockState state, int lifetimeTicks) {
            primitives.add(new VfxPrimitive.LaunchBlock(origin, velocity, state, lifetimeTicks));
            return this;
        }

        public PhaseBuilder launchFloorBlock(VfxAnchor anchor, Vec3d velocity, int lifetimeTicks) {
            primitives.add(new VfxPrimitive.LaunchFloorBlock(anchor, velocity, lifetimeTicks));
            return this;
        }

        public PhaseBuilder debris(VfxAnchor at, BlockState state, int count, double speed) {
            primitives.add(new VfxPrimitive.DebrisParticles(at, state, count, speed));
            return this;
        }

        public PhaseBuilder shake(float intensity, int durationTicks) {
            primitives.add(new VfxPrimitive.Shake(intensity, durationTicks));
            return this;
        }

        public PhaseBuilder screenFlash(int argb, int durationTicks) {
            primitives.add(new VfxPrimitive.ScreenFlash(argb, durationTicks));
            return this;
        }

        public PhaseBuilder hitPause(int freezeTicks) {
            primitives.add(new VfxPrimitive.HitPause(freezeTicks));
            return this;
        }

        public PhaseBuilder floatingText(VfxAnchor at, String text, int color, int lifetimeTicks) {
            primitives.add(new VfxPrimitive.FloatingText(at, text, color, lifetimeTicks));
            return this;
        }

        public PhaseBuilder vignette(VfxPrimitive.VignetteType type, int level, int durationTicks) {
            primitives.add(new VfxPrimitive.Vignette(type, level, durationTicks));
            return this;
        }

        public PhaseBuilder phase(int delayTicks) { return parent.phase(delayTicks); }

        public VfxDescriptor build() { return parent.build(); }

        VfxPhase toPhase() { return new VfxPhase(delayTicks, primitives); }
    }
}
