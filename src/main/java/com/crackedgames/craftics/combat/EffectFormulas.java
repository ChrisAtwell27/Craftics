package com.crackedgames.craftics.combat;

/**
 * The canonical status-effect math, shared by players and enemies.
 *
 * <p>Players and enemies store effects differently: players use a {@link CombatEffects} map of
 * amplifier + duration, enemies use hardcoded per-effect fields on {@link CombatEntity}. That
 * split let the two sides drift until the same effect name meant two different things depending
 * on who had it - poison front-loaded on a player but was flat on a mob, wither ramped on a
 * player but tapered on a mob. This class is the single definition both sides now compute from,
 * so an effect means one thing.
 *
 * <p>The player's behavior is canonical: every formula here is the one that was already running
 * in {@code CombatEffects.applyPerTurnEffects}.
 *
 * <p>Deliberately free of Minecraft types - plain ints in, int out - so the entire ruleset is
 * unit-testable with no bootstrap, and so a parity test can hold the two sides together.
 *
 * <p>Level convention: {@code level = amplifier + 1}, so amplifier 0 is level I. Both sides use
 * it; passing a raw amplifier here silently understates every number by one level.
 */
public final class EffectFormulas {

    private EffectFormulas() {}

    /** Hard ceiling on a single burn tick. High levels make these grow without bound, so one
     *  tick is clamped here to keep a runaway DOT from one-shotting a full-HP target. */
    public static final int MAX_DOT_TICK = 100;

    /** Hard ceiling on a single bleed tick, well under {@link #MAX_DOT_TICK}. Bleed reaches its
     *  cap far faster than any other DOT because stacks are added several at a time (Sharpness V
     *  alone is +5 per hit) and only decay by one a turn, so the general DOT ceiling was never a
     *  real limit on it - the curve simply ran to 100 in three swings. */
    public static final int MAX_BLEED_TICK = 50;

    /**
     * Poison damage for one tick. Front-loaded: the {@code turnsRemaining} term means it hits
     * hardest on the first tick and fades as the effect runs out, so cleansing it late saves
     * little.
     */
    public static int poisonTick(int level, int turnsRemaining, int specialAffinity) {
        return Math.max(1, (2 * level) + turnsRemaining + specialAffinity);
    }

    /**
     * Wither damage for one tick. Ramps: the per-tick base is multiplied by how many turns have
     * elapsed, so it is weakest at the start and worst on its final tick - the mirror image of
     * poison, and the reason cleansing wither late saves the most.
     *
     * @param peakTurns the highest duration this wither has reached, so the ramp measures from
     *                  its real start rather than resetting when it is re-applied
     */
    public static int witherTick(int level, int peakTurns, int turnsRemaining, int specialAffinity) {
        int base = 1 + level + specialAffinity;
        int peak = Math.max(1, peakTurns);
        int elapsed = Math.max(1, peak - turnsRemaining + 1);
        return Math.max(1, base * elapsed);
    }

    /** Burning damage for one tick. Flat: the same every turn. Clamped to {@link #MAX_DOT_TICK}. */
    public static int burningTick(int level, int specialAffinity) {
        return Math.min(MAX_DOT_TICK, Math.max(1, 1 + level + specialAffinity));
    }

    /**
     * Bleed damage for one tick: half the triangle of the stack count (1, 1, 3, 5, 7, 10, 14,
     * 18...), so bleed still punishes stacking rather than duration, but no longer outruns the
     * fight. Special affinity does not scale it.
     *
     * <p>The full triangle {@code stacks*(stacks+1)/2} was the old curve, and it compounded far
     * faster than stacks decay: they fall by one a turn while a Sharpness V sword adds five per
     * hit, before Serrated, Piercing or Impaling add their own. That put a plain sword at 15
     * damage a turn after one swing, 45 after two and 91 after three - past most things' whole
     * HP pool - so bleed decided fights on its own. Halved, and clamped by
     * {@link #MAX_BLEED_TICK}, the same three swings read 7, 22 and 45, and the ceiling needs
     * fourteen live stacks to reach.
     */
    public static int bleedTick(int stacks) {
        if (stacks <= 0) return 0;
        // Floors at 1: a single stack halves to zero, and an effect that is active always does
        // something. That makes 1 and 2 stacks both worth 1, which is the intent - one nick is
        // a nick whether you took it once or twice.
        return Math.min(MAX_BLEED_TICK, Math.max(1, stacks * (stacks + 1) / 4));
    }

    /** Range lost to vision debuffs: Blindness costs 2 per level, Darkness 1, and they stack. */
    public static int rangePenalty(int blindnessLevel, int darknessLevel) {
        return (2 * Math.max(0, blindnessLevel)) + Math.max(0, darknessLevel);
    }
}
