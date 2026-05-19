package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;

import java.util.List;

/**
 * Tank ally AI — a sturdy body-blocker (iron golem, turtle, goat). Always charges
 * the enemy closest to the player to soak hits meant for them, and never flees
 * no matter how low its HP runs.
 *
 * @since 0.3.0
 */
public class TankAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        CombatEntity threat = AllyTargeting.nearestEnemyToPlayer(arena, combatants);
        if (threat == null) return new EnemyAction.Idle();
        return AllyTargeting.advance(self, arena, threat);
    }
}
