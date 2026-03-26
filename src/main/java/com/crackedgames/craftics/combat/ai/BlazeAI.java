package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Blaze AI: Hovering fire artillery.
 * - FIREBALL BARRAGE: shoots at range 4 (doesn't need cardinal LOS — any direction)
 * - HOVER RETREAT: if player gets within 2, flies backward and shoots
 * - Applies burning on hit (handled in CombatManager)
 * - FIRE SHIELD: takes reduced damage when above 50% HP (high defense)
 */
public class BlazeAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();

        // HOVER RETREAT: if too close, fly backward and fire
        if (dist <= 2) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
            }
            // Can't retreat — fire at point blank
            return new EnemyAction.RangedAttack(self.getAttackPower(), "fire");
        }

        // In range — shoot fireball (any direction, not just cardinal)
        if (dist <= range) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "fire");
        }

        // Out of range — reposition to get within range while staying distant
        GridPos shotPos = findShotPosition(self, arena, playerPos);
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

    private GridPos findShotPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
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
                // Prefer being 3-4 tiles away (safe range)
                int score = Math.min(distToPlayer, 4) * 3;
                if (distToPlayer <= 1) score -= 10;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }
}
