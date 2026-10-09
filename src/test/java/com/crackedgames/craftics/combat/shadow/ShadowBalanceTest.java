package com.crackedgames.craftics.combat.shadow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The numbers that keep a mirror match fair.
 *
 * <p>A Shadow copies the player's kit, and a player's kit is tuned to chew through bosses with
 * many times the player's health. Hand the copy the player's real damage and it kills its
 * original in a turn. So the Shadow's damage comes from the BOSS it replaces, by the AP each
 * hit costs. Gear decides the shape of the turn, not its size - which is also why turning up
 * with nothing does not disarm it.
 */
class ShadowBalanceTest {

    @Test
    void aLoneShadowHasTheWholeBossHealthPool() {
        assertEquals(1.0, ShadowBalance.hpShare(1));
        assertEquals(75, ShadowBalance.cloneHp(75, 1));
    }

    @Test
    void eachExtraShadowHasSlightlyLessHealth() {
        assertEquals(0.85, ShadowBalance.hpShare(2), 1e-9);
        assertEquals(0.70, ShadowBalance.hpShare(3), 1e-9);
        assertEquals(0.60, ShadowBalance.hpShare(4), 1e-9);
        assertEquals(64, ShadowBalance.cloneHp(75, 2));
    }

    @Test
    void healthStopsShrinkingSoBigPartiesStillFaceRealBosses() {
        assertEquals(0.60, ShadowBalance.hpShare(5), 1e-9);
        assertEquals(0.60, ShadowBalance.hpShare(8), 1e-9);
    }

    @Test
    void moreShadowsAreAlwaysMoreTotalHealthToChewThrough() {
        int previousTotal = 0;
        for (int clones = 1; clones <= 6; clones++) {
            int total = ShadowBalance.cloneHp(75, clones) * clones;
            assertTrue(total > previousTotal, clones + " shadows should out-bulk " + (clones - 1));
            previousTotal = total;
        }
    }

    @Test
    void nonsenseCountsAreTreatedAsOneShadow() {
        assertEquals(1.0, ShadowBalance.hpShare(0));
        assertEquals(1.0, ShadowBalance.hpShare(-3));
        assertEquals(1, ShadowBalance.cloneHp(0, 1));
    }

    @Test
    void aOneApHitIsThreeTenthsOfTheBossAttack() {
        // Boss attack 12: a sword cut lands for 4, not for the 1 a split-up budget rounded to.
        assertEquals(4, ShadowBalance.hitDamage(12, 1, 7, 7));
    }

    @Test
    void everyApSpentIsWorthTheSame() {
        // Two 1-AP cuts and one 2-AP blow come to about the same thing.
        int cut = ShadowBalance.hitDamage(12, 1, 7, 7);
        int blow = ShadowBalance.hitDamage(12, 2, 7, 7);

        assertEquals(7, blow);
        assertTrue(Math.abs(cut * 2 - blow) <= 1);
    }

    @Test
    void noSingleHitIsWorthMoreThanTheBossAttack() {
        assertEquals(12, ShadowBalance.hitDamage(12, 4, 7, 7));
        assertEquals(12, ShadowBalance.hitDamage(12, 6, 7, 7));
    }

    @Test
    void howMuchApTheShadowHasDoesNotShrinkItsHits() {
        // The first version split one budget across the whole turn, so a Shadow of a player
        // with a lot of AP hit for almost nothing, many times. A hit is a hit now.
        assertEquals(ShadowBalance.hitDamage(12, 1, 7, 7), ShadowBalance.hitDamage(12, 1, 7, 7));
        assertTrue(ShadowBalance.hitDamage(12, 1, 7, 7) >= 3);
    }

    @Test
    void aWeakerWeaponInTheSameKitHitsSofter() {
        int best = ShadowBalance.hitDamage(12, 1, 8, 8);
        int worse = ShadowBalance.hitDamage(12, 1, 4, 8);

        assertTrue(worse < best);
        assertTrue(worse >= 1);
    }

    @Test
    void theBestWeaponInAnyKitHitsAtFullStrength() {
        // Fists are the best weapon of someone who brought nothing, so they hit as hard as a
        // netherite sword does for someone who brought one. Stripping down buys no mercy.
        assertEquals(ShadowBalance.hitDamage(12, 1, 9, 9), ShadowBalance.hitDamage(12, 1, 1, 1));
    }

    @Test
    void aJunkWeaponNeverFallsBelowHalfStrength() {
        int best = ShadowBalance.hitDamage(20, 2, 10, 10);
        int junk = ShadowBalance.hitDamage(20, 2, 1, 10);

        assertEquals(best / 2, junk);
    }

    @Test
    void everyHitDealsAtLeastOne() {
        assertEquals(1, ShadowBalance.hitDamage(1, 1, 1, 9));
        assertEquals(1, ShadowBalance.hitDamage(0, 1, 5, 5));
    }

    @Test
    void aBrokenApCostIsReadAsOne() {
        assertEquals(ShadowBalance.hitDamage(12, 1, 5, 5), ShadowBalance.hitDamage(12, 0, 5, 5));
    }

    @Test
    void aLifestealWeaponHealsAShadowOnItsOwnScale() {
        // Two points to a player is a tenth of their health. To a boss with 400 it is 10.
        assertEquals(10, ShadowBalance.lifesteal(2, 400));
    }

    @Test
    void lifestealNeverUndoesMoreThanASliverOfTheFight() {
        assertEquals(20, ShadowBalance.lifesteal(50, 400), "capped at a twentieth of its health");
        assertEquals(20, ShadowBalance.lifesteal(500, 400));
    }

    @Test
    void aWeaponThatHealedNothingHealsNothing() {
        assertEquals(0, ShadowBalance.lifesteal(0, 400));
        assertEquals(0, ShadowBalance.lifesteal(-2, 400));
        assertEquals(0, ShadowBalance.lifesteal(3, 0));
    }

    @Test
    void anyLifestealAtAllIsWorthAtLeastOnePoint() {
        assertEquals(1, ShadowBalance.lifesteal(1, 20));
    }
}
