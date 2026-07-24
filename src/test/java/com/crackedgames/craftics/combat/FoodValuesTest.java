package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Calibration guard for the generic food heal formula.
 *
 * <p>Food heal values used to be a hand-written table of ~39 vanilla items.
 * Replacing it with a formula over nutrition/saturation is only safe if the
 * formula reproduces the numbers players already knew, so these tests pin the
 * vanilla anchors: if someone retunes the weights, the anchors that drift will
 * say so.
 *
 * <p>Only the pure {@code healFor(nutrition, saturation)} overload is exercised.
 * The {@code Item}-based overloads need a Minecraft bootstrap that the test
 * source set deliberately does not have, which is exactly why the math lives in
 * a stats-only method.
 *
 * <p>Inputs are vanilla's real values. Note {@code saturation} is the COMPUTED
 * saturation (nutrition x modifier x 2), matching {@code FoodComponent.saturation()}.
 */
class FoodValuesTest {

    /** The staples, at exactly the values the old hardcoded table used. */
    @Test
    void reproducesVanillaAnchors() {
        assertEquals(2, FoodValues.healFor(4, 2.4f), "apple");
        assertEquals(3, FoodValues.healFor(5, 6.0f), "bread");
        assertEquals(5, FoodValues.healFor(8, 12.8f), "cooked beef");
        assertEquals(5, FoodValues.healFor(8, 12.8f), "cooked porkchop");
        assertEquals(4, FoodValues.healFor(6, 9.6f), "cooked mutton");
        assertEquals(3, FoodValues.healFor(5, 6.0f), "baked potato");
        assertEquals(4, FoodValues.healFor(8, 4.8f), "pumpkin pie");
        assertEquals(1, FoodValues.healFor(2, 0.4f), "cookie");
        assertEquals(6, FoodValues.healFor(10, 12.0f), "rabbit stew - the best vanilla food");
        assertEquals(4, FoodValues.healFor(6, 7.2f), "mushroom stew");
        assertEquals(2, FoodValues.healFor(4, 0.8f), "rotten flesh");
        assertEquals(2, FoodValues.healFor(3, 3.6f), "carrot");
    }

    /** Better food heals more. The whole point of deriving from the component. */
    @Test
    void betterFoodHealsMore() {
        int rawBeef = FoodValues.healFor(3, 1.8f);
        int cookedBeef = FoodValues.healFor(8, 12.8f);
        int rabbitStew = FoodValues.healFor(10, 12.0f);
        assertTrue(cookedBeef > rawBeef, "cooking beef must be an upgrade");
        assertTrue(rabbitStew > cookedBeef, "stew must beat a steak");
    }

    /** Even a scrap is worth something, and no modded outlier becomes a full heal. */
    @Test
    void clampsBothEnds() {
        assertEquals(FoodValues.MIN_HEAL, FoodValues.healFor(0, 0f), "nothing heals below the floor");
        assertEquals(FoodValues.MIN_HEAL, FoodValues.healFor(1, 0.2f), "tropical fish still heals 1");
        assertEquals(FoodValues.MAX_HEAL, FoodValues.healFor(999, 999f), "a joke modded food is capped");
    }

    /** AP scales in tiers with the heal, so a big heal costs a real part of the turn. */
    @Test
    void apScalesWithHeal() {
        assertEquals(1, FoodValues.apCostForHeal(1));
        assertEquals(1, FoodValues.apCostForHeal(FoodValues.AP2_THRESHOLD - 1));
        assertEquals(2, FoodValues.apCostForHeal(FoodValues.AP2_THRESHOLD));
        assertEquals(2, FoodValues.apCostForHeal(FoodValues.AP3_THRESHOLD - 1));
        assertEquals(3, FoodValues.apCostForHeal(FoodValues.AP3_THRESHOLD));
        assertEquals(3, FoodValues.apCostForHeal(FoodValues.MAX_HEAL));
    }

    /** Every vanilla food stays at 1 AP; only the golden apples and modded monsters cost more. */
    @Test
    void vanillaFoodStaysOneAp() {
        assertEquals(1, FoodValues.apCostForHeal(FoodValues.healFor(10, 12.0f)),
            "rabbit stew, the best vanilla food, must still be a 1 AP snack");
    }
}
