package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for {@link BiomeDifficulty}: the per-biome enemy-count ramp, the
 * post-boss "max enemies" override, and the enemy-count-based reward multiplier.
 * No Minecraft bootstrap needed (all int/double math).
 */
class BiomeDifficultyTest {

    // ---- enemyCount: ramps 3 + biomeIndex, resets per biome ----

    @Test
    void enemyCount_startsAtThreeAndRampsByOne() {
        // not boss-beaten: 3 + biomeIndex
        assertEquals(3, BiomeDifficulty.enemyCount(0, 6, false));
        assertEquals(4, BiomeDifficulty.enemyCount(1, 6, false));
        assertEquals(5, BiomeDifficulty.enemyCount(2, 6, false));
        assertEquals(6, BiomeDifficulty.enemyCount(3, 6, false));
    }

    @Test
    void enemyCount_resetsPerBiome() {
        // biomeIndex is 0-based within each biome, so the first level of ANY biome is 3.
        assertEquals(3, BiomeDifficulty.enemyCount(0, 7, false)); // biome A level 1
        assertEquals(3, BiomeDifficulty.enemyCount(0, 5, false)); // biome B level 1
    }

    // ---- maxEnemyCount: the last normal level's ramp value (levelCount-2 index) ----

    @Test
    void maxEnemyCount_isLastNormalLevelRamp() {
        // levelCount 6 => 5 normal levels (indices 0..4) + boss (index 5).
        // last normal index = 4 => 3 + 4 = 7.
        assertEquals(7, BiomeDifficulty.maxEnemyCount(6));
        // levelCount 7 => last normal index 5 => 3 + 5 = 8.
        assertEquals(8, BiomeDifficulty.maxEnemyCount(7));
        // Degenerate: levelCount 2 (1 normal + boss) => last normal index 0 => 3.
        assertEquals(3, BiomeDifficulty.maxEnemyCount(2));
        // Defensive: levelCount 1 or less never drops below 3.
        assertEquals(3, BiomeDifficulty.maxEnemyCount(1));
    }

    // ---- post-boss override: once the boss is beaten, every level is maxed ----

    @Test
    void enemyCount_bossBeatenForcesMax() {
        // levelCount 6 => max 7. Even level 1 (index 0) yields 7 once beaten.
        assertEquals(7, BiomeDifficulty.enemyCount(0, 6, true));
        assertEquals(7, BiomeDifficulty.enemyCount(2, 6, true));
        // And the last normal level is already 7, so beaten vs not matches there.
        assertEquals(7, BiomeDifficulty.enemyCount(4, 6, false));
        assertEquals(7, BiomeDifficulty.enemyCount(4, 6, true));
    }

    // ---- reward multiplier scales with enemy count vs the baseline of 3 ----

    @Test
    void rewardMultiplier_isCountOverBaseline() {
        // baseline is 3 enemies -> 1.0x
        assertEquals(1.0, BiomeDifficulty.rewardMultiplier(3), 1e-9);
        // more enemies -> proportionally more
        assertEquals(7.0 / 3.0, BiomeDifficulty.rewardMultiplier(7), 1e-9);
        // a level with fewer than baseline still pays something, never below a floor
        assertTrue(BiomeDifficulty.rewardMultiplier(1) > 0.0);
        assertTrue(BiomeDifficulty.rewardMultiplier(0) > 0.0);
    }

    @Test
    void rewardMultiplier_monotonicInCount() {
        assertTrue(BiomeDifficulty.rewardMultiplier(7) > BiomeDifficulty.rewardMultiplier(3));
        assertTrue(BiomeDifficulty.rewardMultiplier(3) >= BiomeDifficulty.rewardMultiplier(2));
    }
}
