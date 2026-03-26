package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Ghast AI: Long-range fireball artillery. Slow but devastating.
 * - FIREBALL: range 5-6 (any direction, not just cardinal)
 * - FLEE: panics if player gets within 3 tiles, retreats
 * - Applies burning on hit
 * - Speed 1 = nearly stationary, but huge range compensates
 */
public class GhastAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();

        // PANIC: if player too close, flee desperately
        if (dist <= 3) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, 1);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, 1, self);
                if (!path.isEmpty()) {
                    // Flee and shoot if possible
                    GridPos endPos = path.get(path.size() - 1);
                    if (endPos.manhattanDistance(playerPos) <= range) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
            // Can't flee — fire at close range
            return new EnemyAction.RangedAttack(self.getAttackPower(), "fireball");
        }

        // In range — bombard with fireballs
        if (dist <= range) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "fireball");
        }

        // Out of range — slowly reposition
        GridPos shotPos = findDistantShotPosition(self, arena, playerPos);
        if (shotPos != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, shotPos, 1, self);
            if (!path.isEmpty()) {
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    private GridPos findDistantShotPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 && dz != 0) continue;
                if (dx == 0 && dz == 0) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                // Prefer maximum distance while staying in range
                int score = distToPlayer * 3 + (distToPlayer <= range ? 20 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }
}
