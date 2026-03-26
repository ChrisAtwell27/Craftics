package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Pillager AI: Tactical crossbow raider.
 * - CROSSBOW VOLLEY: fires at range 4 (any direction, not just cardinal)
 * - REPOSITION: moves then shoots in same turn — never wastes a turn
 * - RETREAT AND FIRE: if player gets within 2, backs up and shoots
 * - Smart positioning — tries to maintain range 3-4
 */
public class PillagerAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // RETREAT AND FIRE: if too close, back up
        if (dist <= 2) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (endPos.manhattanDistance(playerPos) <= 4) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
            // Can't retreat, shoot anyway
            return new EnemyAction.RangedAttack(self.getAttackPower(), "crossbow");
        }

        // In range — fire crossbow
        if (dist <= 4) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "crossbow");
        }

        // Out of range — move to get within 3-4 tiles
        GridPos shotPos = findShotPosition(self, arena, playerPos);
        if (shotPos != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, shotPos, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= 4) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    private GridPos findShotPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int dx = -self.getMoveSpeed(); dx <= self.getMoveSpeed(); dx++) {
            for (int dz = -self.getMoveSpeed(); dz <= self.getMoveSpeed(); dz++) {
                if (Math.abs(dx) + Math.abs(dz) > self.getMoveSpeed()) continue;
                if (dx == 0 && dz == 0) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                if (distToPlayer > 4) continue;
                // Prefer range 3-4
                int score = distToPlayer >= 3 ? 20 : distToPlayer * 3;
                if (distToPlayer <= 1) score -= 15;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }
}
