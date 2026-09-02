package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.Avoidance.Layer;
import com.crackedgames.craftics.combat.Avoidance.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incoming-attack avoidance adds up, and the right layer takes the credit.
 *
 * <p>The old four-rolls-in-a-row made a tank's tooltips lie: 120% of face value avoided 79.6% of
 * hits, and each layer was worth less the more of them you had. The tests here pin the property
 * that fixes - a layer is worth its face value regardless of what else is on - plus the
 * attribution, which decides whether a shield loses durability and whether the hit counts as a
 * dodge for the Sentinel riposte.
 */
class AvoidanceTest {

    private static final Layer AC40 = new Layer(Source.ARMOR_CLASS, 0.40);
    private static final Layer ETHEREAL = new Layer(Source.ETHEREAL, 0.20);
    private static final Layer SHIELD = new Layer(Source.SHIELD, 0.25);
    private static final Layer GILDED = new Layer(Source.GILDED_GUARD, 0.15);

    @Test
    @DisplayName("layers add rather than combining probabilistically")
    void stackingIsAdditive() {
        assertEquals(0.85, Avoidance.total(List.of(AC40, ETHEREAL, SHIELD)), 1e-9);
        // The old sequential rolls gave 0.64 for the same three.
        assertEquals(0.60, Avoidance.total(List.of(AC40, ETHEREAL)), 1e-9);
    }

    @Test
    @DisplayName("a layer is worth its face value whatever else is on")
    void aLayerIsWorthTheSameAlone() {
        double aloneGain = Avoidance.total(List.of(GILDED));
        double stackedGain = Avoidance.total(List.of(AC40, ETHEREAL, GILDED))
            - Avoidance.total(List.of(AC40, ETHEREAL));
        assertEquals(aloneGain, stackedGain, 1e-9,
            "Gilded Guard must be worth 15% on a bare build and on a tank");
    }

    @Test
    @DisplayName("a tank build is walled at 90%, never certainty")
    void theCapHolds() {
        // All four at full value is 100% of face. A tank should hit a wall, not immunity.
        double all = Avoidance.total(List.of(new Layer(Source.ARMOR_CLASS, 0.60),
            ETHEREAL, SHIELD, GILDED));
        assertEquals(Avoidance.CAP, all, 1e-9);
        assertEquals(0.90, Avoidance.CAP, 1e-9);
        assertTrue(Avoidance.CAP < 1.0,
            "an enemy that can never land a hit stops being a fight");
    }

    @Test
    @DisplayName("nothing worn avoids nothing")
    void anEmptyStackNeverAvoids() {
        assertEquals(0.0, Avoidance.total(List.of()), 1e-9);
        assertEquals(0.0, Avoidance.total(null), 1e-9);
        assertFalse(Avoidance.resolve(List.of(), 0.0, 0.0).avoided());
        assertNull(Avoidance.credit(List.of(), 0.5));
    }

    @Test
    @DisplayName("the roll decides at the boundary the chance says")
    void theRollBoundaryIsExact() {
        List<Layer> stack = List.of(AC40, ETHEREAL); // 60%
        assertTrue(Avoidance.resolve(stack, 0.59, 0.0).avoided());
        assertFalse(Avoidance.resolve(stack, 0.60, 0.0).avoided());
        assertFalse(Avoidance.resolve(stack, 0.99, 0.0).avoided());
    }

    @Test
    @DisplayName("credit goes to each layer in proportion to what it contributes")
    void attributionIsWeighted() {
        List<Layer> stack = List.of(AC40, ETHEREAL, SHIELD, GILDED); // 40/20/25/15 of 100
        Map<Source, Integer> tally = new EnumMap<>(Source.class);
        for (Source s : Source.values()) tally.put(s, 0);
        for (int i = 0; i < 1000; i++) {
            tally.merge(Avoidance.credit(stack, i / 1000.0), 1, Integer::sum);
        }
        assertEquals(400, tally.get(Source.ARMOR_CLASS));
        assertEquals(200, tally.get(Source.ETHEREAL));
        assertEquals(250, tally.get(Source.SHIELD));
        assertEquals(150, tally.get(Source.GILDED_GUARD));
    }

    @Test
    @DisplayName("a lone layer always takes its own credit")
    void oneLayerTakesEveryCredit() {
        // Matters because the credited layer decides side effects: a shield-only build must have
        // its shield take the durability every single time, not sometimes.
        for (double pick = 0.0; pick < 1.0; pick += 0.05) {
            assertEquals(Source.SHIELD, Avoidance.credit(List.of(SHIELD), pick));
        }
    }

    @Test
    @DisplayName("a build over the cap still credits its layers proportionally")
    void attributionUsesTheRawShares() {
        // 60 + 20 + 25 + 15 = 120, capped to 90 for the roll. Credit still splits by the raw
        // shares, so an over-capped tank does not suddenly stop spending shield durability.
        List<Layer> over = List.of(new Layer(Source.ARMOR_CLASS, 0.60), ETHEREAL, SHIELD, GILDED);
        assertEquals(Avoidance.CAP, Avoidance.total(over), 1e-9);
        assertEquals(Source.ARMOR_CLASS, Avoidance.credit(over, 0.49));
        assertEquals(Source.ETHEREAL, Avoidance.credit(over, 0.60));
        assertEquals(Source.GILDED_GUARD, Avoidance.credit(over, 0.95));
    }

    @Test
    @DisplayName("an avoided hit always names a layer")
    void everyAvoidedHitIsAttributed() {
        // A null credit would mean no message, no hook and no durability - the hit would vanish
        // with no explanation to the player.
        List<Layer> stack = List.of(AC40, ETHEREAL, SHIELD, GILDED);
        for (double hit = 0.0; hit < 0.89; hit += 0.01) {
            for (double pick = 0.0; pick < 1.0; pick += 0.1) {
                Avoidance.Result r = Avoidance.resolve(stack, hit, pick);
                assertTrue(r.avoided());
                assertNotNull(r.by(), "avoided at hit=" + hit + " pick=" + pick + " with no credit");
            }
        }
    }

    @Test
    @DisplayName("a zero-chance layer never takes credit")
    void emptyLayersAreSkipped() {
        // layers() drops them, but credit must not hand out a share to one that slipped through.
        assertEquals(1, Avoidance.layers(SHIELD, new Layer(Source.ETHEREAL, 0.0)).size());
        List<Layer> withZero = List.of(new Layer(Source.ETHEREAL, 0.0), SHIELD);
        for (double pick = 0.0; pick < 1.0; pick += 0.05) {
            assertEquals(Source.SHIELD, Avoidance.credit(withZero, pick));
        }
    }

    @Test
    @DisplayName("more layers never lower the total")
    void addingALayerNeverHurts() {
        double previous = 0;
        List<Layer> growing = new java.util.ArrayList<>();
        for (Layer l : List.of(GILDED, ETHEREAL, SHIELD, AC40)) {
            growing.add(l);
            double now = Avoidance.total(growing);
            assertTrue(now >= previous, "adding " + l.source() + " lowered avoidance");
            previous = now;
        }
    }
}
