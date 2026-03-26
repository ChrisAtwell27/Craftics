package com.crackedgames.craftics.combat;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Spawns PARTICLE-ONLY projectile trails from source to target.
 * No real entities — purely cosmetic. Particles appear along the flight path
 * and vanish at the destination. Safe, no vanilla side effects.
 */
public class ProjectileSpawner {

    public static void spawnProjectile(ServerWorld world, BlockPos from, BlockPos to, String type) {
        double sx = from.getX() + 0.5, sy = from.getY() + 1.2, sz = from.getZ() + 0.5;
        double ex = to.getX() + 0.5, ey = to.getY() + 1.0, ez = to.getZ() + 0.5;

        switch (type != null ? type : "") {
            case "arrow", "frost_arrow", "crossbow" -> trailParticles(world, sx, sy, sz, ex, ey, ez, ParticleTypes.CRIT, 6);
            case "fireball" -> {
                trailParticles(world, sx, sy, sz, ex, ey, ez, ParticleTypes.FLAME, 8);
                trailParticles(world, sx, sy, sz, ex, ey, ez, ParticleTypes.LARGE_SMOKE, 4);
            }
            case "fire" -> trailParticles(world, sx, sy, sz, ex, ey, ez, ParticleTypes.FLAME, 6);
            case "trident" -> trailParticles(world, sx, sy, sz, ex, ey, ez, ParticleTypes.BUBBLE, 6);
            case "shulker_bullet" -> trailParticles(world, sx, sy, sz, ex, ey, ez, ParticleTypes.END_ROD, 6);
            case "sonic_boom" -> world.spawnParticles(ParticleTypes.SONIC_BOOM,
                (sx + ex) / 2, (sy + ey) / 2 + 0.5, (sz + ez) / 2, 1, 0, 0, 0, 0);
            default -> {
                // Potion splash
                world.spawnParticles(ParticleTypes.SPLASH, ex, ey + 0.5, ez, 12, 0.3, 0.3, 0.3, 0.1);
                world.spawnParticles(ParticleTypes.WITCH, ex, ey + 0.5, ez, 6, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }

    public static void spawnPlayerProjectile(ServerWorld world, BlockPos from, BlockPos to, boolean isTrident) {
        spawnProjectile(world, from, to, isTrident ? "trident" : "arrow");
    }

    /**
     * Spawn particles along a line from start to end — creates a trail effect.
     */
    private static void trailParticles(ServerWorld world,
                                         double sx, double sy, double sz,
                                         double ex, double ey, double ez,
                                         net.minecraft.particle.ParticleEffect particle, int count) {
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double x = sx + (ex - sx) * t;
            double y = sy + (ey - sy) * t + Math.sin(t * Math.PI) * 0.3; // slight arc
            double z = sz + (ez - sz) * t;
            world.spawnParticles(particle, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        // Impact burst at destination
        world.spawnParticles(particle, ex, ey, ez, 3, 0.1, 0.1, 0.1, 0.05);
    }

    /** Spawn impact particles at a position — for melee hits, AoE effects, etc. */
    public static void spawnImpact(ServerWorld world, BlockPos pos, String type) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        switch (type != null ? type : "") {
            case "melee" -> {
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.CRIT, x, y, z, 5, 0.3, 0.3, 0.3, 0.1);
            }
            case "critical" -> {
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 2, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 8, 0.3, 0.3, 0.3, 0.2);
            }
            case "stun" -> {
                world.spawnParticles(ParticleTypes.CRIT, x, y, z, 10, 0.3, 0.5, 0.3, 0.1);
                world.spawnParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 3, 0.2, 0.1, 0.2, 0.02);
            }
            case "explosion" -> {
                world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.FLAME, x, y, z, 12, 0.5, 0.5, 0.5, 0.1);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 8, 0.4, 0.4, 0.4, 0.05);
            }
            case "heal" -> world.spawnParticles(ParticleTypes.HEART, x, y + 0.5, z, 5, 0.3, 0.3, 0.3, 0.05);
            case "freeze" -> world.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 10, 0.3, 0.5, 0.3, 0.05);
            case "poison" -> world.spawnParticles(ParticleTypes.ITEM_SLIME, x, y, z, 8, 0.3, 0.3, 0.3, 0.1);
            case "lightning" -> world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 15, 0.5, 0.8, 0.5, 0.2);
            default -> world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 3, 0.2, 0.2, 0.2, 0.1);
        }
    }

    // ===== Potion Visuals =====

    /**
     * Spawn a potion throw arc from player to target — purple/witch trail with a high arc.
     */
    public static void spawnPotionThrow(ServerWorld world, BlockPos from, BlockPos to) {
        double sx = from.getX() + 0.5, sy = from.getY() + 1.5, sz = from.getZ() + 0.5;
        double ex = to.getX() + 0.5, ey = to.getY() + 1.0, ez = to.getZ() + 0.5;

        int steps = 10;
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            double x = sx + (ex - sx) * t;
            double y = sy + (ey - sy) * t + Math.sin(t * Math.PI) * 1.5; // high potion arc
            double z = sz + (ez - sz) * t;
            world.spawnParticles(ParticleTypes.WITCH, x, y, z, 2, 0.05, 0.05, 0.05, 0.01);
            world.spawnParticles(ParticleTypes.EFFECT, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    /**
     * Spawn splash impact particles at target — big burst of colored swirls.
     * @param isHealing true for pink/heart particles, false for purple/harmful
     */
    public static void spawnPotionSplash(ServerWorld world, BlockPos pos, boolean isHealing) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;

        // Glass shatter sound is already played by the caller

        // Splash burst
        world.spawnParticles(ParticleTypes.SPLASH, x, y, z, 15, 0.5, 0.3, 0.5, 0.1);

        if (isHealing) {
            // Pink/healing particles
            world.spawnParticles(ParticleTypes.HEART, x, y + 0.5, z, 6, 0.4, 0.3, 0.4, 0.05);
            world.spawnParticles(ParticleTypes.EFFECT, x, y, z, 10, 0.4, 0.3, 0.4, 0.02);
        } else {
            // Purple/harmful particles
            world.spawnParticles(ParticleTypes.WITCH, x, y, z, 15, 0.5, 0.4, 0.5, 0.08);
            world.spawnParticles(ParticleTypes.ITEM_SLIME, x, y, z, 8, 0.4, 0.2, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, 5, 0.3, 0.2, 0.3, 0.02);
        }
    }

    /**
     * Spawn lingering cloud particles on a tile — called each turn the cloud exists.
     * Creates a swirling, low-hanging poison mist effect.
     */
    public static void spawnLingeringCloud(ServerWorld world, BlockPos pos) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;

        // Swirling cloud particles
        world.spawnParticles(ParticleTypes.WITCH, x, y + 0.2, z, 6, 0.4, 0.15, 0.4, 0.01);
        world.spawnParticles(ParticleTypes.EFFECT, x, y + 0.1, z, 4, 0.3, 0.1, 0.3, 0.0);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, 2, 0.3, 0.1, 0.3, 0.005);
    }

    // ===== Spell Visual Methods =====

    /**
     * Spawn a spell trail from source to target with configurable particle types and arc height.
     */
    public static void spawnSpellTrail(ServerWorld world, BlockPos from, BlockPos to,
                                        net.minecraft.particle.ParticleEffect primary,
                                        net.minecraft.particle.ParticleEffect secondary,
                                        int count, double arcHeight) {
        double sx = from.getX() + 0.5, sy = from.getY() + 1.2, sz = from.getZ() + 0.5;
        double ex = to.getX() + 0.5, ey = to.getY() + 1.0, ez = to.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double x = sx + (ex - sx) * t;
            double y = sy + (ey - sy) * t + Math.sin(t * Math.PI) * arcHeight;
            double z = sz + (ez - sz) * t;
            world.spawnParticles(primary, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            if (secondary != null && i % 2 == 0) {
                world.spawnParticles(secondary, x, y + 0.1, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    /**
     * Spawn an expanding ring of particles outward from center — for AoE shockwave propagation.
     */
    public static void spawnExpandingRing(ServerWorld world, BlockPos center, double radius,
                                           net.minecraft.particle.ParticleEffect particle, int count) {
        double cx = center.getX() + 0.5, cy = center.getY() + 1.0, cz = center.getZ() + 0.5;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            world.spawnParticles(particle, x, cy, z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /**
     * Spawn particles converging inward toward center — for self-buff gather animations.
     */
    public static void spawnConverging(ServerWorld world, BlockPos center, double radius,
                                        net.minecraft.particle.ParticleEffect particle, int count) {
        double cx = center.getX() + 0.5, cy = center.getY() + 1.0, cz = center.getZ() + 0.5;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            // Particles at outer edge with speed toward center
            double vx = (cx - x) * 0.1;
            double vz = (cz - z) * 0.1;
            world.spawnParticles(particle, x, cy, z, 1, vx, 0.02, vz, 0.05);
        }
    }

    /**
     * Spawn spectral arrows descending from high above onto target zone — for Archer volley.
     */
    public static void spawnDescendingVolley(ServerWorld world, BlockPos target, int arrowCount, double spread) {
        double ex = target.getX() + 0.5, ey = target.getY() + 1.0, ez = target.getZ() + 0.5;
        for (int a = 0; a < arrowCount; a++) {
            double offsetX = (Math.random() - 0.5) * spread * 2;
            double offsetZ = (Math.random() - 0.5) * spread * 2;
            double startY = ey + 6.0;
            for (int i = 0; i < 6; i++) {
                double t = (double) i / 6;
                double x = (ex + offsetX) + (ex - (ex + offsetX)) * t;
                double y = startY + (ey - startY) * t;
                double z = (ez + offsetZ) + (ez - (ez + offsetZ)) * t;
                world.spawnParticles(ParticleTypes.CRIT, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    /**
     * Spawn a reversed trail from target BACK to source — for life steal / pull effects.
     */
    public static void spawnReversedTrail(ServerWorld world, BlockPos from, BlockPos to,
                                           net.minecraft.particle.ParticleEffect particle, int count) {
        // Note: "from" is the drain SOURCE (enemy), "to" is the DESTINATION (player)
        double sx = from.getX() + 0.5, sy = from.getY() + 1.2, sz = from.getZ() + 0.5;
        double ex = to.getX() + 0.5, ey = to.getY() + 1.0, ez = to.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double x = sx + (ex - sx) * t;
            double y = sy + (ey - sy) * t + Math.sin(t * Math.PI) * 0.3 + t * 0.1; // slight upward drift
            double z = sz + (ez - sz) * t;
            world.spawnParticles(particle, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /**
     * Spawn a ground-level trail (no arc) from source to target — for earth spells.
     */
    public static void spawnGroundTrail(ServerWorld world, BlockPos from, BlockPos to,
                                         net.minecraft.particle.ParticleEffect particle, int count) {
        double sx = from.getX() + 0.5, sy = from.getY() + 0.3, sz = from.getZ() + 0.5;
        double ex = to.getX() + 0.5, ey = to.getY() + 0.3, ez = to.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double x = sx + (ex - sx) * t;
            double y = sy + (ey - sy) * t;
            double z = sz + (ez - sz) * t;
            world.spawnParticles(particle, x, y, z, 1, 0.05, 0.02, 0.05, 0.0);
        }
    }
}
