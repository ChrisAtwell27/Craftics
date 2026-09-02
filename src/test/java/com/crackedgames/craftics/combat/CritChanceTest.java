package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crit chance adds up the way it is written down.
 *
 * <p>The whole point of consolidating these was that the old sequential rolls did not: three 30%
 * sources read as 90% and behaved as 65.7%, and no amount of stacking could reach certainty. The
 * tests here are mostly about the property players actually rely on - that a source worth 15% is
 * worth 15% regardless of what else is already on the build.
 */
class CritChanceTest {

    private static CritChance.Sources of(int luckPoints, int effectLevel, int trimLuck,
                                         boolean gold, boolean eagle,
                                         double config, double hybrid, double light) {
        return new CritChance.Sources(luckPoints, effectLevel, trimLuck, gold, eagle,
            config, hybrid, light);
    }

    private static CritChance.Sources luck(int points) {
        return of(points, 0, 0, false, false, 0, 0, 0);
    }

    @Test
    @DisplayName("each source contributes its face value")
    void theSourcesAreWorthWhatTheySay() {
        assertEquals(0.08, CritChance.total(luck(1)), 1e-9);
        assertEquals(0.40, CritChance.total(luck(5)), 1e-9);
        assertEquals(0.15, CritChance.total(of(0, 0, 0, true, false, 0, 0, 0)), 1e-9);
        assertEquals(0.30, CritChance.total(of(0, 0, 0, false, true, 0, 0, 0)), 1e-9);
        assertEquals(0.24, CritChance.total(of(0, 0, 8, false, false, 0, 0, 0)), 1e-9);
        assertEquals(0.10, CritChance.total(of(0, 2, 0, false, false, 0, 0, 0)), 1e-9);
    }

    @Test
    @DisplayName("sources add rather than combining probabilistically")
    void stackingIsAdditive() {
        // Gold set (15) + 8 trim luck (24) = 39, not the 35.4 the old sequential rolls produced.
        assertEquals(0.39, CritChance.total(of(0, 0, 8, true, false, 0, 0, 0)), 1e-9);

        // A source is worth the same whether it is alone or on top of a big stack. This is the
        // property the old code broke: there, a late 15% source was worth less the more crit you
        // already had, because it only rolled on the hits everything else had already missed.
        double alone = CritChance.total(of(0, 0, 0, true, false, 0, 0, 0));
        double withGold = CritChance.total(of(5, 0, 0, true, false, 0, 0, 0));
        double withoutGold = CritChance.total(of(5, 0, 0, false, false, 0, 0, 0));
        assertEquals(alone, withGold - withoutGold, 1e-9);
    }

    @Test
    @DisplayName("nothing is capped - stacking keeps counting past certainty")
    void stackingIsUncapped() {
        // 13 points is 104%. The total has to KEEP the 4, because that is what becomes Overcrit.
        assertEquals(1.04, CritChance.total(luck(13)), 1e-9);
        assertEquals(4.00, CritChance.total(luck(50)), 1e-9);
        assertTrue(CritChance.isGuaranteed(luck(13)));
        assertFalse(CritChance.isGuaranteed(luck(12)));
    }

    @Test
    @DisplayName("every whole 100% is a guaranteed tier")
    void wholeHundredsAreGuaranteed() {
        assertEquals(0, CritChance.guaranteedTiers(luck(5)), "40% guarantees nothing");
        assertEquals(1, CritChance.guaranteedTiers(luck(13)), "104% guarantees the crit");
        assertEquals(2, CritChance.guaranteedTiers(of(0, 0, 0, false, false, 0, 2.0, 0)),
            "200% guarantees a crit and an Overcrit");
        assertEquals(3, CritChance.guaranteedTiers(of(0, 0, 0, false, false, 0, 3.0, 0)),
            "300% guarantees a Double Overcrit");
    }

    @Test
    @DisplayName("the leftover percentage is the chance at one more tier")
    void theRemainderIsTheRoll() {
        assertEquals(0.40, CritChance.chanceOfNextTier(luck(5)), 1e-9);
        assertEquals(0.04, CritChance.chanceOfNextTier(luck(13)), 1e-9);
        // A whole multiple has nothing left over - it is certain and not a coin flip.
        assertEquals(0.0, CritChance.chanceOfNextTier(of(0, 0, 0, false, false, 0, 2.0, 0)), 1e-9);
    }

    @Test
    @DisplayName("a swing lands the guaranteed tiers plus the roll")
    void rollingStacksOnTheGuarantee() {
        CritChance.Sources at248 = of(0, 0, 0, false, false, 0, 2.48, 0);
        // Two tiers for certain either way; the third depends on the roll landing under 48%.
        assertEquals(3, CritChance.rollTiers(at248, 0.47));
        assertEquals(2, CritChance.rollTiers(at248, 0.48));
        assertEquals(2, CritChance.rollTiers(at248, 0.99));
    }

    @Test
    @DisplayName("below 100% nothing changed")
    void ordinaryBuildsBehaveExactlyAsBefore() {
        // The tier model has to collapse to "one roll for one crit" for a normal build, or it
        // would have quietly rewritten crit for every player who is not stacking Luck.
        CritChance.Sources at44 = of(0, 0, 8, true, false, 0.05, 0, 0);
        assertEquals(0.44, CritChance.total(at44), 1e-9);
        assertEquals(1, CritChance.rollTiers(at44, 0.43), "a hit under the chance crits once");
        assertEquals(0, CritChance.rollTiers(at44, 0.44), "a hit over it does not crit");
        assertEquals(0, CritChance.overcritTiers(CritChance.rollTiers(at44, 0.0)),
            "an ordinary crit is never an Overcrit");
    }

    @Test
    @DisplayName("the first tier is the crit, the rest are Overcrits")
    void theFirstTierIsNotAnOvercrit() {
        assertEquals(0, CritChance.overcritTiers(0));
        assertEquals(0, CritChance.overcritTiers(1));
        assertEquals(1, CritChance.overcritTiers(2));
        assertEquals(2, CritChance.overcritTiers(3));
    }

    @Test
    @DisplayName("each Overcrit multiplies again, like another crit")
    void overcritsCompound() {
        double normal = 1.5;
        assertEquals(1.5, CritChance.critMultiplier(normal, 0), 1e-9);
        assertEquals(2.25, CritChance.critMultiplier(normal, 1), 1e-9);
        assertEquals(3.375, CritChance.critMultiplier(normal, 2), 1e-9);
    }

    @Test
    @DisplayName("Gladiator stays a proportional bonus at every tier")
    void gladiatorDoesNotRunAway() {
        // The base multiplier is applied once and Overcrits compound on top, rather than the base
        // being raised to a power. Otherwise Gladiator's flat +50% crit damage would become an
        // exponential advantage at deep tiers: 2^3 = 8x against 1.5^3 = 3.375x.
        for (int tiers = 0; tiers <= 4; tiers++) {
            double ratio = CritChance.critMultiplier(2.0, tiers)
                / CritChance.critMultiplier(1.5, tiers);
            assertEquals(2.0 / 1.5, ratio, 1e-9,
                "Gladiator's advantage changed at " + tiers + " Overcrits");
        }
    }

    @Test
    @DisplayName("Overcrits are never negative and never weaken a hit")
    void overcritIsNeverAPenalty() {
        assertEquals(1.5, CritChance.critMultiplier(1.5, -3), 1e-9);
        assertEquals(0, CritChance.overcritTiers(-5));
    }

    @Test
    @DisplayName("investment past certainty keeps buying something")
    void deepInvestmentIsNotWasted() {
        // The whole point of not capping: another 100% has to be worth more than nothing.
        double at1 = CritChance.critMultiplier(1.5, CritChance.overcritTiers(
            CritChance.rollTiers(of(0, 0, 0, false, false, 0, 1.0, 0), 0.99)));
        double at3 = CritChance.critMultiplier(1.5, CritChance.overcritTiers(
            CritChance.rollTiers(of(0, 0, 0, false, false, 0, 3.0, 0), 0.99)));
        assertTrue(at3 > at1, "stacking past guaranteed must still do something");
    }

    @Test
    @DisplayName("Overcrits are named for the player")
    void theTiersHaveNames() {
        assertEquals("", CritChance.overcritName(0));
        assertEquals("OVERCRIT", CritChance.overcritName(1));
        assertEquals("DOUBLE OVERCRIT", CritChance.overcritName(2));
        assertEquals("TRIPLE OVERCRIT", CritChance.overcritName(3));
        assertEquals("QUAD OVERCRIT", CritChance.overcritName(4));
        // Past the words it still has to say something rather than fall back to nothing.
        assertTrue(CritChance.overcritName(7).contains("7"));
    }

    @Test
    @DisplayName("the server baseline needs a source of the player's own")
    void theConfigBaselineIsGated() {
        // Preserved from the original: a player with no investment and no gear gets nothing,
        // even with a server-wide crit chance configured.
        assertEquals(0.0, CritChance.total(of(0, 0, 0, false, false, 0.05, 0, 0)), 1e-9);
        // One point of Luck opens the gate, so it is worth 8 + 5, not 8.
        assertEquals(0.13, CritChance.total(of(1, 0, 0, false, false, 0.05, 0, 0)), 1e-9);
        // A hybrid or Rogue chance counts as the player's own source too.
        assertEquals(0.25, CritChance.total(of(0, 0, 0, false, false, 0.05, 0.20, 0)), 1e-9);
    }

    @Test
    @DisplayName("nothing invested is nothing")
    void anEmptyBuildCritsNever() {
        assertEquals(0.0, CritChance.total(of(0, 0, 0, false, false, 0, 0, 0)), 1e-9);
        assertEquals(0.0, CritChance.total(null), 1e-9);
    }

    @Test
    @DisplayName("a negative input contributes nothing rather than subtracting")
    void negativesCannotCancelOutRealBonuses() {
        // A stat that somehow read negative must not quietly eat a set bonus the player can see
        // on their own sheet.
        assertEquals(0.15, CritChance.total(of(-5, 0, 0, true, false, 0, 0, 0)), 1e-9);
        assertEquals(0.15, CritChance.total(of(0, 0, -3, true, false, 0, 0, 0)), 1e-9);
        assertEquals(0.15, CritChance.total(of(0, 0, 0, true, false, 0, -1.0, 0)), 1e-9);
        assertTrue(CritChance.total(of(-99, 0, 0, false, false, 0, 0, 0)) >= 0.0);
    }

    @Test
    @DisplayName("the displayed percent matches the roll")
    void thePercentIsTheSameNumber() {
        assertEquals(40, CritChance.percent(luck(5)));
        assertEquals(39, CritChance.percent(of(0, 0, 8, true, false, 0, 0, 0)));
        assertEquals(800, CritChance.percent(luck(100)), "percent is not capped either");
    }

    @Test
    @DisplayName("more of a stat never lowers the total")
    void investingIsMonotonic() {
        double previous = -1;
        for (int points = 0; points <= 40; points++) {
            double now = CritChance.total(luck(points));
            assertTrue(now >= previous, "crit went DOWN from " + (points - 1) + " to " + points);
            previous = now;
        }
    }
}
