package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A biome run must never be able to walk PAST its own boss.
 *
 * <p>Players reported skipping levels after a between-level event: one went Deep Dark level 3
 * -> level 6 and landed in "Nether Wastes I", another skipped straight to Stony Peaks "right
 * before the boss level". The arithmetic is exact: Deep Dark starts at global level 41 with 5
 * levels, so its boss is index 4 (global 45) and Nether Wastes starts at 46. An index of 6
 * yields global 41+6 = 47 - inside Nether Wastes, with the boss jumped clean over.
 *
 * <p>The level counter could be advanced more than once for a single victory, and nothing
 * bounded the result: the boss test was exact equality, so an index that overshot the boss
 * reported "not a boss" and kept indexing off the OLD biome's startLevel into the next
 * biome's global range - silently skipping the boss fight, the biome unlock, and the NG+
 * check. These tests pin the bound.
 */
class BiomeLevelBoundsTest {

    /** Deep Dark as registered: startLevel 41, 5 levels -> boss at index 4 (global 45). */
    private static final int DD_START = 41;
    private static final int DD_LEVELS = 5;

    private static boolean isBoss(int globalLevel) {
        return BiomeLevelMath.isBossLevel(globalLevel, DD_START, DD_LEVELS);
    }

    private static int index(int globalLevel) {
        return BiomeLevelMath.biomeLevelIndex(globalLevel, DD_START, DD_LEVELS);
    }

    @Test
    void bossIsTheLastLevelOfTheBiome() {
        assertFalse(isBoss(44), "index 3 is a normal level");
        assertTrue(isBoss(45), "index 4 is the boss");
    }

    /**
     * The reported bug, as an assertion. Before the fix an index past the boss read as a
     * normal level and the run sailed into the next biome.
     */
    @Test
    void overshootingTheBossStillCountsAsTheBossLevel() {
        assertTrue(isBoss(46), "index 5 overshot the boss - must still gate on the boss");
        assertTrue(isBoss(47), "index 6 (the reported Deep Dark 3->6 skip) must not slip past");
        assertTrue(isBoss(99), "however far it overshoots, the boss is still the gate");
    }

    /** An overshoot resolves to this biome's LAST level, never a phantom index. */
    @Test
    void overshootClampsToTheLastLevelOfThisBiome() {
        assertEquals(4, index(45), "the boss itself");
        assertEquals(4, index(47), "the reported skip clamps back onto the boss");
        assertEquals(4, index(99), "no matter how far past");
    }

    /** Levels before the boss are unaffected - the gate must not fire early. */
    @Test
    void levelsBeforeTheBossAreUntouched() {
        for (int global = DD_START; global <= 44; global++) {
            assertFalse(isBoss(global), "global " + global + " is a normal level");
            assertEquals(global - DD_START, index(global), "normal levels index straight through");
        }
    }

    /** A level below the biome's start can't produce a negative index. */
    @Test
    void undershootClampsToZero() {
        assertEquals(0, index(40));
        assertEquals(0, index(0));
    }

    /** A single-level biome is all boss, and must not divide by or index below zero. */
    @Test
    void singleLevelBiomeIsAllBoss() {
        assertTrue(BiomeLevelMath.isBossLevel(10, 10, 1));
        assertEquals(0, BiomeLevelMath.biomeLevelIndex(10, 10, 1));
    }
}
