package com.crackedgames.craftics.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Map;

/**
 * The combat heal value of ANY edible item - vanilla or modded - derived from
 * the item's own {@link FoodComponent} instead of a hand-written table.
 *
 * <p>The old system was a fixed {@code Map<Item,Integer>} of ~39 vanilla foods:
 * anything outside it (every modded food, every food added by a future MC
 * version) silently healed the fallback 1 HP and had no tooltip. This reads
 * nutrition and saturation off the item, so a modded steak that is objectively
 * better than a vanilla one heals more, automatically.
 *
 * <p><b>The formula</b> is {@code 0.45 * nutrition + 0.125 * saturation},
 * clamped to {@value #MIN_HEAL}..{@value #MAX_HEAL}. It was fitted against the
 * old hand-tuned table so the rework doesn't silently rebalance the game:
 * 33 of the 39 vanilla foods come out at exactly their old value and the rest
 * are within 1 HP. Cooked beef stays 5, rabbit stew stays 6, an apple stays 2,
 * a cookie stays 1.
 *
 * <p>Note {@code FoodComponent.saturation()} is the COMPUTED saturation value
 * (nutrition x modifier x 2), not the modifier - the builder's
 * {@code saturationModifier} bakes it in. The weights above assume that.
 *
 * <p>The clamp matters for modded content: a joke food with nutrition 100 is
 * capped at {@value #MAX_HEAL} rather than becoming a full heal. Genuinely
 * magical foods that don't follow their own nutrition (the golden apples)
 * keep authored values in {@link #OVERRIDES} and bypass the clamp.
 */
public final class FoodValues {

    private FoodValues() {}

    /** Weight on hunger points restored. */
    private static final float NUTRITION_WEIGHT = 0.45f;
    /** Weight on the computed saturation value. */
    private static final float SATURATION_WEIGHT = 0.125f;

    /** Any edible heals at least this much, so a nutrition-1 scrap is still worth an AP. */
    public static final int MIN_HEAL = 1;
    /**
     * Ceiling for formula-derived foods. Generous rather than tight because the
     * AP tiers below are the real balancing lever now: a monster modded food can
     * heal a lot, but it costs most or all of a turn to eat.
     */
    public static final int MAX_HEAL = 16;

    /** At or above this heal, eating costs 2 AP instead of 1. */
    public static final int AP2_THRESHOLD = 7;
    /** At or above this heal, eating costs 3 AP. */
    public static final int AP3_THRESHOLD = 12;

    /**
     * Foods whose combat value is authored rather than derived. These are magic
     * items whose nutrition doesn't reflect what they do (a golden apple has the
     * nutrition of an apple). Overrides bypass {@link #MAX_HEAL}.
     *
     * <p>Deliberately held in a lazy holder rather than a plain static field:
     * touching {@code Items} runs Minecraft's registry init, and the unit tests
     * exercise the pure {@link #healFor(int, float)} math with no bootstrap. A
     * plain static map here would run at class-load and break every one of them.
     * Do not "simplify" this back into a direct field.
     */
    private static final class Overrides {
        static final Map<Item, Integer> MAP = Map.of(
            Items.GOLDEN_APPLE, 8,
            Items.ENCHANTED_GOLDEN_APPLE, 10,
            // The golden carrot's combat value is its AP refund, not its HP.
            Items.GOLDEN_CARROT, 2
        );
    }

    /** The item's food component, or {@code null} if it isn't edible. */
    public static FoodComponent foodOf(Item item) {
        if (item == null) return null;
        return item.getComponents().get(DataComponentTypes.FOOD);
    }

    /** True if the item is edible at all (vanilla or modded). */
    public static boolean isEdible(Item item) {
        return foodOf(item) != null;
    }

    /**
     * HP this food restores in combat, or 0 if the item isn't edible.
     * Authored {@link #OVERRIDES} win; everything else is the fitted formula.
     */
    public static int healFor(Item item) {
        Integer override = Overrides.MAP.get(item);
        if (override != null) return override;
        FoodComponent food = foodOf(item);
        if (food == null) return 0;
        return healFor(food.nutrition(), food.saturation());
    }

    /** The raw formula, exposed for tests and for callers holding loose stats. */
    public static int healFor(int nutrition, float saturation) {
        int raw = Math.round(NUTRITION_WEIGHT * nutrition + SATURATION_WEIGHT * saturation);
        return Math.max(MIN_HEAL, Math.min(MAX_HEAL, raw));
    }

    /**
     * AP it costs to eat this food in combat, scaled by how much it heals: a big
     * heal is a bigger commitment, so a top-tier food can eat most of a turn.
     * Callers that special-case an item's cost (the golden carrot is free) do so
     * before consulting this.
     */
    public static int apCostFor(Item item) {
        return apCostForHeal(healFor(item));
    }

    /** AP tier for an already-known heal amount. */
    public static int apCostForHeal(int heal) {
        if (heal >= AP3_THRESHOLD) return 3;
        if (heal >= AP2_THRESHOLD) return 2;
        return 1;
    }
}
