package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Default ally combat AI: a melee fighter. Retreats when badly wounded ({@code <=25%}
 * HP); otherwise scores every live enemy — closer is better, with bonuses for
 * threats near the player and for wounded enemies — then closes on the best
 * target, moving and striking in the same turn when the approach ends in range.
 *
 * @since 0.2.0
 */
public class MeleeAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        GridPos allyPos = self.getGridPos();

        // RETREAT when badly wounded.
        if (AllyTargeting.lowHp(self, 0.25f)) {
            CombatEntity nearest = AllyTargeting.nearestEnemy(allyPos, combatants);
            if (nearest != null) {
                EnemyAction flee = AllyTargeting.fleeFrom(self, arena, nearest);
                if (flee != null) return flee;
            }
            return new EnemyAction.Idle(); // cower
        }

        // ATTACK: pick the best target by priority scoring.
        GridPos playerPos = arena.getPlayerGridPos();
        CombatEntity target = null;
        int bestScore = Integer.MIN_VALUE;
        int targetDist = Integer.MAX_VALUE;
        for (CombatEntity e : combatants) {
            if (!e.isAlive() || e.isAlly()) continue;
            int d = AllyTargeting.distanceToTarget(e, arena, allyPos);
            int dToPlayer = AllyTargeting.distanceToTarget(e, arena, playerPos);
            int score = -d;
            if (dToPlayer <= 2) score += 3;                        // protect the player
            if (e.getCurrentHp() <= e.getMaxHp() / 2) score += 2;  // finish the wounded
            if (score > bestScore || (score == bestScore && d < targetDist)) {
                bestScore = score;
                targetDist = d;
                target = e;
            }
        }

        if (target == null) {
            return new EnemyAction.Idle();
        }

        // advance() attacks in place when in range, or moves-and-attacks in one turn.
        return AllyTargeting.advance(self, arena, target);
    }
}
