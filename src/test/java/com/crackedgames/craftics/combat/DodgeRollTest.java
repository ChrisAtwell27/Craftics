package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link DodgeRoll} dodge-chance formula and d100 roll.
 * Formula: {@code dodge% = clamp(3*(AC-atk) - 8, 5, 40)}.
 */
class DodgeRollTest {

    // ---- dodgePercent: anchor points from spec § 2.3 ----

    @Test
    void dodgePercent_leatherAnchor() {
        // Full leather (AC 7) vs a starting enemy (eff atk 2): diff 5 → 7%
        assertEquals(7, DodgeRoll.dodgePercent(7, 2));
    }

    @Test
    void dodgePercent_ironAnchor() {
        // Full iron (AC 13) vs an iron-tier enemy (eff atk 4): diff 9 → 19%
        assertEquals(19, DodgeRoll.dodgePercent(13, 4));
    }

    @Test
    void dodgePercent_referenceCurve() {
        // diff → dodge% across the reference table
        assertEquals(10, DodgeRoll.dodgePercent(8, 2));   // diff 6
        assertEquals(13, DodgeRoll.dodgePercent(10, 3));  // diff 7
        assertEquals(16, DodgeRoll.dodgePercent(11, 3));  // diff 8
        assertEquals(31, DodgeRoll.dodgePercent(19, 6));  // diff 13
        assertEquals(37, DodgeRoll.dodgePercent(23, 8));  // diff 15
    }

    // ---- Floor and cap ----

    @Test
    void dodgePercent_floorsAtFive() {
        assertEquals(5, DodgeRoll.dodgePercent(7, 4));   // diff 3 → 1 → floored
        assertEquals(5, DodgeRoll.dodgePercent(0, 10));  // naked, far outmatched
        assertEquals(5, DodgeRoll.dodgePercent(5, 5));   // diff 0
    }

    @Test
    void dodgePercent_capsAtForty() {
        assertEquals(40, DodgeRoll.dodgePercent(23, 7));  // diff 16 → 40 exactly
        assertEquals(40, DodgeRoll.dodgePercent(40, 2));  // far overmatched
    }

    // ---- roll: d100 behavior ----

    @Test
    void roll_dodgesWhenRollWithinChance() {
        // dodgePercent(13,4) = 19. A seeded RNG whose first nextInt(100) is < 19 dodges.
        Random low = new Random() {
            @Override public int nextInt(int bound) { return 0; } // → rolled 1
        };
        DodgeRoll.DodgeResult r = DodgeRoll.roll(13, 4, low);
        assertTrue(r.dodged());
        assertEquals(19, r.dodgePercent());
        assertEquals(1, r.rolled());
    }

    @Test
    void roll_hitsWhenRollExceedsChance() {
        Random high = new Random() {
            @Override public int nextInt(int bound) { return 98; } // → rolled 99
        };
        DodgeRoll.DodgeResult r = DodgeRoll.roll(13, 4, high);
        assertFalse(r.dodged());
        assertEquals(99, r.rolled());
    }

    @Test
    void roll_boundaryRollEqualsChanceIsADodge() {
        // rolled == dodgePercent → dodge (roll <= pct)
        Random exact = new Random() {
            @Override public int nextInt(int bound) { return 18; } // → rolled 19
        };
        DodgeRoll.DodgeResult r = DodgeRoll.roll(13, 4, exact); // pct 19
        assertTrue(r.dodged());
    }
}
