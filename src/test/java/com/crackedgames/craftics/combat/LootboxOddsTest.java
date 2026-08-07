package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ported alongside {@code LootboxOdds} from the plugin's {@code WeightedRollTest}-shaped
 * expectations: the roll and the displayed percentage must be the exact same arithmetic, and
 * both must degrade sanely when there is nothing winnable.
 */
class LootboxOddsTest {

    private record Entry(String id, int weight) implements LootboxOdds.Weighted {}

    @Test
    void pickFavoursHeavierWeightsProportionally() {
        List<Entry> pool = List.of(new Entry("common", 90), new Entry("legendary", 10));
        Random rng = new Random(42);
        int legendary = 0;
        int rolls = 20_000;
        for (int i = 0; i < rolls; i++) {
            Entry picked = LootboxOdds.pick(pool, rng).orElseThrow();
            if (picked.id().equals("legendary")) legendary++;
        }
        double share = legendary / (double) rolls;
        assertTrue(share > 0.07 && share < 0.13,
            "legendary should land near its 10% weight share, got " + share);
    }

    @Test
    void emptyPoolNeverGetsPicked() {
        assertTrue(LootboxOdds.pick(List.of(), new Random()).isEmpty());
    }

    @Test
    void allZeroWeightIsUnopenable() {
        List<Entry> pool = List.of(new Entry("a", 0), new Entry("b", 0));
        assertTrue(LootboxOdds.pick(pool, new Random()).isEmpty(),
            "a pool with no positive weight must never be picked from");
    }

    @Test
    void negativeWeightsAreTreatedAsZeroNotSubtracted() {
        // A malformed negative weight must never let roll() walk past the end of the list
        // (which would be reachable if negative weights reduced the summed total below what
        // the walk actually subtracts).
        List<Entry> pool = List.of(new Entry("broken", -5), new Entry("fine", 10));
        Random rng = new Random(7);
        for (int i = 0; i < 1000; i++) {
            Entry picked = LootboxOdds.pick(pool, rng).orElseThrow();
            assertEquals("fine", picked.id());
        }
    }

    @Test
    void aSinglePositiveWeightIsAlwaysPicked() {
        List<Entry> pool = List.of(new Entry("only", 1));
        Optional<Entry> picked = LootboxOdds.pick(pool, new Random());
        assertTrue(picked.isPresent());
        assertEquals("only", picked.get().id());
    }

    @Test
    void chanceOfMatchesTheProportionOfWeight() {
        assertEquals(25.0, LootboxOdds.chanceOf(25, 100));
        assertEquals(0.0, LootboxOdds.chanceOf(0, 100));
        assertEquals(0.0, LootboxOdds.chanceOf(10, 0), "an empty pool must show zero, not divide by zero");
        assertEquals(100.0, LootboxOdds.chanceOf(5, 5));
    }

    @Test
    void formatIsAlwaysTwoDecimalsSoAColumnLinesUp() {
        assertEquals("25.00%", LootboxOdds.format(25.0));
        assertEquals("33.33%", LootboxOdds.format(100.0 / 3.0));
        assertEquals("0.10%", LootboxOdds.format(0.1));
        assertEquals("100.00%", LootboxOdds.format(100.0));
    }

    @Test
    void formatChanceComposesChanceOfAndFormat() {
        assertEquals(LootboxOdds.format(LootboxOdds.chanceOf(7, 40)), LootboxOdds.formatChance(7, 40));
    }
}
