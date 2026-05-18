package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Default ally combat AI: a melee fighter. Retreats when badly wounded, otherwise
 * advances on and attacks the best enemy target. This is the behavior every tamed
 * pet used before the ally framework existed — extracted verbatim from the old
 * {@code CombatManager.handleAllyTurn()}.
 *
 * @since 0.2.0
 */
public class MeleeAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        GridPos allyPos = self.getGridPos();
        boolean lowHp = self.getMaxHp() > 0
            && (float) self.getCurrentHp() / self.getMaxHp() <= 0.25f;

        if (lowHp) {
            // RETREAT: move away from the nearest enemy.
            CombatEntity nearestEnemy = null;
            int nearestDist = Integer.MAX_VALUE;
            for (CombatEntity e : combatants) {
                if (!e.isAlive() || e.isAlly()) continue;
                int d = Math.abs(e.getGridPos().x() - allyPos.x())
                    + Math.abs(e.getGridPos().z() - allyPos.z());
                if (d < nearestDist) {
                    nearestDist = d;
                    nearestEnemy = e;
                }
            }
            if (nearestEnemy != null) {
                int dx = Integer.signum(allyPos.x() - nearestEnemy.getGridPos().x());
                int dz = Integer.signum(allyPos.z() - nearestEnemy.getGridPos().z());
                GridPos retreatTarget = new GridPos(allyPos.x() + dx * 2, allyPos.z() + dz * 2);
                List<GridPos> path = Pathfinding.findPath(
                    arena, allyPos, retreatTarget, self.getMoveSpeed(), false);
                if (path != null && !path.isEmpty()) {
                    return new EnemyAction.Flee(path);
                }
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
            int d = e.minDistanceTo(allyPos);
            int dToPlayer = e.minDistanceTo(playerPos);
            int score = -d;
            if (dToPlayer <= 2) score += 3;       // protect the player
            if (e.getCurrentHp() <= e.getMaxHp() / 2) score += 2; // finish wounded
            if (score > bestScore || (score == bestScore && d < targetDist)) {
                bestScore = score;
                targetDist = d;
                target = e;
            }
        }

        if (target == null) {
            return new EnemyAction.Idle();
        }

        if (targetDist <= self.getRange()) {
            // In range — attack in place. Owner-gear bonus is added by the executor.
            return new EnemyAction.MoveAndAttackMob(
                List.of(), target.getEntityId(), self.getAttackPower());
        }

        // Move toward the target.
        List<GridPos> path = Pathfinding.findPath(
            arena, allyPos, target.getGridPos(), self.getMoveSpeed(), false);
        if (path != null && !path.isEmpty()) {
            return new EnemyAction.Move(path);
        }
        // Can't path directly — seek the closest reachable tile.
        GridPos closest = Pathfinding.findClosestReachableTo(
            arena, allyPos, target.getGridPos(), self.getMoveSpeed(), self);
        if (closest != null && !closest.equals(allyPos)) {
            List<GridPos> seekPath = Pathfinding.findPath(
                arena, allyPos, closest, self.getMoveSpeed(), false);
            if (seekPath != null && !seekPath.isEmpty()) {
                return new EnemyAction.Move(seekPath);
            }
        }
        return new EnemyAction.Idle();
    }
}
