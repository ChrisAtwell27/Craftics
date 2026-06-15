package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Stray AI: Ice skeleton - ranged kiter that applies Slowness on hit.
 * - FROST ARROW: shoots at range, slows the player (-1 movement next turn)
 * - KITE: actively retreats when ANY threat (player or ally pet) gets within
 *   2 tiles, then shoots
 * - Prefers maximum distance - more cautious than regular skeleton - and
 *   never ends its move on a hazard tile
 * - Extremely annoying in groups (stacking slowness)
 */
public class StrayAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();

        List<GridPos> threats = AIUtils.threatPositions(arena, playerPos);

        // KITE: if any threat is too close, retreat then shoot
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
            // Can't flee - shoot at close range
            if (dist <= range) {
                return new EnemyAction.RangedAttack(self.getAttackPower(), "frost_arrow");
            }
        }

        // In range - frost arrow
        if (dist <= range) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "frost_arrow");
        }

        // Out of range - reposition
        GridPos shotPos = findKitePosition(self, arena, playerPos, threats);
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

    /**
     * A reachable tile that puts the target inside firing range while staying
     * as far as possible from every threat.
     */
    private GridPos findKitePosition(CombatEntity self, GridArena arena, GridPos playerPos,
                                     List<GridPos> threats) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();

        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, myPos, self.getMoveSpeed(), self.getSize(), self)) {
            if (candidate.equals(myPos)) continue;

            int distToPlayer = candidate.manhattanDistance(playerPos);
            if (distToPlayer > range) continue;
            // Prefer maximum distance from every threat while staying in range
            int score = AIUtils.minThreatDistance(candidate, threats) * 5;
            if (distToPlayer <= 1) score -= 20;
            if (AIUtils.isHazardTile(arena, candidate)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
