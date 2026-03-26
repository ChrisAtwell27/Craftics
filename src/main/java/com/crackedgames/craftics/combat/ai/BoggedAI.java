package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Bogged AI: Swamp skeleton variant — poison arrow kiter.
 * - POISON ARROW: ranged attack that applies poison (damage over time concept)
 * - KITE: retreats when player gets close, like StrayAI but more aggressive
 * - AMBUSH: if far away, advances to range 3 then starts shooting
 * - Less cautious than Stray — will hold ground at range 2 instead of fleeing
 */
public class BoggedAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();

        // Adjacent — desperate melee + back away
        if (dist <= 1) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, self.getMoveSpeed());
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
            // Can't flee — shoot at point blank
            return new EnemyAction.RangedAttack(self.getAttackPower(), "poison_arrow");
        }

        // In range — poison arrow (holds ground more aggressively than stray)
        if (dist <= range) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "poison_arrow");
        }

        // Out of range — advance to shooting position
        GridPos shotPos = findShootPosition(self, arena, playerPos);
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

    private GridPos findShootPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();

        for (int dx = -self.getMoveSpeed(); dx <= self.getMoveSpeed(); dx++) {
            for (int dz = -self.getMoveSpeed(); dz <= self.getMoveSpeed(); dz++) {
                if (Math.abs(dx) + Math.abs(dz) > self.getMoveSpeed()) continue;
                if (dx == 0 && dz == 0) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                if (distToPlayer > range) continue;

                // Prefer range 2-3 (close enough to hit, far enough to be safe)
                int score = distToPlayer * 3;
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
