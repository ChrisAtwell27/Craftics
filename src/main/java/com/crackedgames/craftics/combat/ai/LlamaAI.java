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
 * Llama AI: Farm animal normally. When attacked, becomes permanently agro
 * and spits at the player from 2 blocks away (ranged).
 * Tries to maintain distance 2 for spitting — backs up if too close, approaches if too far.
 */
public class LlamaAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);

        // Become agro when hit
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged()) {
            self.setEnraged(true);
        }

        // AGRO: spit from range 2
        if (self.isEnraged()) {
            // In range with LOS — spit!
            if (dist <= 2 && AIUtils.hasCardinalLOS(arena, myPos, playerPos, 2)) {
                return new EnemyAction.RangedAttack(self.getAttackPower(), "llama_spit");
            }

            // Too close — back up to range 2 and spit
            if (dist <= 1) {
                GridPos retreatPos = findSpitPosition(self, arena, playerPos);
                if (retreatPos != null) {
                    List<GridPos> path = Pathfinding.findPath(arena, myPos, retreatPos, self.getMoveSpeed(), self);
                    if (!path.isEmpty()) {
                        GridPos endPos = path.get(path.size() - 1);
                        if (AIUtils.hasCardinalLOS(arena, endPos, playerPos, 2)) {
                            return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                        }
                        return new EnemyAction.Move(path);
                    }
                }
                // Cornered at melee — just spit point blank
                return new EnemyAction.Attack(self.getAttackPower());
            }

            // Too far — approach to spit range
            GridPos spitPos = findSpitPosition(self, arena, playerPos);
            if (spitPos != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, spitPos, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (AIUtils.hasCardinalLOS(arena, endPos, playerPos, 2)) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
            return AIUtils.seekOrWander(self, arena, playerPos);
        }

        // Normal farm animal: configurable wander chance
        if (ThreadLocalRandom.current().nextFloat() < com.crackedgames.craftics.CrafticsMod.CONFIG.passiveMobWanderChance()) {
            return tryWander(self, arena);
        }
        return new EnemyAction.Idle();
    }

    /** Find a tile at distance 2 from player with cardinal LOS for spitting. */
    private GridPos findSpitPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > self.getMoveSpeed()) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                if (distToPlayer < 1 || distToPlayer > 2) continue;
                if (!AIUtils.hasCardinalLOS(arena, candidate, playerPos, 2)) continue;

                int moveDist = myPos.manhattanDistance(candidate);
                if (moveDist < bestDist) {
                    bestDist = moveDist;
                    best = candidate;
                }
            }
        }
        return best;
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
