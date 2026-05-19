package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;

import java.util.List;

/**
 * Flying ally AI — a fast, fearless harasser (parrot, bee, bat, allay). Dives on
 * the weakest enemy to pick off wounded targets and never retreats; its strength
 * is its high movement speed letting it reach the backline.
 *
 * @since 0.3.0
 */
public class FlyerAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        CombatEntity prey = AllyTargeting.weakestEnemy(combatants);
        if (prey == null) return new EnemyAction.Idle();
        return AllyTargeting.advance(self, arena, prey);
    }
}
