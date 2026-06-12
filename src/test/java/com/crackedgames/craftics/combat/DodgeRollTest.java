package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link DodgeRoll} dodge-chance formula and d100 roll.
 * Formula: {@code dodge% = clamp(diff*diff/3 + diff, 3, 60)} where
 * {@code diff = AC - enemyEffectiveAttack}.
 */
class DodgeRollTest {

    @Test
    void dodgePercent_ac8_vs_atk5_lowDiff() {
        // AC 8 vs 5-ATK enemy: diff 3 -> 9/3 + 3 = 6
        assertEquals(6, DodgeRoll.dodgePercent(8, 5));
    }

    @Test
    void dodgePercent_ac8_vs_atk3_midDiff() {
        // AC 8 vs 3-ATK zombie: diff 5 -> 25/3 + 5 = 8 + 5 = 13
        assertEquals(13, DodgeRoll.dodgePercent(8, 3));
    }

    @Test
    void dodgePercent_ac10_vs_atk3() {
        // AC 10 vs 3-ATK enemy: diff 7 -> 49/3 + 7 = 16 + 7 = 23
        assertEquals(23, DodgeRoll.dodgePercent(10, 3));
    }

    @Test
    void dodgePercent_ironTier() {
        // Full iron (AC 13) vs iron-tier enemy (eff atk 4): diff 9 -> 81/3 + 9 = 27 + 9 = 36
        assertEquals(36, DodgeRoll.dodgePercent(13, 4));
    }

    @Test
    void dodgePercent_referenceCurve() {
        assertEquals(9, DodgeRoll.dodgePercent(8, 4));    // diff 4 -> 16/3+4 = 5+4 = 9
        assertEquals(18, DodgeRoll.dodgePercent(10, 4));  // diff 6 -> 36/3+6 = 12+6 = 18
        assertEquals(29, DodgeRoll.dodgePercent(11, 3));  // diff 8 -> 64/3+8 = 21+8 = 29
        assertEquals(43, DodgeRoll.dodgePercent(15, 5));  // diff 10 -> 100/3+10 = 33+10 = 43
    }

    @Test
    void dodgePercent_floorsAtThree() {
        assertEquals(3, DodgeRoll.dodgePercent(0, 10));  // negative diff -> floored
        assertEquals(3, DodgeRoll.dodgePercent(5, 5));   // diff 0 -> 0 -> floored
        assertEquals(3, DodgeRoll.dodgePercent(6, 5));   // diff 1 -> 1 -> floored
        assertEquals(3, DodgeRoll.dodgePercent(7, 5));   // diff 2 -> 4/3+2 = 3 (boundary)
    }

    @Test
    void dodgePercent_capsAtSixty() {
        // diff 12 -> 144/3 + 12 = 48 + 12 = 60 (just hits cap)
        assertEquals(60, DodgeRoll.dodgePercent(17, 5));
        // diff 15 -> 225/3 + 15 = 75 + 15 = 90 -> capped at 60
        assertEquals(60, DodgeRoll.dodgePercent(20, 5));
        assertEquals(60, DodgeRoll.dodgePercent(40, 2));  // far overmatched
    }

    @Test
    void roll_dodgesWhenRollWithinChance() {
        // dodgePercent(13,4) = 36. A seeded RNG whose first nextInt(100) is < 36 dodges.
        Random low = new Random() {
            @Override public int nextInt(int bound) { return 0; } // -> rolled 1
        };
        DodgeRoll.DodgeResult r = DodgeRoll.roll(13, 4, low);
        assertTrue(r.dodged());
        assertEquals(36, r.dodgePercent());
        assertEquals(1, r.rolled());
    }

    @Test
    void roll_hitsWhenRollExceedsChance() {
        Random high = new Random() {
            @Override public int nextInt(int bound) { return 98; } // -> rolled 99
        };
        DodgeRoll.DodgeResult r = DodgeRoll.roll(13, 4, high);
        assertFalse(r.dodged());
        assertEquals(99, r.rolled());
    }

    @Test
    void roll_boundaryRollEqualsChanceIsADodge() {
        // rolled == dodgePercent -> dodge (roll <= pct)
        Random exact = new Random() {
            @Override public int nextInt(int bound) { return 35; } // -> rolled 36
        };
        DodgeRoll.DodgeResult r = DodgeRoll.roll(13, 4, exact); // pct 36
        assertTrue(r.dodged());
    }
}
