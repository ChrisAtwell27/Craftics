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
     * What a player takes of the tick a mob would take from the same effect.
     *
     * <p>Players and mobs run the identical formulas - that is the whole point of this class -
     * but a player is one health bar that has to survive a whole run, while a mob only has to
     * survive this fight. The same number is therefore not the same threat, so the player's
     * share is trimmed once, here, at the very end.
     *
     * <p>One factor at one place, applied to the finished tick rather than woven into each
     * formula, so there is still exactly one definition of what poison does and the difference
     * between the two sides is a single readable number instead of a second ruleset.
     *
     * <p>Three fifths is not arbitrary. Together with {@link #MAX_PLAYER_BLEED_STACKS} it is the
     * value that holds the hard requirement in
     * {@code EffectParityTest.playerBleedIsNeverWorseThanTheFlatVersionItReplaced}: turning bleed
     * into accumulating stacks must not make it harsher against a player than the flat effect it
     * replaced, at ANY strength. Raise this and that test is what tells you.
     */
    public static final double PLAYER_DOT_SCALE = 0.6;

    /**
     * Ceiling on bleed stacks carried by a PLAYER, well under the mob ceiling.
     *
     * <p>Bleed is the one DOT that accumulates, and accumulation is what makes it dangerous:
     * stacks climb faster than they decay when a player is being hit every turn. Unbounded, that
     * turned the mob-side curve loose on a 20 HP health bar and made bleed harsher than the flat
     * version it replaced. Five stacks is the most a player carries, which after
     * {@link #PLAYER_DOT_SCALE} lands strictly below the old flat value it is replacing.
     */
    public static final int MAX_PLAYER_BLEED_STACKS = 5;

    /**
     * Put a finished tick through the player's share.
     *
     * <p>Rounds rather than truncates, and that is not a detail. Poison is front-loaded and
     * wither ramps - their whole character is that consecutive ticks differ - and truncating
     * three-quarters of 5, 4 and 3 gives 3, 3, 2, collapsing the first two into a plateau and
     * quietly flattening the shape the formula exists to create. Rounding keeps them 4, 3, 2.
     *
     * <p>Floors at 1 whenever there was any damage at all: an effect the player can see on their
     * status bar has to do something, or it reads as broken.
     */
    public static int forPlayer(int tick) {
        if (tick <= 0) return 0;
        return Math.max(1, (int) Math.round(tick * PLAYER_DOT_SCALE));
    }

    /**
     * The share of a victim's own health pool that every damage-over-time tick carries.
     *
     * <p>A DOT that is purely flat means something completely different to a 20 HP player and a
     * 400 HP boss: lethal to one, a rounding error to the other. Scaling part of the tick by the
     * victim's OWN maximum health makes one rule cover both - the same poison is a real threat
     * to whoever has it, without needing a separate player table and a separate mob table that
     * drift apart.
     *
     * <p>This is also why nothing here needs a manual "scale it down for players" factor. A
     * twentieth of the victim's pool is +1 on a 20 HP player and +10 on a 200 HP boss; the
     * scaling falls out of whose health it is.
     *
     * <p>Was live on mobs only ({@code CombatEntity.getMaxHpDotBonus}) while players took the
     * flat term alone, which is exactly the split this class exists to close.
     */
    public static int maxHpDotBonus(int maxHp) {
        return Math.max(1, maxHp / 20);
    }

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
