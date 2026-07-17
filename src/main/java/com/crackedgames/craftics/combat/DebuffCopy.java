package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;

/**
 * Copies debuffs from a source (player or pet) onto an enemy for the Conductive and Rabid
 * enchantments. There is no generic enemy addEffect, so each debuff type dispatches to its own
 * CombatEntity.stack* method by hand. Types with no enemy-side apply (WEAKNESS, MINING_FATIGUE,
 * LEVITATION, DARKNESS) are silently skipped: they cannot be represented on an enemy.
 */
public final class DebuffCopy {

    private DebuffCopy() {}

    /** Whether this debuff has an enemy-side apply and can therefore be copied. */
    public static boolean copyableToEnemy(EffectType type) {
        return switch (type) {
            case POISON, BURNING, WITHER, SLOWNESS, BLEEDING, BLINDNESS, SOAKED, CONFUSION -> true;
            default -> false;
        };
    }

    /**
     * Apply one debuff to the enemy, dispatching by type. {@code amplifier} and remaining
     * {@code duration} come from the source's stored effect. Called only for types where
     * {@link #copyableToEnemy} is true.
     *
     * <p>Two of the enemy {@code stack*} methods take an irregular second argument, so the copy
     * maps the source amplifier onto them deliberately:
     * <ul>
     *   <li>SLOWNESS: {@code stackSlowness(turns, penaltyIncrease)} wants a movement PENALTY, not an
     *       amplifier. The player stores Slowness at amplifier N and suffers {@code 1 + N} speed
     *       lost (CombatEffects.getSpeedPenalty), so the copy passes {@code amplifier + 1} - the
     *       enemy loses exactly the movement the source was losing.
     *   <li>BLEEDING: {@code stackBleed(stacks)} takes STACKS, not turns. The player stores bleed as
     *       an amplifier where {@code stacks = amplifier + 1} (CombatEffects ticks bleed at
     *       {@code amplifier + 1}), so the copy passes {@code amplifier + 1}.
     * </ul>
     */
    public static void applyToEnemy(CombatEntity enemy, EffectType type, int amplifier, int duration) {
        switch (type) {
            case POISON -> enemy.stackPoison(duration, amplifier);
            case BURNING -> enemy.stackBurning(duration, amplifier);
            case WITHER -> enemy.stackWither(duration, amplifier);
            case SLOWNESS -> enemy.stackSlowness(duration, amplifier + 1); // 2nd arg is a movement penalty
            case BLEEDING -> enemy.stackBleed(amplifier + 1);              // bleed is stacks, not turns
            case BLINDNESS -> enemy.stackBlinded(duration);
            case SOAKED -> enemy.stackSoaked(duration, amplifier);
            case CONFUSION -> enemy.stackConfusion(duration, amplifier);
            default -> { /* not copyable; caller filtered via copyableToEnemy */ }
        }
    }
}
