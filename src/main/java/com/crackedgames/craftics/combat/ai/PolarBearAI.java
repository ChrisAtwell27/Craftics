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
 * Polar Bear AI: Territorial - becomes permanently agro if player gets within 2 blocks.
 * Once agro, charges and mauls: a standing swat that knocks the target back a
 * tile (it's a polar bear). Otherwise wanders like a farm animal.
 */
public class PolarBearAI implements EnemyAI {
    /** Polar bears are neutral - only a threat once provoked (enraged). */
    @Override
    public boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return self.isEnraged();
    }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        // Size-aware: the bear is 2x2, so adjacency is measured from its whole
        // footprint (the old anchor-only distance let players stand "inside"
        // its melee reach without triggering the territorial agro).
        int dist = self.minDistanceTo(playerPos);
        int sizeX = self.getSizeX();
        int sizeZ = self.getSizeZ();

        // Become agro if player is within 2 blocks OR if hit
        if (!self.isEnraged() && (dist <= 2 || self.wasDamagedSinceLastTurn())) {
            self.setEnraged(true);
        }

        // AGRO: charge and maul - the swat sends the target sprawling a tile back
        if (self.isEnraged()) {
            if (dist <= 1) {
                return new EnemyAction.AttackWithKnockback(self.getAttackPower(), 1);
            }

            GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed(), sizeX, sizeZ);
            if (target != null) {
                List<GridPos> path = Pathfinding.findPathSized(arena, myPos, target, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (CombatEntity.minDistanceFromSizedEntity(endPos, sizeX, sizeZ, playerPos) <= 1) {
                        return new EnemyAction.MoveAndAttackWithKnockback(path, self.getAttackPower(), 1);
                    }
                    return new EnemyAction.Move(path);
                }
            }
            return AIUtils.seekOrWander(self, arena, playerPos);
        }

        // Not agro: configurable wander chance
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
                if (!path.isEmpty()) return new EnemyAction.Move(path);
            }
        }
        return new EnemyAction.Idle();
    }
}
