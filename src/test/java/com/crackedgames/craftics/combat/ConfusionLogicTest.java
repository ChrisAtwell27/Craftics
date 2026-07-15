package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfusionLogicTest {
    @Test
    void rollBelowChanceHits() {
        assertTrue(ConfusionLogic.rollHits(0.10, 0.35));
        assertTrue(ConfusionLogic.rollHits(0.0, 0.50));
    }
    @Test
    void rollAtOrAboveChanceMisses() {
        assertFalse(ConfusionLogic.rollHits(0.35, 0.35)); // boundary: not <
        assertFalse(ConfusionLogic.rollHits(0.90, 0.35));
    }
    @Test
    void zeroChanceNeverHits() {
        assertFalse(ConfusionLogic.rollHits(0.0, 0.0));
    }
    @Test
    void fullChanceAlwaysHits() {
        assertTrue(ConfusionLogic.rollHits(0.99, 1.0));
    }
}
