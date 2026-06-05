package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;

/**
 * Optional on-kill effect for an ally type — runs once when a registered ally
 * lands a killing blow on a victim. Used by compat modules for kill-triggered
 * perks (e.g. the barrel golem "bartering" a bonus loot roll from its kills).
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface AllyKillHook {
    /**
     * @param killerAlly the ally that landed the kill
     * @param victim     the dying victim (about to go through the death pipeline)
     */
    void onKill(CombatEntity killerAlly, CombatEntity victim);
}
