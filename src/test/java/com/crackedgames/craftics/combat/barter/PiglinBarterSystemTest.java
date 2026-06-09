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
}
