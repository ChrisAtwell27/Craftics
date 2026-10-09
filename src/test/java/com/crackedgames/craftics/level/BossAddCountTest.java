package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How much backup a boss starts its fight with.
 *
 * <p>Most bosses bring up to three themed adds. The Shadow brings its own company - one Shadow
 * per player, a shadow of every pet - so its level starts with a single shulker.
 */
class BossAddCountTest {

    @Test
    void mostBossesBringUpToThree() {
        assertEquals(3, LevelGenerator.bossAddCount("desert", 3));
        assertEquals(3, LevelGenerator.bossAddCount("nether_wastes", 8), "never more than three");
    }

    @Test
    void theConfigCanLowerIt() {
        assertEquals(2, LevelGenerator.bossAddCount("desert", 2));
        assertEquals(0, LevelGenerator.bossAddCount("desert", 0));
    }

    @Test
    void theShadowStartsWithOneShulker() {
        assertEquals(1, LevelGenerator.bossAddCount("end_city", 3));
        assertEquals(1, LevelGenerator.bossAddCount("end_city", 8));
    }

    @Test
    void theConfigCanStillTakeTheShadowsShulkerAway() {
        assertEquals(0, LevelGenerator.bossAddCount("end_city", 0));
    }

    @Test
    void aBiomeNobodyListedGetsTheUsualCrew() {
        assertEquals(3, LevelGenerator.bossAddCount("mymod:cavern", 3));
        assertEquals(3, LevelGenerator.bossAddCount(null, 3));
    }

    @Test
    void aNonsenseConfigNeverMeansNegativeAdds() {
        assertEquals(0, LevelGenerator.bossAddCount("desert", -4));
        assertEquals(0, LevelGenerator.bossAddCount("end_city", -4));
    }
}
