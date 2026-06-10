package com.crackedgames.craftics.level;

/**
 * Pure difficulty math for a combat level: how many enemies it spawns and how much its
 * completion reward scales. No Minecraft dependencies, so it is unit-testable.
 *
 * <p>Enemy count ramps <em>within</em> a biome and resets at each new biome: the first level of
 * every biome has {@link #BASE_ENEMIES} enemies and each subsequent level adds one. Global
 * campaign position ({@code biomeOrdinal}) no longer drives the count; later biomes stay harder
 * through enemy stat/HP scaling, which lives elsewhere.
 *
 * <p>Once the biome's boss has been beaten, every level in that biome spawns the biome's peak
 * count (the value its last normal level would have had), so replays are full-strength.
 */
public final class BiomeDifficulty {

    /** Enemies on the first level of any biome (biomeIndex 0). */
    public static final int BASE_ENEMIES = 3;

    /** Enemy count the reward multiplier treats as the baseline (= a first level). */
    public static final int REWARD_BASELINE = BASE_ENEMIES;

    private BiomeDifficulty() {}

    /**
     * Enemy count for a level, before arena-size / config clamping (the caller applies those).
     *
     * @param biomeIndex  0-based level index within the biome (resets to 0 each biome)
     * @param levelCount  total levels in the biome including the boss level
     * @param bossBeaten  whether the biome's boss has already been defeated (forces max count)
     */
    public static int enemyCount(int biomeIndex, int levelCount, boolean bossBeaten) {
        if (bossBeaten) return maxEnemyCount(levelCount);
        return BASE_ENEMIES + Math.max(0, biomeIndex);
    }

    /**
     * The biome's peak normal enemy count: the ramp value of the last non-boss level. With
     * {@code levelCount} levels the boss is index {@code levelCount-1}, so the last normal level is
     * index {@code levelCount-2}. Never drops below {@link #BASE_ENEMIES}.
     */
    public static int maxEnemyCount(int levelCount) {
        int lastNormalIndex = Math.max(0, levelCount - 2);
        return BASE_ENEMIES + lastNormalIndex;
    }

    /**
     * Reward multiplier for a level, proportional to how many enemies it has relative to the
     * baseline of {@link #REWARD_BASELINE}. A baseline level pays 1.0x; a fuller level pays more.
     * Floored so a level with very few enemies still pays a little, never zero.
     */
    public static double rewardMultiplier(int enemyCount) {
        double m = (double) Math.max(0, enemyCount) / (double) REWARD_BASELINE;
        return Math.max(0.34, m); // ~1/3 floor: even a 1-enemy level pays something
    }
}
