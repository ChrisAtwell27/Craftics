package com.crackedgames.craftics.combat;

/**
 * Every source of critical-hit chance, added into one number and rolled once.
 *
 * <p>These used to be separate sequential rolls - {@code if (!crit) crit = random() < p} repeated
 * for each source, first success wins. That is mathematically {@code 1 - PRODUCT(1 - p)}, which
 * behaves nothing like the way the numbers are written down. Three 30% sources read as 90% and
 * behaved as 65.7%. Worse, the total could never reach 100% however much a player stacked, so
 * "+15% crit" from a set was worth less the more crit you already had, and a source late in the
 * chain was only ever rolled on the hits where everything before it had already missed.
 *
 * <p>Now every source is a straight addend and there is exactly one roll. 30 + 30 + 30 is 90,
 * which is what it says. A player can read their sheet and predict their crit rate.
 *
 * <p>Free of Minecraft so the arithmetic can be checked directly. The caller reads the player's
 * gear and stats; this only adds up what it is handed.
 */
public final class CritChance {

    private CritChance() {}

    /** Luck stat, per allocated point. */
    public static final double LUCK_STAT_PER_POINT = 0.08;
    /** Luck status effect (potion, sherd, artifact), per level. */
    public static final double LUCK_EFFECT_PER_LEVEL = 0.05;
    /** Each point of LUCK from armour trims. */
    public static final double TRIM_LUCK_PER_POINT = 0.03;
    /** Wearing the full gold set. */
    public static final double GOLD_SET = 0.15;
    /** Eagle Eye (All-Seeing trim set), ranged attacks only. */
    public static final double EAGLE_EYE_RANGED = 0.30;

    /**
     * Chance above this is Overcrit rather than wasted.
     *
     * <p>Nothing is capped. Past certainty a build stops buying reliability - every hit already
     * crits - and starts buying damage instead, so the points a player sinks into Luck after the
     * hundredth percent are still doing something.
     */
    public static final double GUARANTEED = 1.0;

    /**
     * What one Overcrit multiplies damage by - the same as an ordinary critical hit.
     *
     * <p>An Overcrit IS another crit. Taken from {@link HybridSetEffects#NORMAL_CRIT_MULT} rather
     * than written out again, so the two can never drift apart.
     */
    public static final double OVERCRIT_MULT = HybridSetEffects.NORMAL_CRIT_MULT;

    /**
     * What a player's crit chance is built from.
     *
     * <p>{@code configChance} is the server-wide baseline and only counts when the player has some
     * crit source of their own - preserved from the original behaviour, where it sat behind the
     * same gate. {@code hybridChance} covers Lucky Streak and Berserker; {@code lightWeaponChance}
     * is the Rogue set's bonus with 1-AP weapons.
     */
    public record Sources(int luckPoints,
                          int luckEffectLevel,
                          int trimLuck,
                          boolean goldSet,
                          boolean eagleEyeRanged,
                          double configChance,
                          double hybridChance,
                          double lightWeaponChance) {

        /** Nothing invested and nothing worn: the server baseline does not apply on its own. */
        public boolean hasOwnSource() {
            return luckPoints > 0 || luckEffectLevel > 0 || trimLuck > 0
                || goldSet || eagleEyeRanged
                || hybridChance > 0 || lightWeaponChance > 0;
        }
    }

    /**
     * The player's total crit chance. Uncapped - anything past 1.0 becomes {@link #overcrit}.
     *
     * <p>Negative inputs are floored at zero rather than allowed to subtract from other sources: a
     * stat that somehow read negative should contribute nothing, not quietly cancel out a set
     * bonus the player can see on their sheet.
     */
    public static double total(Sources s) {
        if (s == null) return 0.0;
        double sum = 0.0;
        sum += Math.max(0, s.luckPoints()) * LUCK_STAT_PER_POINT;
        sum += Math.max(0, s.luckEffectLevel()) * LUCK_EFFECT_PER_LEVEL;
        sum += Math.max(0, s.trimLuck()) * TRIM_LUCK_PER_POINT;
        if (s.goldSet()) sum += GOLD_SET;
        if (s.eagleEyeRanged()) sum += EAGLE_EYE_RANGED;
        sum += Math.max(0.0, s.hybridChance());
        sum += Math.max(0.0, s.lightWeaponChance());
        if (s.hasOwnSource()) sum += Math.max(0.0, s.configChance());
        return Math.max(0.0, sum);
    }

    /** The same number as a whole percent, for anything that shows it to a player. */
    public static int percent(Sources s) {
        return (int) Math.round(total(s) * 100.0);
    }

    /** Whether every hit crits, so a UI can stop showing a chance and start showing Overcrit. */
    public static boolean isGuaranteed(Sources s) {
        return total(s) >= GUARANTEED;
    }

    /**
     * How many crit tiers a build lands without rolling for them.
     *
     * <p>Every whole 100% is one guaranteed tier. 100% is a guaranteed crit, 200% is a guaranteed
     * crit AND a guaranteed Overcrit, 300% is a guaranteed Double Overcrit, and so on - each
     * hundred wraps the counter round again rather than being thrown away.
     */
    public static int guaranteedTiers(Sources s) {
        return (int) Math.floor(total(s));
    }

    /**
     * The chance at ONE more tier on top of the guaranteed ones - whatever is left over.
     *
     * <p>Below 100% this is simply the crit chance, and the whole thing behaves exactly as it
     * always did: 44% crit is a 44% chance of one tier, which is one ordinary critical hit.
     */
    public static double chanceOfNextTier(Sources s) {
        double t = total(s);
        return t - Math.floor(t);
    }

    /**
     * How many crit tiers this particular swing lands.
     *
     * <p>0 is an ordinary hit, 1 is a critical hit, 2 is a crit plus one Overcrit, 3 is a Double
     * Overcrit. The roll is passed in rather than taken here so the outcome can be tested at the
     * boundaries instead of sampled.
     *
     * @param roll a value in [0, 1)
     */
    public static int rollTiers(Sources s, double roll) {
        return guaranteedTiers(s) + (roll < chanceOfNextTier(s) ? 1 : 0);
    }

    /** Overcrits in a swing that landed {@code tiers} tiers - the first tier is the crit itself. */
    public static int overcritTiers(int tiers) {
        return Math.max(0, tiers - 1);
    }

    /**
     * What damage is multiplied by for a swing that landed this many Overcrits.
     *
     * <p>Each Overcrit multiplies again, because each one IS another crit. The base multiplier is
     * applied once and the Overcrits compound on top of it, rather than the base itself being
     * raised to a power - otherwise Gladiator's 2.0 would run away from an ordinary 1.5 at deep
     * tiers, turning a flat +50% crit damage bonus into an exponential one.
     *
     * @param baseCritMult the multiplier the crit would use anyway - 1.5, or 2.0 under Gladiator
     * @param overcritTiers Overcrits landed, from {@link #overcritTiers}
     */
    public static double critMultiplier(double baseCritMult, int overcritTiers) {
        return baseCritMult * Math.pow(OVERCRIT_MULT, Math.max(0, overcritTiers));
    }

    /** What to call this many Overcrits on screen. Empty for none. */
    public static String overcritName(int overcritTiers) {
        return switch (Math.max(0, overcritTiers)) {
            case 0 -> "";
            case 1 -> "OVERCRIT";
            case 2 -> "DOUBLE OVERCRIT";
            case 3 -> "TRIPLE OVERCRIT";
            case 4 -> "QUAD OVERCRIT";
            default -> "OVERCRIT x" + overcritTiers;
        };
    }
}
