package com.crackedgames.craftics.combat.barter;

import java.util.Random;

/**
 * Pure mechanics for piglin bartering: the secret success threshold and the offer-scaled
 * success/overpay odds. No Minecraft dependencies in these methods so they stay unit-testable.
 * Reward selection (which touches ItemStack/registries) lives in later methods added in the
 * reward-roll task.
 */
public final class PiglinBarterSystem {

    /** Inclusive minimum of the secret threshold roll. */
    public static final int MIN_THRESHOLD = 16;
    /** Inclusive maximum of the secret threshold roll (also the max offer). */
    public static final int MAX_THRESHOLD = 64;

    /** Per-surplus-gold bonus probability, before the cap. */
    private static final double OVERPAY_PER_GOLD = 0.03;

    private PiglinBarterSystem() {}

    /**
     * Secret offer (in gold ingots) that guarantees a successful barter, rolled uniformly in
     * {@code [MIN_THRESHOLD, MAX_THRESHOLD]} per player per event.
     */
    public static int rollThreshold(Random random) {
        return MIN_THRESHOLD + random.nextInt(MAX_THRESHOLD - MIN_THRESHOLD + 1);
    }

    /**
     * Linear success chance for an {@code offer} against the secret {@code threshold}:
     * {@code clamp(offer / threshold, 0, 1)}. A non-positive threshold is treated as a
     * guaranteed success (defensive; the real roll is always >= 16).
     */
    public static double successChance(int offer, int threshold) {
        if (threshold <= 0) return 1.0;
        double c = (double) offer / (double) threshold;
        if (c < 0.0) return 0.0;
        if (c > 1.0) return 1.0;
        return c;
    }

    /**
     * Chance at a second bonus item when the offer exceeded the secret threshold. Scales linearly
     * with the surplus gold and is capped at 1.0. Zero when there is no surplus.
     */
    public static double overpayBonusChance(int surplus) {
        if (surplus <= 0) return 0.0;
        double c = surplus * OVERPAY_PER_GOLD;
        return c > 1.0 ? 1.0 : c;
    }
}
