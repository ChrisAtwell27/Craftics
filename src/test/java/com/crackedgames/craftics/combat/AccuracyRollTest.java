package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link AccuracyRoll} to-hit formula and d100 roll.
 * Accuracy is a multiplier where 1.0 always lands; the resulting chance is
 * {@code clamp(round(accuracy * 100), 5, 100)}.
 */
class AccuracyRollTest {

    @Test
    void hitPercent_defaultAccuracyAlwaysLands() {
        assertEquals(AccuracyRoll.CAP, AccuracyRoll.hitPercent(AccuracyRoll.DEFAULT));
    }

    @Test
    void hitPercent_scalesLinearly() {
        assertEquals(95, AccuracyRoll.hitPercent(0.95));
        assertEquals(50, AccuracyRoll.hitPercent(0.5));
        assertEquals(75, AccuracyRoll.hitPercent(0.75));
    }

    @Test
    void hitPercent_clampsAccumulatedPenaltiesToFloor() {
        // Penalties stack by multiplication, so they approach zero without reaching it.
        assertEquals(AccuracyRoll.FLOOR, AccuracyRoll.hitPercent(0.01));
        assertEquals(AccuracyRoll.FLOOR, AccuracyRoll.hitPercent(0.04));
    }

    @Test
    void hitPercent_zeroMeansNeverAndIsNotFloored() {
        // The floor stops debuffs accumulating into silence; it does not overrule something
        // that explicitly asked for never, such as a config knob set to zero.
        assertEquals(0, AccuracyRoll.hitPercent(0.0));
        assertEquals(0, AccuracyRoll.hitPercent(-2.0));
    }

    @Test
    void roll_zeroAccuracyMissesAndDrawsNoRandomness() {
        Random exploding = new Random() {
            @Override public int nextInt(int bound) {
                throw new AssertionError("a certain miss must not draw from the RNG");
            }
        };
        AccuracyRoll.HitResult r = AccuracyRoll.roll(0.0, exploding);
        assertFalse(r.hit());
        assertEquals(0, r.hitPercent());
        assertEquals(0, r.rolled());
    }

    @Test
    void hitPercent_clampsToCap() {
        // An addon handing out a +50% accuracy buff gets a reliable attacker, not an error.
        assertEquals(AccuracyRoll.CAP, AccuracyRoll.hitPercent(1.5));
        assertEquals(AccuracyRoll.CAP, AccuracyRoll.hitPercent(Double.POSITIVE_INFINITY));
        assertEquals(AccuracyRoll.CAP, AccuracyRoll.hitPercent(Double.NaN));
    }

    @Test
    void roll_missesWhenRollExceedsChance() {
        // hitPercent(0.5) = 50. A roll of 99 is well past it.
        Random high = new Random() {
            @Override public int nextInt(int bound) { return 98; } // -> rolled 99
        };
        AccuracyRoll.HitResult r = AccuracyRoll.roll(0.5, high);
        assertFalse(r.hit());
        assertEquals(50, r.hitPercent());
        assertEquals(99, r.rolled());
    }

    @Test
    void roll_hitsWhenRollWithinChance() {
        Random low = new Random() {
            @Override public int nextInt(int bound) { return 0; } // -> rolled 1
        };
        AccuracyRoll.HitResult r = AccuracyRoll.roll(0.5, low);
        assertTrue(r.hit());
        assertEquals(1, r.rolled());
    }

    @Test
    void roll_boundaryRollEqualsChanceIsAHit() {
        // rolled == hitPercent -> hit (roll <= pct), matching DodgeRoll's boundary.
        Random exact = new Random() {
            @Override public int nextInt(int bound) { return 49; } // -> rolled 50
        };
        AccuracyRoll.HitResult r = AccuracyRoll.roll(0.5, exact); // pct 50
        assertTrue(r.hit());
    }

    @Test
    void roll_blindedHalvesTheChance() {
        Random exact = new Random() {
            @Override public int nextInt(int bound) { return 50; } // -> rolled 51
        };
        AccuracyRoll.HitResult r = AccuracyRoll.roll(AccuracyRoll.BLINDED_MULTIPLIER, exact);
        assertEquals(50, r.hitPercent());
        assertFalse(r.hit());
    }

    @Test
    void roll_certainHitDrawsNoRandomness() {
        // The property that makes this safe to drop into every damage path: at full accuracy
        // the roll must not consume the RNG, or every dodge and crit downstream of it in the
        // same fight would land differently than it did before accuracy existed.
        Random exploding = new Random() {
            @Override public int nextInt(int bound) {
                throw new AssertionError("a certain hit must not draw from the RNG");
            }
        };
        AccuracyRoll.HitResult r = AccuracyRoll.roll(AccuracyRoll.DEFAULT, exploding);
        assertTrue(r.hit());
        assertEquals(AccuracyRoll.CAP, r.hitPercent());
        assertEquals(0, r.rolled());
    }

    @Test
    void roll_aboveOneIsAlsoCertain() {
        Random exploding = new Random() {
            @Override public int nextInt(int bound) {
                throw new AssertionError("a certain hit must not draw from the RNG");
            }
        };
        assertTrue(AccuracyRoll.roll(2.0, exploding).hit());
    }
}
