package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Ranged ally AI — a kiter (llama spit, snow golem snowballs). Fires from its
 * full attack range and backs away when an enemy closes to melee, snapping off
 * a parting shot while it retreats. Flees outright when badly wounded. When out
 * of range it closes the gap and fires in the same turn.
 *
 * @since 0.3.0
 */
public class RangedAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        GridPos pos = self.getGridPos();
        int range = Math.max(1, self.getRange());

        CombatEntity target = AllyTargeting.nearestEnemy(pos, combatants);
        if (target == null) return new EnemyAction.Idle();

        int dist = AllyTargeting.distanceToTarget(target, arena, pos);
        boolean lowHp = AllyTargeting.lowHp(self, 0.25f);

        // Wounded, or an enemy has closed to melee — kite away from the threat.
        // The kite tile is scored over everything reachable this turn: gain as
        // much distance as possible, and (unless fleeing for our life) prefer
        // tiles that keep the target inside firing range for the parting shot.
        if (lowHp || dist <= 1) {
            GridPos targetAnchor = AllyTargeting.nearestTileOnTarget(target, arena, pos);
            GridPos retreat = null;
            int bestScore = Integer.MIN_VALUE;
            int currentDist = pos.manhattanDistance(targetAnchor);
            for (GridPos candidate : Pathfinding.getReachableTiles(
                    arena, pos, self.getMoveSpeed(), self.getSize(), self)) {
                int d = candidate.manhattanDistance(targetAnchor);
                if (d <= currentDist) continue;
                int score = d * 10;
                if (!lowHp && AllyTargeting.distanceToTarget(target, arena, candidate) <= range) {
                    score += 15; // keep the parting shot lined up
                }
                if (score > bestScore) {
                    bestScore = score;
                    retreat = candidate;
                }
            }
            if (retreat != null) {
                List<GridPos> path = AllyTargeting.pathTo(self, arena, retreat);
                if (path != null && !path.isEmpty()) {
                    GridPos end = path.get(path.size() - 1);
                    // Parting shot if the target stays in range and we aren't fleeing for our life.
                    if (!lowHp && AllyTargeting.distanceToTarget(target, arena, end) <= range) {
                        return new EnemyAction.MoveAndAttackMob(
                            path, target.getEntityId(), self.getAttackPower());
                    }
                    return new EnemyAction.Flee(path);
                }
            }
            // Cornered — bite back.
            return new EnemyAction.MoveAndAttackMob(
                List.of(), target.getEntityId(), self.getAttackPower());
        }

        // Already at a good firing distance — shoot without moving.
        if (dist <= range) {
            return new EnemyAction.MoveAndAttackMob(
                List.of(), target.getEntityId(), self.getAttackPower());
        }

        // Out of range — close just enough to fire, moving and shooting in one turn.
        // pathTo(target.getGridPos()) returns empty (target's tile is "blocked"
        // by the target), so route to the closest reachable tile and fire if it
        // lands within range.
        GridPos aim = AllyTargeting.nearestTileOnTarget(target, arena, pos);
        GridPos closest = Pathfinding.findClosestReachableTo(
            arena, pos, aim, self.getMoveSpeed(), self, self.getSize());
        if (closest != null && !closest.equals(pos)) {
            List<GridPos> seek = AllyTargeting.pathTo(self, arena, closest);
            if (seek != null && !seek.isEmpty()) {
                if (AllyTargeting.distanceToTarget(target, arena, closest) <= range) {
                    return new EnemyAction.MoveAndAttackMob(
                        seek, target.getEntityId(), self.getAttackPower());
                }
                return new EnemyAction.Move(seek);
            }
        }
        return new EnemyAction.Idle();
    }
}
