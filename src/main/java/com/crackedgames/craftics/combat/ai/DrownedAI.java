package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Drowned AI: Versatile aquatic fighter — melee + ranged trident.
 * - TRIDENT THROW: ranged attack at range 3 if cardinal LOS (move + throw same turn)
 * - Falls back to melee rush if no LOS
 * - Repositions to get a trident shot angle, then throws
 */
public class DrownedAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — melee attack
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Trident throw if cardinal LOS at range 3
        if (AIUtils.hasCardinalLOS(arena, myPos, playerPos, 3)) {
            return new EnemyAction.RangedAttack(self.getAttackPower() + 1, "trident");
        }

        // Try to move to get cardinal LOS for a trident throw
        for (int dx = -self.getMoveSpeed(); dx <= self.getMoveSpeed(); dx++) {
            for (int dz = -self.getMoveSpeed(); dz <= self.getMoveSpeed(); dz++) {
                if (Math.abs(dx) + Math.abs(dz) > self.getMoveSpeed()) continue;
                if (dx == 0 && dz == 0) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;
                if (AIUtils.hasCardinalLOS(arena, candidate, playerPos, 3)) {
                    List<GridPos> path = Pathfinding.findPath(arena, myPos, candidate, self.getMoveSpeed(), self);
                    if (!path.isEmpty()) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower() + 1);
                    }
                }
            }
        }

        // No trident shot available — rush melee
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) <= 1) {
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
        }
        return new EnemyAction.Move(path);
    }
}
