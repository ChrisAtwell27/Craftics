package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;

import java.util.List;

/**
 * Flying ally AI — a fast, fearless harasser (parrot, bee, bat, allay). Dives
 * on the weakest enemy to pick off wounded targets and never retreats; its
 * strength is its high movement speed letting it reach the backline. Given the
 * choice, it takes a kill it can actually finish THIS turn over a marginally
 * weaker enemy across the map.
 *
 * @since 0.3.0
 */
public class FlyerAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        // Kill-secure first: the weakest enemy we can both reach and finish.
        int reach = self.getMoveSpeed() + Math.max(1, self.getRange());
        CombatEntity executable = null;
        for (CombatEntity e : combatants) {
            if (!e.isAlive() || e.isAlly()) continue;
            if (e.getCurrentHp() > self.getAttackPower()) continue;
            if (AllyTargeting.distanceToTarget(e, arena, self.getGridPos()) > reach) continue;
            if (executable == null || e.getCurrentHp() < executable.getCurrentHp()) {
                executable = e;
            }
        }
        CombatEntity prey = executable != null ? executable : AllyTargeting.weakestEnemy(combatants);
        if (prey == null) return new EnemyAction.Idle();
        return AllyTargeting.advance(self, arena, prey);
    }
}
