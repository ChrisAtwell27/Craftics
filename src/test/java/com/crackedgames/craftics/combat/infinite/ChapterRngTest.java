package com.crackedgames.craftics.combat.infinite;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ChapterRngTest {

    @Test
    void sameInputsGiveSameSeed() {
        long a = ChapterRng.derive(12345L, ChapterRng.SALT_LEVEL, 7, 3);
        long b = ChapterRng.derive(12345L, ChapterRng.SALT_LEVEL, 7, 3);
        assertEquals(a, b);
    }

    @Test
    void differentChapterSeedsGiveDifferentSeeds() {
        long a = ChapterRng.derive(12345L, ChapterRng.SALT_LEVEL, 7, 3);
        long b = ChapterRng.derive(12346L, ChapterRng.SALT_LEVEL, 7, 3);
        assertNotEquals(a, b);
    }

    @Test
    void differentSaltsGiveDifferentSeeds() {
        long level = ChapterRng.derive(12345L, ChapterRng.SALT_LEVEL, 7, 3);
        long arena = ChapterRng.derive(12345L, ChapterRng.SALT_ARENA, 7, 3);
        long loot = ChapterRng.derive(12345L, ChapterRng.SALT_LOOT, 7, 3);
        assertNotEquals(level, arena);
        assertNotEquals(level, loot);
        assertNotEquals(arena, loot);
    }

    @Test
    void adjacentLevelsDoNotCorrelate() {
        // The old seed was a raw XOR of small ints, so neighbouring levels shared
        // most of their bits and produced visibly similar arenas. Require at least
        // a quarter of the 64 bits to differ between every adjacent pair.
        for (int depth = 0; depth < 20; depth++) {
            for (int index = 0; index < 5; index++) {
                long here = ChapterRng.derive(999L, ChapterRng.SALT_LEVEL, depth, index);
                long next = ChapterRng.derive(999L, ChapterRng.SALT_LEVEL, depth, index + 1);
                assertTrue(Long.bitCount(here ^ next) >= 16,
                    "depth " + depth + " index " + index + " correlates with its neighbour");
            }
        }
    }

    @Test
    void inputOrderMatters() {
        assertNotEquals(
            ChapterRng.derive(1L, ChapterRng.SALT_LEVEL, 2, 3),
            ChapterRng.derive(1L, ChapterRng.SALT_LEVEL, 3, 2));
    }

    @Test
    void randomIsReproducible() {
        Random a = ChapterRng.random(42L, ChapterRng.SALT_BOSS, 5);
        Random b = ChapterRng.random(42L, ChapterRng.SALT_BOSS, 5);
        for (int i = 0; i < 50; i++) {
            assertEquals(a.nextInt(1000), b.nextInt(1000));
        }
    }

    @Test
    void zeroSeedStillMixes() {
        // A fresh save could hold seed 0 before the first roll. It must not
        // collapse every level to the same value.
        assertNotEquals(
            ChapterRng.derive(0L, ChapterRng.SALT_LEVEL, 0, 0),
            ChapterRng.derive(0L, ChapterRng.SALT_LEVEL, 0, 1));
    }
}
