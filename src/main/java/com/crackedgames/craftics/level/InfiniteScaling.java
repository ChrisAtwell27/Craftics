package com.crackedgames.craftics.level;

/**
 * Flat, ordinal-driven HP curves for infinite mode. Decoupled from the campaign's
 * per-biome authored HP + bossHpMultiplier so a boss's HP depends only on how deep the
 * run is, not which random biome was rolled. Pure (int in, int out) - unit-tested.
 * See docs/superpowers/specs/2026-07-09-infinite-hp-scaling-design.md.
 */
public final class InfiniteScaling {

    private InfiniteScaling() {}

    /** Boss HP: base + ordinal*perBiome (ordinal = biomes cleared, 0-based). Floor 1. */
    public static int bossHp(int ordinal, int base, int perBiome) {
        return Math.max(1, base + Math.max(0, ordinal) * perBiome);
    }

    /** Enemy HP: base + biomeIndex*perLevel + ordinal*perBiome. Floor 1. */
    public static int enemyHp(int biomeIndex, int ordinal, int base, int perLevel, int perBiome) {
        return Math.max(1, base
            + Math.max(0, biomeIndex) * perLevel
            + Math.max(0, ordinal) * perBiome);
    }
}
