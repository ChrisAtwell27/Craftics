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
 * Axolotl AI: Stays in water. Attacks any hostile mob that is also in water.
 * Otherwise wanders on water tiles.
 */
public class AxolotlAI implements EnemyAI {

    // Passive mob type IDs — everything NOT in this set is considered hostile
    private static final Set<String> PASSIVE_MOBS = Set.of(
        "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken",
        "minecraft:rabbit", "minecraft:cod", "minecraft:salmon", "minecraft:parrot",
        "minecraft:bat", "minecraft:axolotl", "minecraft:llama", "minecraft:fox",
        "minecraft:wolf", "minecraft:polar_bear", "minecraft:panda", "minecraft:goat",
        "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:cat",
        "minecraft:bee", "minecraft:ocelot", "minecraft:turtle", "minecraft:frog"
    );

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        // Hunt hostile mobs that are on water tiles
        CombatEntity waterTarget = findHostileOnWater(self, arena);
        if (waterTarget != null) {
            GridPos targetPos = waterTarget.getGridPos();
            int dist = myPos.manhattanDistance(targetPos);

            if (dist <= 1) {
                return new EnemyAction.AttackMob(waterTarget.getEntityId(), self.getAttackPower());
            }

            // Move toward target on water only
            GridPos moveTarget = findWaterAdjacentTo(arena, myPos, targetPos, self.getMoveSpeed());
            if (moveTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, moveTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty() && allWater(arena, path)) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (endPos.manhattanDistance(targetPos) <= 1) {
                        return new EnemyAction.MoveAndAttackMob(path, waterTarget.getEntityId(), self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
        }

        // Wander on water (configurable chance)
        if (ThreadLocalRandom.current().nextFloat() < com.crackedgames.craftics.CrafticsMod.CONFIG.passiveMobWanderChance()) {
            return tryWanderOnWater(self, arena);
        }
        return new EnemyAction.Idle();
    }

    private CombatEntity findHostileOnWater(CombatEntity self, GridArena arena) {
        GridPos myPos = self.getGridPos();
        CombatEntity nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (CombatEntity entity : arena.getOccupants().values()) {
            if (!entity.isAlive() || entity == self) continue;
            if (PASSIVE_MOBS.contains(entity.getEntityTypeId())) continue;
            // Must be on a water tile
            var tile = arena.getTile(entity.getGridPos());
            if (tile == null || !tile.isWater()) continue;
            int dist = myPos.manhattanDistance(entity.getGridPos());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private GridPos findWaterAdjacentTo(GridArena arena, GridPos from, GridPos target, int maxDist) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int[] d : dirs) {
            GridPos adj = new GridPos(target.x() + d[0], target.z() + d[1]);
            if (!arena.isInBounds(adj) || arena.isOccupied(adj)) continue;
            var tile = arena.getTile(adj);
            if (tile == null || !tile.isWater()) continue;
            int dist = from.manhattanDistance(adj);
            if (dist <= maxDist && dist < bestDist) {
                bestDist = dist;
                best = adj;
            }
        }
        return best;
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
                List<GridPos> path = Pathfinding.findPath(arena, myPos, target, d, self);
                if (!path.isEmpty() && allWater(arena, path)) {
                    return new EnemyAction.Move(path);
                }
            }
        }
        return new EnemyAction.Idle();
    }

    private boolean allWater(GridArena arena, List<GridPos> path) {
        for (GridPos pos : path) {
            var tile = arena.getTile(pos);
            if (tile == null || !tile.isWater()) return false;
        }
        return true;
    }
}
