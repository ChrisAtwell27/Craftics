package com.crackedgames.craftics.combat;

/**
 * Pure shrine reward maths, extracted from the legacy handleEventChoice shrine branch
 * so the tier/odds logic is testable. Item tables stay in CombatManager (they need
 * ItemStack); this only decides the reward BAND and the offering COST per tier.
 */
public final class ShrineRewards {

    public enum Band { NOTHING, COMMON, GOOD, GREAT, JACKPOT }

    private ShrineRewards() {}

    /** Offering cost in emeralds for tier 0/1/2 (small/medium/large). */
    public static int cost(int tier) {
        return switch (tier) {
            case 0 -> 2;
            case 1 -> 5;
            default -> 10;
        };
    }

    /**
     * Select the reward band. Mirrors the original: the raw 0..99 roll is shifted down
     * by {@code 8 * lootTierBonus} (clamped at 0), then compared against tier thresholds.
     *
     * @param tier          0 small, 1 medium, 2 large
     * @param raw100Roll    a value in [0,100) (caller supplies rng.nextInt(100))
     * @param lootTierBonus biome loot-tier bonus (getEventLootTierBonus)
     */
    public static Band band(int tier, int raw100Roll, int lootTierBonus) {
        int roll = Math.max(0, raw100Roll - lootTierBonus * 8);
        int threshold = tier == 0 ? 40 : (tier == 1 ? 25 : 10);
        int bust = tier == 0 ? 25 : (tier == 1 ? 15 : 8);
        if (roll < bust) return Band.NOTHING;
        if (roll < threshold) return Band.COMMON;
        if (roll < threshold + 35) return Band.GOOD;
        if (roll < threshold + 55) return Band.GREAT;
        return Band.JACKPOT;
    }
}
