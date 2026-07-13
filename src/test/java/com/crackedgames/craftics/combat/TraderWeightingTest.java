package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.registry.TraderCategoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The trader event should lean toward merchants the island has NOT met, so the Trading Hall fills
 * out instead of handing you the same Weaponsmith every time - while still leaving a met trader
 * possible.
 *
 * <p>Registers bare categories with empty stock providers: these tests are about WHICH trader gets
 * picked, and building real trades would need a Minecraft bootstrap the test JVM doesn't have.
 */
class TraderWeightingTest {

    private static final int ROLLS = 20_000;

    @BeforeEach
    void registerFourTraders() {
        TraderCategoryRegistry.clearAllForTest();
        for (String id : new String[]{"t:a", "t:b", "t:c", "t:d"}) {
            TraderCategoryRegistry.register(
                new TraderCategory(id, id, "", 0), (pool, tier) -> { });
        }
    }

    private static Map<String, Integer> rollHistogram(Set<String> met) {
        Random rng = new Random(1234);
        Map<String, Integer> hist = new HashMap<>();
        for (int i = 0; i < ROLLS; i++) {
            var offer = TraderSystem.generateOffer(1, rng, met);
            assertNotNull(offer, "a trader must always be offered when any is registered");
            hist.merge(offer.type().id(), 1, Integer::sum);
        }
        return hist;
    }

    @Test
    void unmetTradersAreFavouredOverMetOnes() {
        Map<String, Integer> hist = rollHistogram(Set.of("t:a"));
        int metCount = hist.getOrDefault("t:a", 0);
        int unmetAvg = (hist.getOrDefault("t:b", 0)
            + hist.getOrDefault("t:c", 0) + hist.getOrDefault("t:d", 0)) / 3;
        assertTrue(unmetAvg > metCount * 3,
            "an unmet trader should come up far more often than a met one: met=" + metCount
                + " unmetAvg=" + unmetAvg);
    }

    /** Rarer, but never impossible - you can still bump into someone you know. */
    @Test
    void aMetTraderIsStillPossible() {
        Map<String, Integer> hist = rollHistogram(Set.of("t:a"));
        assertTrue(hist.getOrDefault("t:a", 0) > 0,
            "a met trader must stay reachable, just less likely");
    }

    /** With everyone met the weights equalise, so the roll must not get stuck or starve. */
    @Test
    void allMetDegradesToUniform() {
        Map<String, Integer> hist = rollHistogram(Set.of("t:a", "t:b", "t:c", "t:d"));
        assertEquals(4, hist.size(), "every trader should still be reachable once all are met");
        for (var e : hist.entrySet()) {
            // Uniform would be 25%; allow generous slack for RNG.
            assertTrue(e.getValue() > ROLLS / 8,
                e.getKey() + " starved once every trader was met: " + e.getValue());
        }
    }

    /**
     * Saves from before 0.2.10 stored the bare, upper-cased enum name. If those aren't resolved
     * back to the namespaced id, an already-met trader keeps being treated as new and rolled at
     * full weight - so every existing player would go on meeting the same merchants forever.
     *
     * <p>Uses synthetic ids rather than VanillaTraderContent: registering the real traders builds
     * ItemStacks, which needs a Minecraft bootstrap the test JVM doesn't have.
     */
    @Test
    void legacyMetIdsStillCountAsMet() {
        // "T:A" is the legacy form of "t:a" - unnamespaced and upper-cased.
        Map<String, Integer> hist = rollHistogram(Set.of("A"));
        int metCount = hist.getOrDefault("t:a", 0);
        int unmetAvg = (hist.getOrDefault("t:b", 0)
            + hist.getOrDefault("t:c", 0) + hist.getOrDefault("t:d", 0)) / 3;
        assertTrue(unmetAvg > metCount * 3,
            "a legacy-named met trader must still be down-weighted: met=" + metCount
                + " unmetAvg=" + unmetAvg);
    }

    @Test
    void noRegisteredTradersMeansNoOffer() {
        TraderCategoryRegistry.clearAllForTest();
        assertNull(TraderSystem.generateOffer(1, new Random(), Set.of()));
    }
}
