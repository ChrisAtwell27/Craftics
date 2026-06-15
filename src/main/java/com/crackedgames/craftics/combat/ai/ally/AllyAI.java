package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;

import java.util.List;

/**
 * Combat AI for a friendly ally - the ally-side parallel to {@code EnemyAI}.
 *
 * <p>An {@code AllyAI} decides one ally's turn: given the ally, the arena, and
 * the current combatant list, it returns the {@link EnemyAction} the ally should
 * take. Allies hunt enemies, so the implementation is responsible for picking a
 * target from {@code combatants} (skipping entries where {@code isAlly()} is true).
 *
 * @since 0.2.0
 */
public interface AllyAI {

    /**
     * Decide this ally's action for the current turn.
     *
     * <p>For an attack action, pass the ally's base {@code self.getAttackPower()}
     * as the damage. The combat executor adds owner-equipment bonuses on top for
     * gear-scaling allies - implementations must not pre-apply those bonuses.
     *
     * @param self       the ally taking its turn
     * @param arena      the combat arena
     * @param combatants every combatant in the arena (allies and enemies); the AI
     *                   filters to live enemies itself
     * @return the action to execute; never {@code null} (use {@code EnemyAction.Idle}
     *         when the ally should do nothing)
     */
    EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants);
}
