package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.FishingTable.Catch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fishing odds, counted rather than read.
 *
 * <p>These numbers ARE the balance: "about a third of casts catch nothing" and "treasure opens up
 * as a run goes on" are claims about a distribution, and a distribution is the one thing you
 * cannot check by looking at the branches that produce it.
 */
class FishingTableTest {

    /** Every possible roll at one point in a run, tallied. */
    private static Map<Catch, Integer> tally(int ordinal) {
        Map<Catch, Integer> counts = new EnumMap<>(Catch.class);
        for (Catch c : Catch.values()) counts.put(c, 0);
        for (int roll = 0; roll < 100; roll++) {
            counts.merge(FishingTable.resolve(roll, ordinal), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    @DisplayName("30% of casts catch nothing, at every point in a run")
    void nothingIsThirtyPercentAlways() {
        for (int ordinal = 0; ordinal <= 12; ordinal++) {
            assertEquals(30, tally(ordinal).get(Catch.NOTHING),
                "empty casts at ordinal " + ordinal);
        }
    }

    @Test
    @DisplayName("5% of casts hook a drowned, at every point in a run")
    void drownedIsFivePercentAlways() {
        for (int ordinal = 0; ordinal <= 12; ordinal++) {
            assertEquals(5, tally(ordinal).get(Catch.DROWNED),
                "drowned at ordinal " + ordinal);
        }
    }

    @Test
    @DisplayName("every roll resolves to exactly one outcome")
    void theTableAddsUp() {
        for (int ordinal = 0; ordinal <= 12; ordinal++) {
            int total = tally(ordinal).values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(100, total, "ordinal " + ordinal + " does not cover every roll");
        }
    }

    @Test
    @DisplayName("the first biome is mostly fish, and treasure is rare")
    void earlyRunsCatchFish() {
        Map<Catch, Integer> first = tally(0);
        assertEquals(2, first.get(Catch.TREASURE), "treasure should be a real surprise on level one");
        assertTrue(first.get(Catch.COMMON_FISH) > first.get(Catch.GOOD_ITEM) + first.get(Catch.TREASURE),
            "plain fish should dominate an early table");
    }

    @Test
    @DisplayName("progress makes the good tiers more likely, monotonically")
    void progressImprovesTheCatch() {
        int previousGoodOrBetter = -1;
        for (int ordinal = 0; ordinal <= 12; ordinal++) {
            Map<Catch, Integer> t = tally(ordinal);
            int goodOrBetter = t.get(Catch.GOOD_ITEM) + t.get(Catch.TREASURE);
            assertTrue(goodOrBetter >= previousGoodOrBetter,
                "fishing got WORSE going from ordinal " + (ordinal - 1) + " to " + ordinal);
            previousGoodOrBetter = goodOrBetter;
        }
        assertTrue(tally(8).get(Catch.TREASURE) > tally(0).get(Catch.TREASURE),
            "a late run should out-fish the first level");
    }

    @Test
    @DisplayName("a long run cannot turn the whole table into treasure")
    void theGoodTiersAreCapped() {
        Map<Catch, Integer> veryLate = tally(40);
        assertEquals(12, veryLate.get(Catch.TREASURE), "treasure is capped");
        assertEquals(20, veryLate.get(Catch.GOOD_ITEM), "good is capped");
        assertTrue(veryLate.get(Catch.COMMON_FISH) > 0,
            "there must still be plain fish in the deepest run - the caps exist for this");
    }

    @Test
    @DisplayName("a drowned hooked late is worth more than one hooked early")
    void theDrownedScalesWithTheRun() {
        assertTrue(FishingTable.drownedHealth(8) > FishingTable.drownedHealth(0));
        assertTrue(FishingTable.drownedAttack(8) > FishingTable.drownedAttack(0));
        // A negative ordinal is not a thing, but a caller reading an unset field could hand
        // one over, and a drowned with negative health would simply not work.
        assertTrue(FishingTable.drownedHealth(-5) > 0);
        assertTrue(FishingTable.drownedAttack(-5) > 0);
    }
}
