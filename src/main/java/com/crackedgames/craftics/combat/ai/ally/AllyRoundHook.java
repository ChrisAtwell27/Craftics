package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.level.EnvironmentStyle;

/**
 * Optional per-round effect for an ally type — runs once per round for each living
 * ally whose {@code AllyEntry} defines one. Used by Part B for the snow golem's
 * hot-biome chip damage; exposed so addon allies can have environmental quirks.
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface AllyRoundHook {
    /**
     * @param self        the living ally
     * @param environment the arena's environment style
     */
    void onRound(CombatEntity self, EnvironmentStyle environment);
}
