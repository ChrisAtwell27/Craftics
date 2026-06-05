package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;

/**
 * Optional per-round effect for an ally type — runs once per round for each living
 * ally whose {@code AllyEntry} defines one. Used by the honey golem (summons a bee
 * each round) and the hay golem (mends the lowest-HP ally each round); exposed so
 * addon allies can have per-round quirks.
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface AllyRoundHook {
    /**
     * @param self the living ally
     * @param ctx  controlled access to combat state (summon, heal, list allies, message)
     */
    void onRound(CombatEntity self, AllyCombatContext ctx);
}
