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
 * Rabbit AI: Skittish — always flees if player is within 2 blocks.
 * Otherwise wanders 1-2 blocks randomly like a farm animal.
 */
public class RabbitAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);

        // Always flee if player is within 2 blocks
        if (dist <= 2) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, 2);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, 2, self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Flee(path);
                }
            }
        }

        // Also flee if hit (in case player used ranged attack from > 2 blocks)
        if (self.wasDamagedSinceLastTurn()) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, 2);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, 2, self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Flee(path);
                }
            }
        }

        // Configurable wander chance
        if (ThreadLocalRandom.current().nextFloat() < com.crackedgames.craftics.CrafticsMod.CONFIG.passiveMobWanderChance()) {
            return tryWander(self, arena);
        }
        return new EnemyAction.Idle();
    }

    private EnemyAction tryWander(CombatEntity self, GridArena arena) {
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
                if (tile == null || !tile.isWalkable()) continue;

                List<GridPos> path = Pathfinding.findPath(arena, myPos, target, d, self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Move(path);
                }
            }
        }
        return new EnemyAction.Idle();
    }
}
