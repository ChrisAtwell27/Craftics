package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;

/**
 * Optional on-death effect for an ally type — runs once when a living ally of a
 * registered type dies. Used by compat modules for split/spawn behaviours (e.g.
 * the slime golem splitting into two small slime golems on death).
 *
 * <p>Death hooks never touch the {@code CombatManager} directly — they go through
 * the shared {@link AllyCombatContext}, the same controlled view handed to
 * {@link AllyRoundHook}s.
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface AllyDeathHook {
    /**
     * @param self the dying ally
     * @param ctx  controlled access to combat state (summon, heal, list allies, message)
     */
    void onDeath(CombatEntity self, AllyCombatContext ctx);
}
