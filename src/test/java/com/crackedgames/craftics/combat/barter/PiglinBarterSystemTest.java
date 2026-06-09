package com.crackedgames.craftics.combat.barter;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PiglinBarterSystemTest {

    @Test
    void successChance_isLinearRatioClampedToZeroOne() {
        // threshold T = 32
        assertEquals(0.0,  PiglinBarterSystem.successChance(0, 32),  1e-9);
        assertEquals(0.25, PiglinBarterSystem.successChance(8, 32),  1e-9);
        assertEquals(0.50, PiglinBarterSystem.successChance(16, 32), 1e-9);
        assertEquals(0.75, PiglinBarterSystem.successChance(24, 32), 1e-9);
        assertEquals(1.0,  PiglinBarterSystem.successChance(32, 32), 1e-9);
    }

    @Test
    void successChance_offerAboveThresholdClampsToOne() {
        assertEquals(1.0, PiglinBarterSystem.successChance(40, 32), 1e-9);
        assertEquals(1.0, PiglinBarterSystem.successChance(64, 16), 1e-9);
    }

    @Test
    void successChance_guardsZeroOrNegativeThreshold() {
        // Defensive: a non-positive threshold should never divide-by-zero; treat as guaranteed.
        assertEquals(1.0, PiglinBarterSystem.successChance(5, 0), 1e-9);
    }

    @Test
    void rollThreshold_staysWithinSixteenToSixtyFour() {
        Random r = new Random(12345L);
        for (int i = 0; i < 10_000; i++) {
            int t = PiglinBarterSystem.rollThreshold(r);
            assertTrue(t >= 16 && t <= 64, "threshold out of range: " + t);
        }
    }

    @Test
    void overpayBonusChance_zeroAtNoSurplus_monotonic_capped() {
        assertEquals(0.0, PiglinBarterSystem.overpayBonusChance(0), 1e-9);
        double a = PiglinBarterSystem.overpayBonusChance(4);
        double b = PiglinBarterSystem.overpayBonusChance(16);
        assertTrue(a > 0.0, "surplus 4 should give some chance");
        assertTrue(b >= a, "bonus chance should be monotonic in surplus");
        assertTrue(b <= 1.0, "bonus chance must be capped at 1.0");
        // Cap holds for huge surplus.
        assertTrue(PiglinBarterSystem.overpayBonusChance(10_000) <= 1.0);
    }

    @Test
    void pickWeightedIndex_selectsBucketByCumulativeWeight() {
        int[] weights = {2, 3, 5}; // cumulative: [0,2) -> 0, [2,5) -> 1, [5,10) -> 2
        assertEquals(0, PiglinBarterSystem.pickWeightedIndex(weights, 0));
        assertEquals(0, PiglinBarterSystem.pickWeightedIndex(weights, 1));
        assertEquals(1, PiglinBarterSystem.pickWeightedIndex(weights, 2));
        assertEquals(1, PiglinBarterSystem.pickWeightedIndex(weights, 4));
        assertEquals(2, PiglinBarterSystem.pickWeightedIndex(weights, 5));
        assertEquals(2, PiglinBarterSystem.pickWeightedIndex(weights, 9));
    }

    @Test
    void pickWeightedIndex_clampsOutOfRangeRollToLastBucket() {
        int[] weights = {1, 1};
        assertEquals(1, PiglinBarterSystem.pickWeightedIndex(weights, 2));
        assertEquals(1, PiglinBarterSystem.pickWeightedIndex(weights, 100));
    }

    @Test
    void rollCount_staysWithinInclusiveRange() {
        Random r = new Random(7L);
        for (int i = 0; i < 1000; i++) {
            int c = PiglinBarterSystem.rollCount(6, 12, r);
            assertTrue(c >= 6 && c <= 12, "count in [6,12]: " + c);
        }
        assertEquals(5, PiglinBarterSystem.rollCount(5, 5, new Random(1L)));
        assertTrue(PiglinBarterSystem.rollCount(0, 0, new Random(1L)) >= 1);
    }

    @Test
    void junkItemCount_isStableAndNonEmpty() {
        assertTrue(PiglinBarterSystem.JUNK_ITEM_COUNT >= 5, "junk pool should have several options");
    }
}
