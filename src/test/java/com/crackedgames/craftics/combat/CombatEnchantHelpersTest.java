package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CombatEnchantHelpersTest {
    @Test void apReductionAllPass() {
        assertEquals(5, CombatEnchantHelpers.apReduction(5, () -> 0.0));   // every roll < 0.10
    }
    @Test void apReductionAllFail() {
        assertEquals(0, CombatEnchantHelpers.apReduction(5, () -> 0.99));
    }
    @Test void apReductionExactCount() {
        // rolls: 0.05(pass),0.5(fail),0.09(pass) over level 3 => 2
        double[] rolls = {0.05, 0.5, 0.09};
        AtomicInteger i = new AtomicInteger();
        assertEquals(2, CombatEnchantHelpers.apReduction(3, () -> rolls[i.getAndIncrement()]));
    }
    @Test void apReductionZeroLevel() { assertEquals(0, CombatEnchantHelpers.apReduction(0, () -> 0.0)); }

    @Test void airResetsOutOfWater() { assertEquals(3, CombatEnchantHelpers.respirationNextAir(0, false, 3)); }
    @Test void airDecrementsInWater() { assertEquals(2, CombatEnchantHelpers.respirationNextAir(3, true, 3)); }
    @Test void drownsWhenEmptyAndInWater() { assertTrue(CombatEnchantHelpers.respirationDrowns(0, true)); }
    @Test void noDrownWithAir() { assertFalse(CombatEnchantHelpers.respirationDrowns(2, true)); }
    @Test void noDrownOutOfWater() { assertFalse(CombatEnchantHelpers.respirationDrowns(0, false)); }

    @Test void fortunePickThirds() {
        assertEquals(0, CombatEnchantHelpers.fortunePick(0.0));
        assertEquals(1, CombatEnchantHelpers.fortunePick(0.4));
        assertEquals(2, CombatEnchantHelpers.fortunePick(0.9));
    }

    private static final java.util.Set<String> UNCAPPED = java.util.Set.of("knockback");

    @Test void clampEnchantLevelCapsAboveMax() {
        // Hilt: max level I, but the lootbox/mob-gear roll is blind up to 5.
        assertEquals(1, CombatEnchantHelpers.clampEnchantLevel("hilt", 5, 1, UNCAPPED));
        assertEquals(1, CombatEnchantHelpers.clampEnchantLevel("hilt", 1, 1, UNCAPPED));
    }

    @Test void clampEnchantLevelPassesThroughAtOrBelowMax() {
        assertEquals(3, CombatEnchantHelpers.clampEnchantLevel("sharpness", 3, 5, UNCAPPED));
        assertEquals(5, CombatEnchantHelpers.clampEnchantLevel("sharpness", 5, 5, UNCAPPED));
    }

    @Test void clampEnchantLevelAllowsKnockbackAboveVanillaMax() {
        // Knockback's vanilla max is II, but Craftics deliberately lets it roll higher.
        assertEquals(5, CombatEnchantHelpers.clampEnchantLevel("knockback", 5, 2, UNCAPPED));
    }

    @Test void clampEnchantLevelUncappedSetIsExact() {
        // Only the named exception skips the clamp - everything else, including a
        // similarly-named enchant, still gets capped.
        assertEquals(2, CombatEnchantHelpers.clampEnchantLevel("punch", 5, 2, UNCAPPED));
    }
}
