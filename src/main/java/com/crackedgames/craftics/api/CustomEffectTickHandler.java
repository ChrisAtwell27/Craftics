package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;

/**
 * Optional scripted per-turn logic for a {@link CustomEffectDef custom status effect}.
 *
 * <p>Called once per round for each combatant carrying the effect, after the effect's
 * declarative {@link CustomEffectDef#hpChangePerTurn() hpChangePerTurn} has been applied.
 * Use it for behavior a flat HP change cannot express - spreading to neighbors, scaling
 * with the target's state, spawning particles, and so on.
 *
 * <p>Effects that only need a flat per-turn HP change do not need a handler at all.
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface CustomEffectTickHandler {

    /**
     * Run one tick of the effect on {@code entity}.
     *
     * @param entity         the combatant carrying the effect
     * @param amplifier      the effect's current amplifier ({@code 0} = level I)
     * @param turnsRemaining turns left including this one
     */
    void onTick(CombatEntity entity, int amplifier, int turnsRemaining);
}
