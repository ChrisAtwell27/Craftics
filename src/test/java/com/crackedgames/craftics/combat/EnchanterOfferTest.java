package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchanter's "is this actually an improvement" rule.
 *
 * <p>The invariant worth defending is at the bottom: whenever an enchantment is judged worth
 * offering, the level it lands at is strictly above what the item already had. Those two halves
 * live in separate methods and are called from separate places, so it is entirely possible for
 * one to drift from the other without either looking wrong on its own.
 */
class EnchanterOfferTest {

    // -- canImprove -----------------------------------------------------------

    @Test
    @DisplayName("an enchantment the item does not have is always worth offering")
    void freshEnchantmentIsOfferable() {
        assertTrue(EnchanterOffer.canImprove(0, 5));
        assertTrue(EnchanterOffer.canImprove(0, 1));
    }

    @Test
    @DisplayName("an enchantment already at its ceiling is off the table")
    void maxedEnchantmentIsNotOfferable() {
        assertFalse(EnchanterOffer.canImprove(5, 5));
        assertFalse(EnchanterOffer.canImprove(3, 3));
    }

    @Test
    @DisplayName("a single-level enchantment the item already carries is off the table")
    void singleLevelAlreadyPresent() {
        // Mending, Hilt, Infinity. There is no Mending II to upgrade into, so re-offering it
        // would be the enchanter doing nothing at all.
        assertFalse(EnchanterOffer.canImprove(1, 1));
    }

    @Test
    @DisplayName("an enchantment with headroom left stays on the table")
    void partialEnchantmentIsOfferable() {
        assertTrue(EnchanterOffer.canImprove(1, 5));
        assertTrue(EnchanterOffer.canImprove(4, 5));
    }

    @Test
    @DisplayName("an enchantment the registry cannot resolve is not offered")
    void unresolvableEnchantmentIsNotOfferable() {
        // A zero ceiling means the lookup failed, and the apply path cannot add what it cannot
        // look up. Offering it would put an outcome on the shortlist that can never happen.
        assertFalse(EnchanterOffer.canImprove(0, 0));
        assertFalse(EnchanterOffer.canImprove(2, 0));
    }

    @Test
    @DisplayName("an item somehow above the ceiling is not pushed higher")
    void overMaxIsNotOfferable() {
        // Other sources in this mod can hand out odd levels; the enchanter should not compound it.
        assertFalse(EnchanterOffer.canImprove(7, 5));
    }

    // -- improvedLevel --------------------------------------------------------

    @Test
    @DisplayName("a fresh enchantment lands at whatever the roll wanted")
    void freshEnchantmentUsesTheRoll() {
        assertEquals(2, EnchanterOffer.improvedLevel(0, 2, 5));
        assertEquals(3, EnchanterOffer.improvedLevel(0, 3, 5));
    }

    @Test
    @DisplayName("a roll at or below what the item has is pushed above it")
    void rollIsRaisedPastWhatIsThere() {
        // Applying Sharpness II to a Sharpness III sword changes nothing but reads as a
        // downgrade, which is the specific outcome this exists to prevent.
        assertEquals(4, EnchanterOffer.improvedLevel(3, 1, 5));
        assertEquals(4, EnchanterOffer.improvedLevel(3, 3, 5));
    }

    @Test
    @DisplayName("the ceiling still holds after the push")
    void ceilingSurvivesTheRaise() {
        assertEquals(5, EnchanterOffer.improvedLevel(4, 2, 5));
        assertEquals(1, EnchanterOffer.improvedLevel(0, 3, 1));
    }

    @Test
    @DisplayName("never below level one")
    void neverBelowOne() {
        assertEquals(1, EnchanterOffer.improvedLevel(0, 0, 5));
        assertEquals(1, EnchanterOffer.improvedLevel(0, -4, 5));
    }

    @Test
    @DisplayName("anything worth offering strictly improves the item")
    void offerableAlwaysImproves() {
        // The invariant the two halves have to agree on. If canImprove ever says yes where
        // improvedLevel cannot beat what is already there, the enchanter silently does nothing.
        for (int max = 1; max <= 5; max++) {
            for (int existing = 0; existing <= 6; existing++) {
                if (!EnchanterOffer.canImprove(existing, max)) continue;
                for (int rolled = 1; rolled <= 3; rolled++) {
                    int applied = EnchanterOffer.improvedLevel(existing, rolled, max);
                    assertTrue(applied > existing,
                        "existing=" + existing + " rolled=" + rolled + " max=" + max
                            + " applied=" + applied + " did not improve the item");
                    assertTrue(applied <= max,
                        "existing=" + existing + " rolled=" + rolled + " max=" + max
                            + " applied=" + applied + " exceeded the ceiling");
                }
            }
        }
    }
}
