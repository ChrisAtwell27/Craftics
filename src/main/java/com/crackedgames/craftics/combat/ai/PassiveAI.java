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
 * Passive mob AI (farm animals): 50/50 wander 1-2 blocks or idle.
 * If hit by the player, immediately flees 2 blocks away.
 */
public class PassiveAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Hit reaction: flee 2 blocks away from player immediately
        if (self.wasDamagedSinceLastTurn()) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, self.getGridPos(), playerPos, 2);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, self.getGridPos(), fleeTarget, 2, self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Flee(path);
                }
            }
            // Cornered — just idle
            return new EnemyAction.Idle();
        }

        // Configurable wander chance
        if (ThreadLocalRandom.current().nextFloat() < com.crackedgames.craftics.CrafticsMod.CONFIG.passiveMobWanderChance()) {
            return tryWander(self, arena);
        }
        return new EnemyAction.Idle();
    }

    /**
     * Wander 1-2 blocks in a random walkable direction.
     */
    private EnemyAction tryWander(CombatEntity self, GridArena arena) {
        GridPos myPos = self.getGridPos();
        int wanderDist = 1 + ThreadLocalRandom.current().nextInt(2); // 1 or 2

        // Collect all valid adjacent directions, shuffled for randomness
        List<int[]> directions = new ArrayList<>(List.of(
            new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1}
        ));
        Collections.shuffle(directions, ThreadLocalRandom.current());

        for (int[] dir : directions) {
            // Try the full wander distance first, then fall back to 1
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

        // No valid wander — just idle
        return new EnemyAction.Idle();
    }
}
