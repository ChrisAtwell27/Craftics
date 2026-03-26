package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cat AI: Runs away from the player unless the player is holding a fish.
 * If player holds fish, the cat approaches. If attacked, becomes agro (untamable).
 */
public class CatAI implements EnemyAI {

    private static final Set<String> FISH_ITEMS = Set.of(
        "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish",
        "minecraft:cooked_cod", "minecraft:cooked_salmon"
    );

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);

        // If attacked, become agro permanently
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged()) {
            self.setEnraged(true);
        }

        // AGRO: attack player
        if (self.isEnraged()) {
            if (dist <= 1) {
                return new EnemyAction.Attack(self.getAttackPower());
            }
            GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (target != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (endPos.manhattanDistance(playerPos) <= 1) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
            return AIUtils.seekOrWander(self, arena, playerPos);
        }

        // Check if player is holding fish
        boolean holdingFish = FISH_ITEMS.contains(arena.getPlayerHeldItemId());

        if (holdingFish) {
            // Approach player (drawn to fish)
            if (dist <= 1) {
                // Already adjacent — just sit and wait happily
                return new EnemyAction.Idle();
            }
            GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (target != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
                if (!path.isEmpty()) return new EnemyAction.Move(path);
            }
            return new EnemyAction.Idle();
        }

        // Not holding fish — flee if player is within 3 blocks
        if (dist <= 3) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, 2);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, 2, self);
                if (!path.isEmpty()) return new EnemyAction.Flee(path);
            }
        }

        // Otherwise wander (configurable chance)
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
