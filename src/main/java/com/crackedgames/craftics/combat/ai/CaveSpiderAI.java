package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cave Spider AI: Fast venomous ambush predator with ceiling drop.
 * - Speed 3 (faster than regular spider)
 * - Applies poison on hit
 * - Same ceiling mechanic as SpiderAI but faster and more aggressive
 * - Pounce range 2 (shorter than regular spider's 3)
 * - Drops within 2 tiles of player from ceiling
 */
public class CaveSpiderAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // If on the ceiling, drop near the player
        if (self.isOnCeiling()) {
            GridPos dropTarget = findDropTarget(arena, playerPos, 2);
            if (dropTarget != null) {
                return new EnemyAction.CeilingDrop(dropTarget, self.getAttackPower());
            }
            return new EnemyAction.Idle();
        }

        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — venomous bite
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Pounce range (up to 2 tiles)
        if (dist <= 2) {
            GridPos pounceTarget = findPounceTarget(arena, myPos, playerPos, 2);
            if (pounceTarget != null) {
                return new EnemyAction.Pounce(pounceTarget, self.getAttackPower());
            }
        }

        // Can walk to player this turn? Move + attack
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;
        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (!path.isEmpty()) {
            GridPos endPos = path.get(path.size() - 1);
            if (endPos.manhattanDistance(playerPos) == 1) {
                return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
            }
            if (dist <= self.getMoveSpeed() + 2) {
                return new EnemyAction.Move(path);
            }
        }

        // Too far — ascend to ceiling
        return new EnemyAction.CeilingAscend();
    }

    private GridPos findPounceTarget(GridArena arena, GridPos from, GridPos playerPos, int pounceRange) {
        GridPos[] dirs = {
            new GridPos(0, 1), new GridPos(0, -1),
            new GridPos(1, 0), new GridPos(-1, 0)
        };
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos dir : dirs) {
            GridPos landing = new GridPos(playerPos.x() + dir.x(), playerPos.z() + dir.z());
            if (!arena.isInBounds(landing) || arena.isEnemyOccupied(landing)) continue;
            var tile = arena.getTile(landing);
            if (tile == null || !tile.isWalkable()) continue;
            int pDist = from.manhattanDistance(landing);
            if (pDist <= pounceRange && pDist > 0 && pDist < bestDist) {
                bestDist = pDist;
                best = landing;
            }
        }
        return best;
    }

    private GridPos findDropTarget(GridArena arena, GridPos playerPos, int radius) {
        List<GridPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                GridPos pos = new GridPos(playerPos.x() + dx, playerPos.z() + dz);
                if (!arena.isInBounds(pos) || arena.isOccupied(pos)) continue;
                var tile = arena.getTile(pos);
                if (tile == null || !tile.isWalkable()) continue;
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) return null;
        for (GridPos c : candidates) {
            if (c.manhattanDistance(playerPos) == 1) return c;
        }
        Collections.shuffle(candidates);
        return candidates.get(0);
    }
}
