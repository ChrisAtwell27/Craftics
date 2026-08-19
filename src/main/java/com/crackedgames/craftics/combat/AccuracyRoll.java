package com.crackedgames.craftics.combat;

import java.util.Random;

/**
 * To-hit resolution for a combatant's attack.
 *
 * <p>Distinct from {@link DodgeRoll}, and the two are not interchangeable. Dodge is a
 * property of the <b>defender</b> - your armor class against what is swinging at you -
 * and answers "did I get out of the way". Accuracy is a property of the <b>attack</b>:
 * a creature whose movepool holds a wild haymaker and a reliable jab needs the haymaker
 * to miss more often, and no amount of defender-side maths expresses that.
 *
 * <p>Accuracy is a multiplier where {@code 1.0} is "always lands". That default is what
 * keeps the whole mechanic inert: nothing in Craftics sets an accuracy today, so every
 * attack resolves at 1.0 and behaves exactly as it did before. It is the same bet
 * {@code AttackTypeRegistry} makes by returning 1.0 for anything untyped - a system that
 * costs nothing until something opts in can sit in the hot path without argument.
 *
 * <p><b>A certain hit consumes no randomness.</b> {@link #roll} returns early at
 * {@value #CAP}% without touching the {@link Random}, so adding this call to a damage
 * path does not shift the sequence any other roll in that fight would have drawn. Were
 * it to draw and discard, every existing dodge and crit downstream of it would land
 * differently the day this shipped, and that would read as a balance change nobody made.
 *
 * <ul>
 *   <li>{@value #FLOOR}% floor - a stacked pile of accuracy debuffs cannot grind a combatant
 *       down to unable-to-act. A creature that can never land a hit is indistinguishable from
 *       a stunned one, and stun is already a mechanic. Penalties stack by multiplication and
 *       so only ever approach zero, which is precisely the case the floor is for.</li>
 *   <li>{@value #CAP}% cap - accuracy above 1.0 is clamped rather than rejected, so an
 *       addon handing out a +50% accuracy buff gets a reliable attacker instead of an
 *       exception.</li>
 *   <li>Both ends are exact, neither clamped: an accuracy of 0 never lands and an accuracy of
 *       1 always does. The floor exists to stop debuffs accumulating into silence, not to
 *       overrule something that asked for never - a config knob set to zero that still landed
 *       one shot in twenty would look broken.</li>
 * </ul>
 */
public final class AccuracyRoll {

    private AccuracyRoll() {}

    /** Lowest hit chance, in percent - no stack of debuffs makes an attacker useless. */
    public static final int FLOOR = 5;
    /** Highest hit chance, in percent. Also the value at which no roll is drawn. */
    public static final int CAP = 100;

    /** Accuracy of an attack nothing has modified: it always lands. */
    public static final double DEFAULT = 1.0;

    /**
     * Sentinel for "no per-action override set" in
     * {@link CombatEntity#getPendingAccuracy()}. Negative rather than {@code null} so the
     * slot is a primitive - it is read on every hit and boxing it would allocate per swing.
     */
    public static final double NO_OVERRIDE = -1.0;

    /** How far a blinded combatant's accuracy is scaled. */
    public static final double BLINDED_MULTIPLIER = 0.5;

    /**
     * Apply blindness to whatever accuracy an attack was already going to use.
     *
     * <p>Scales rather than assigns. A commanded or addon-authored move brings its own
     * accuracy, and overwriting it with {@value #BLINDED_MULTIPLIER} would make a wild
     * haymaker <b>more</b> likely to land while blinded than while sighted - blindness
     * would read as a buff on exactly the attacks it should punish hardest.
     *
     * @param current the accuracy already set, or {@link #NO_OVERRIDE} when none is
     * @return the blinded accuracy, itself a value that can be blinded again
     */
    public static double blinded(double current) {
        return current == NO_OVERRIDE ? BLINDED_MULTIPLIER : current * BLINDED_MULTIPLIER;
    }

    /**
     * Result of a to-hit roll: whether it landed, the chance used, and the d100 drawn.
     *
     * @param rolled the d100 result, or {@code 0} when the hit was certain and no roll
     *               was drawn. Present for logging and tests, not for gameplay decisions
     */
    public record HitResult(boolean hit, int hitPercent, int rolled) {}

    /**
     * Hit chance (in percent, {@value #FLOOR}-{@value #CAP}) for the given accuracy
     * multiplier.
     */
    public static int hitPercent(double accuracy) {
        if (Double.isNaN(accuracy)) return CAP;
        if (accuracy >= 1.0) return CAP;
        if (accuracy <= 0.0) return 0;
        int pct = (int) Math.round(accuracy * 100.0);
        return Math.max(FLOOR, Math.min(CAP, pct));
    }

    /**
     * Rolls a d100 against the chance {@link #hitPercent} derives from {@code accuracy}.
     *
     * <p>Draws nothing from {@code rng} when the hit is certain - see the class note.
     *
     * @return a {@link HitResult} whose {@code hit} is false when the attack misses
     */
    public static HitResult roll(double accuracy, Random rng) {
        int pct = hitPercent(accuracy);
        // Both certainties short-circuit without touching the RNG - see the class note.
        if (pct >= CAP) return new HitResult(true, CAP, 0);
        if (pct <= 0) return new HitResult(false, 0, 0);
        int rolled = rng.nextInt(100) + 1; // 1..100
        return new HitResult(rolled <= pct, pct, rolled);
    }
}
