package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Hoglin AI: Aggressive beast with bull rush and ground stomp.
 * - BULL RUSH: charges up to 3 tiles in a straight line, deals damage + knockback 2
 * - GROUND STOMP: if can't charge, damages all adjacent tiles (AoE around self)
 * - Speed 2, 2x2 size, always aggressive
 */
public class HoglinAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — knockback attack (tusks)
        if (dist == 1) {
            return new EnemyAction.AttackWithKnockback(self.getAttackPower(), 2);
        }

        // Try bull rush — charge in cardinal directions up to 3 tiles
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : directions) {
            List<GridPos> chargePath = buildChargePath(arena, myPos, dir[0], dir[1], 3);
            if (chargePath.isEmpty()) continue;

            // Check if player is at the end of or in the charge path
            GridPos chargeEnd = chargePath.get(chargePath.size() - 1);
            if (CombatEntity.minDistanceFromSizedEntity(chargeEnd, self.getSize(), playerPos) <= 1) {
                // Charge ends adjacent to player — charge + attack with knockback
                return new EnemyAction.MoveAndAttackWithKnockback(
                    new ArrayList<>(chargePath), self.getAttackPower() + 1, 2);
            }
        }

        // Can't charge into player — try to get closer (size-aware)
        int size = self.getSize();
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed(), size);
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPathSized(arena, myPos, target, self.getMoveSpeed(), self, size);
        if (!path.isEmpty()) {
            GridPos endPos = path.get(path.size() - 1);
            if (CombatEntity.minDistanceFromSizedEntity(endPos, size, playerPos) <= 1) {
                return new EnemyAction.MoveAndAttackWithKnockback(path, self.getAttackPower(), 2);
            }
            return new EnemyAction.Move(path);
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    private List<GridPos> buildChargePath(GridArena arena, GridPos start, int dx, int dz, int maxLen) {
        // Use size-aware footprint check for 2x2 hoglin
        int size = 2;
        List<GridPos> path = new ArrayList<>();
        GridPos current = start;
        for (int i = 0; i < maxLen; i++) {
            GridPos next = new GridPos(current.x() + dx, current.z() + dz);
            if (!AIUtils.canPlaceFootprint(arena, next, size)) break;
            path.add(next);
            current = next;
        }
        return path;
    }
}
