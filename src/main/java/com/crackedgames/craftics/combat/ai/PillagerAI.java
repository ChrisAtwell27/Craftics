package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Pillager AI: Tactical crossbow raider.
 * - CROSSBOW VOLLEY: fires at its configured range (any direction, not just cardinal)
 * - REPOSITION: moves then shoots in same turn - never wastes a turn
 * - RETREAT AND FIRE: backs up when any threat (player or ally pet) closes
 *   to within 2 tiles, snapping off a shot if the target stays in range
 * - Smart positioning - tries to keep its target near max range and stays
 *   off hazard tiles
 */
public class PillagerAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        // Honor the per-biome range from the enemy entry instead of a
        // hardcoded 4 - forest pillagers are registered at range 3.
        int range = Math.max(1, self.getRange());

        List<GridPos> threats = AIUtils.threatPositions(arena, playerPos);

        // RETREAT AND FIRE: if anything is too close, back up
        if (AIUtils.minThreatDistance(myPos, threats) <= 2) {
            GridPos fleeTarget = AIUtils.bestRetreatTile(self, arena, threats);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (endPos.manhattanDistance(playerPos) <= range) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
            // Can't retreat, shoot anyway
            return new EnemyAction.RangedAttack(self.getAttackPower(), "crossbow");
        }

        // In range - fire crossbow
        if (dist <= range) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "crossbow");
        }

        // Out of range - move to a firing position
        GridPos shotPos = findShotPosition(self, arena, playerPos, threats, range);
        if (shotPos != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, shotPos, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= range) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    private GridPos findShotPosition(CombatEntity self, GridArena arena, GridPos playerPos,
                                     List<GridPos> threats, int range) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;

        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, myPos, self.getMoveSpeed(), self)) {
            if (candidate.equals(myPos)) continue;

            int distToPlayer = candidate.manhattanDistance(playerPos);
            if (distToPlayer > range) continue;
            // Prefer firing from near max range, well clear of every threat
            int score = distToPlayer >= range - 1 ? 20 : distToPlayer * 3;
            if (AIUtils.minThreatDistance(candidate, threats) <= 1) score -= 15;
            if (AIUtils.isHazardTile(arena, candidate)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
