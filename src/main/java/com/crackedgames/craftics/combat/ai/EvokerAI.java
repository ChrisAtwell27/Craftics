package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Evoker AI: Illager spellcaster with fang attacks.
 * - FANG SNAP: ranged attack at 3-4 tiles (evoker fangs)
 * - RETREAT: kites backward when player closes in
 * - REPOSITION: moves then attacks in same turn
 * - Prefers to maintain range 3-4
 */
public class EvokerAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Too close — retreat and cast
        if (dist <= 2) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (endPos.manhattanDistance(playerPos) <= self.getRange()) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
            // Can't retreat — cast at point blank
            return new EnemyAction.RangedAttack(self.getAttackPower(), "fangs");
        }

        // In range — cast fangs
        if (dist <= self.getRange()) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "fangs");
        }

        // Out of range — reposition to get within casting distance
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -self.getMoveSpeed(); dx <= self.getMoveSpeed(); dx++) {
            for (int dz = -self.getMoveSpeed(); dz <= self.getMoveSpeed(); dz++) {
                if (Math.abs(dx) + Math.abs(dz) > self.getMoveSpeed()) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                if (distToPlayer < 3 || distToPlayer > self.getRange()) continue;
                int distToSelf = myPos.manhattanDistance(candidate);
                if (distToSelf < bestDist) {
                    bestDist = distToSelf;
                    best = candidate;
                }
            }
        }

        if (best != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, best, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= self.getRange()) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }
}
