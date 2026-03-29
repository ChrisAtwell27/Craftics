package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Spider AI: 2x2 ambush predator with ceiling drop mechanic.
 * - Speed 2 (slower ground movement)
 * - If too far to pounce, shoots a web upward and ascends to the ceiling
 * - Waits 1 turn on the ceiling (off-grid, invisible)
 * - Drops within 2 tiles of the player on the next turn, attacking on landing
 * - If close enough, pounces or melees normally
 */
public class SpiderAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // If on the ceiling, drop near the player
        if (self.isOnCeiling()) {
            GridPos dropTarget = findDropTarget(arena, playerPos, 2);
            if (dropTarget != null) {
                return new EnemyAction.CeilingDrop(dropTarget, self.getAttackPower());
            }
            // No valid drop target — stay on ceiling another turn
            return new EnemyAction.Idle();
        }

        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — bite attack
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Pounce range (up to 3 tiles) — leap to adjacent tile
        if (dist <= 3) {
            GridPos pounceTarget = findPounceTarget(arena, myPos, playerPos);
            if (pounceTarget != null) {
                return new EnemyAction.Pounce(pounceTarget, self.getAttackPower());
            }
        }

        // Can walk to player this turn? Move + attack (size-aware pathfinding)
        int size = self.getSize();
        GridPos adjTarget = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed(), size);
        if (adjTarget != null) {
            List<GridPos> path = Pathfinding.findPathSized(arena, myPos, adjTarget,
                self.getMoveSpeed(), self, size);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (CombatEntity.minDistanceFromSizedEntity(endPos, size, playerPos) <= 1) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                // Can get closer but not adjacent — still try walking
                if (dist <= self.getMoveSpeed() + 3) {
                    return new EnemyAction.Move(path);
                }
            }
        }

        // Too far to reach — ascend to ceiling
        return new EnemyAction.CeilingAscend();
    }

    private GridPos findPounceTarget(GridArena arena, GridPos from, GridPos playerPos) {
        int size = 2; // spider is 2x2
        GridPos[] dirs = {
            new GridPos(0, 1), new GridPos(0, -1),
            new GridPos(1, 0), new GridPos(-1, 0)
        };
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos dir : dirs) {
            GridPos landing = new GridPos(playerPos.x() + dir.x(), playerPos.z() + dir.z());
            if (!AIUtils.canPlaceFootprint(arena, landing, size)) continue;
            int pDist = from.manhattanDistance(landing);
            if (pDist <= 3 && pDist > 0 && pDist < bestDist) {
                bestDist = pDist;
                best = landing;
            }
        }
        return best;
    }

    private GridPos findDropTarget(GridArena arena, GridPos playerPos, int radius) {
        int size = 2; // spider is 2x2
        List<GridPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                GridPos pos = new GridPos(playerPos.x() + dx, playerPos.z() + dz);
                if (!AIUtils.canPlaceFootprint(arena, pos, size)) continue;
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) return null;
        // Prefer tiles adjacent to the player for immediate attack
        for (GridPos c : candidates) {
            if (CombatEntity.minDistanceFromSizedEntity(c, size, playerPos) <= 1) return c;
        }
        Collections.shuffle(candidates);
        return candidates.get(0);
    }
}
