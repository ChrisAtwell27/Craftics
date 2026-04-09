package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cod/Salmon AI: Fish — ALWAYS stays on water tiles.
 * Wanders randomly between water tiles, flees on water if hit.
 */
public class CodAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        // If hit, flee along water tiles
        if (self.wasDamagedSinceLastTurn()) {
            GridPos fleeTarget = findWaterFleeTarget(arena, myPos, playerPos);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, 2, self, false, false, true);
                if (!path.isEmpty() && allWater(arena, path)) {
                    return new EnemyAction.Flee(path);
                }
            }
        }

        // Configurable wander chance on water
        if (ThreadLocalRandom.current().nextFloat() < com.crackedgames.craftics.CrafticsMod.CONFIG.passiveMobWanderChance()) {
            return tryWanderOnWater(self, arena);
        }
        return new EnemyAction.Idle();
    }

    private EnemyAction tryWanderOnWater(CombatEntity self, GridArena arena) {
        GridPos myPos = self.getGridPos();
        int wanderDist = 1 + ThreadLocalRandom.current().nextInt(2);

        List<int[]> directions = new ArrayList<>(List.of(
            new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1}
        ));
        Collections.shuffle(directions, ThreadLocalRandom.current());

        for (int[] dir : directions) {
            for (int d = wanderDist; d >= 1; d--) {
                GridPos target = new GridPos(myPos.x() + dir[0] * d, myPos.z() + dir[1] * d);
                if (!arena.isInBounds(target) || arena.isOccupied(target)) continue;
                var tile = arena.getTile(target);
                if (tile == null || !tile.isWater()) continue;

                List<GridPos> path = Pathfinding.findPath(arena, myPos, target, d, self, false, false, true);
                if (!path.isEmpty() && allWater(arena, path)) {
                    return new EnemyAction.Move(path);
                }
            }
        }
        return new EnemyAction.Idle();
    }

    private GridPos findWaterFleeTarget(GridArena arena, GridPos self, GridPos threat) {
        int dx = Integer.signum(self.x() - threat.x());
        int dz = Integer.signum(self.z() - threat.z());
        if (dx == 0 && dz == 0) dx = 1; // default direction

        // Try fleeing away on water
        for (int dist = 2; dist >= 1; dist--) {
            GridPos target = new GridPos(self.x() + dx * dist, self.z() + dz * dist);
            if (arena.isInBounds(target) && !arena.isOccupied(target)) {
                var tile = arena.getTile(target);
                if (tile != null && tile.isWater()) return target;
            }
            // Try perpendicular
            GridPos perp1 = new GridPos(self.x() + dz * dist, self.z() + dx * dist);
            if (arena.isInBounds(perp1) && !arena.isOccupied(perp1)) {
                var tile = arena.getTile(perp1);
                if (tile != null && tile.isWater()) return perp1;
            }
            GridPos perp2 = new GridPos(self.x() - dz * dist, self.z() - dx * dist);
            if (arena.isInBounds(perp2) && !arena.isOccupied(perp2)) {
                var tile = arena.getTile(perp2);
                if (tile != null && tile.isWater()) return perp2;
            }
        }
        return null;
    }

    private boolean allWater(GridArena arena, List<GridPos> path) {
        for (GridPos pos : path) {
            var tile = arena.getTile(pos);
            if (tile == null || !tile.isWater()) return false;
        }
        return true;
    }
}
