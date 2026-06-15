package com.crackedgames.craftics.combat;

/**
 * Pure, unit-testable math for the hybrid armor-set combat mechanics. The numeric
 * constants for every hybrid live here as the single source of truth; the action
 * wiring (knockback, AP, counterattacks, splash) is inline in {@code CombatManager},
 * keyed off {@code CombatManager.activeHybridEffect()}.
 */
public final class HybridSetEffects {

    private HybridSetEffects() {}

    // --- Outgoing damage bonuses ---
    public static final int SKIRMISHER_BONUS = 3;
    public static final int DUELIST_BONUS    = 4;
    public static final int DEADEYE_BONUS    = 3;

    // --- Crit ---
    public static final double NORMAL_CRIT_MULT      = 1.5;
    public static final double GLADIATOR_CRIT_MULT   = 2.0;
    public static final double LUCKY_STREAK_PER_KILL = 0.10;
    public static final int    CUTPURSE_HEAL         = 2;

    // --- Defensive ---
    public static final int    STONEWALL_CAP        = 6;
    public static final double GILDED_GUARD_CHANCE  = 0.15;
    public static final double AEGIS_DODGE_CHANCE   = 0.30;
    public static final int    IMMOVABLE_REFLECT    = 2;
    public static final int    SENTINEL_RIPOSTE     = 3;
    public static final double COUNTERPUNCH_CHANCE  = 0.50;

    // --- Multi-target & utility ---
    /** Siege: a ranged hit splashes {@code base / SIEGE_SPLASH_DIVISOR} to each adjacent enemy. */
    public static final int SIEGE_SPLASH_DIVISOR     = 2;
    /** Stormbringer: the attack arcs to one extra enemy for {@code base / STORMBRINGER_ARC_DIVISOR}. */
    public static final int STORMBRINGER_ARC_DIVISOR = 2;
    /** Warlord: melee hits knock the target back this many tiles. */
    public static final int WARLORD_KNOCKBACK        = 1;
    /** Run and Gun: ranged range bonus while the player has moved this turn. */
    public static final int RUN_AND_GUN_RANGE_BONUS  = 1;
    /** Rampage: AP refunded on a kill. */
    public static final int RAMPAGE_AP_REFUND        = 1;

    /** Stonewall: an incoming hit is capped at {@link #STONEWALL_CAP}. */
    public static int capIncomingDamage(int damage) {
        return Math.min(damage, STONEWALL_CAP);
    }

    /**
     * Berserker: extra crit chance from missing HP - +1% per 2% of max HP missing
     * (a player at 0 HP reaches +50% crit chance). Returns 0 for a non-positive maxHp.
     */
    public static double berserkerCritChance(float currentHp, float maxHp) {
        if (maxHp <= 0f) return 0.0;
        double missingFraction = 1.0 - (currentHp / maxHp);
        return Math.max(0.0, missingFraction) / 2.0;
    }

    /** Lucky Streak: extra crit chance from the current consecutive-kill streak. */
    public static double luckyStreakCritChance(int killStreak) {
        return killStreak * LUCKY_STREAK_PER_KILL;
    }
}
