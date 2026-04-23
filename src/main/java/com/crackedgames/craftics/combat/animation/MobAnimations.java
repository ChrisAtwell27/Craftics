package com.crackedgames.craftics.combat.animation;

import com.crackedgames.craftics.component.CrafticsAnimComponent;
import com.crackedgames.craftics.component.CrafticsComponents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Helper for setting a mob's {@link AnimState} and firing the particle/sound
 * burst associated with that state. Server-side only.
 *
 * <p>This is the single entry point combat code uses — callers should not write
 * directly to the component so we always emit the matching VFX in lockstep.
 */
public final class MobAnimations {
    private MobAnimations() {}

    /** Set a pose on {@code mob} and spawn the state's signature particles/sound. */
    public static void set(MobEntity mob, AnimState state) {
        if (mob == null || !(mob.getEntityWorld() instanceof ServerWorld world)) return;
        CrafticsAnimComponent comp = CrafticsComponents.ANIM.getNullable(mob);
        if (comp == null) return;
        comp.setState(state, world.getTime());
        spawnStateFx(world, mob, state);
    }

    /** Reset a mob to IDLE without spawning any FX. */
    public static void reset(MobEntity mob) {
        if (mob == null || !(mob.getEntityWorld() instanceof ServerWorld world)) return;
        CrafticsAnimComponent comp = CrafticsComponents.ANIM.getNullable(mob);
        if (comp == null) return;
        comp.reset(world.getTime());
    }

    /**
     * Per-tick maintenance: reset any mob whose state has outlived its max
     * duration to IDLE. Call from the combat tick so a forgotten trigger can't
     * freeze a mob in a pose forever.
     */
    public static void tick(MobEntity mob) {
        if (mob == null || !(mob.getEntityWorld() instanceof ServerWorld world)) return;
        CrafticsAnimComponent comp = CrafticsComponents.ANIM.getNullable(mob);
        if (comp == null) return;
        AnimState s = comp.getState();
        if (s == AnimState.IDLE) return;
        long age = world.getTime() - comp.getStartTick();
        if (age >= s.maxTicks()) comp.reset(world.getTime());
    }

    private static void spawnStateFx(ServerWorld world, MobEntity mob, AnimState state) {
        double x = mob.getX();
        double y = mob.getY() + mob.getHeight() * 0.6;
        double z = mob.getZ();
        switch (state) {
            case WINDUP -> spawnConverge(world, mob, ParticleTypes.CRIT, 12, 1.2);
            case ATTACK -> {
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0, 0, 0, 0.0);
                playSound(world, mob, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.0f);
            }
            case RECOIL -> world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 6, 0.25, 0.15, 0.25, 0.02);
            case HIT -> {
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 8, 0.2, 0.2, 0.2, 0.1);
                world.spawnParticles(ParticleTypes.CRIT, x, y, z, 4, 0.2, 0.2, 0.2, 0.15);
            }
            case CAST -> spawnConverge(world, mob, ParticleTypes.ENCHANT, 20, 1.6);
            case ROAR -> {
                for (int i = 0; i < 24; i++) {
                    double ang = Math.PI * 2 * i / 24.0;
                    double rx = Math.cos(ang);
                    double rz = Math.sin(ang);
                    world.spawnParticles(ParticleTypes.EXPLOSION, x + rx, mob.getY() + 0.1, z + rz,
                        1, 0, 0, 0, 0.0);
                }
                playSound(world, mob, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.1f);
            }
            case STUNNED -> world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                x, mob.getY() + mob.getHeight() + 0.4, z, 3, 0.15, 0.05, 0.15, 0.0);
            case IDLE -> { /* no FX on return to idle */ }
        }
    }

    private static void spawnConverge(ServerWorld world, MobEntity mob, ParticleEffect type,
                                      int count, double radius) {
        double cx = mob.getX();
        double cy = mob.getY() + mob.getHeight() * 0.5;
        double cz = mob.getZ();
        for (int i = 0; i < count; i++) {
            double ang = Math.PI * 2 * i / count;
            double px = cx + Math.cos(ang) * radius;
            double pz = cz + Math.sin(ang) * radius;
            double vx = (cx - px) * 0.1;
            double vz = (cz - pz) * 0.1;
            world.spawnParticles(type, px, cy, pz, 1, vx, 0.02, vz, 0.05);
        }
    }

    private static void playSound(ServerWorld world, MobEntity mob, SoundEvent sound,
                                  float volume, float pitch) {
        world.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
            sound, SoundCategory.HOSTILE, volume, pitch);
    }
}
