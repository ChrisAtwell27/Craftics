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
    /** Llamas are neutral — only a threat once provoked (enraged). */
    @Override
    public boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return self.isEnraged();
    }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);

        // Become agro when hit
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged()) {
            self.setEnraged(true);
        }

        // AGRO: spit from its registered range (2 for vanilla llamas)
        int range = Math.max(1, self.getRange());
        if (self.isEnraged()) {
            // In range with LOS — spit!
            if (dist <= range && AIUtils.hasCardinalLOS(arena, myPos, playerPos, range)) {
                return new EnemyAction.RangedAttack(self.getAttackPower(), "llama_spit");
            }

            // Too close — back up to spitting range
            if (dist <= 1) {
                GridPos retreatPos = findSpitPosition(self, arena, playerPos, range);
                if (retreatPos != null) {
                    List<GridPos> path = Pathfinding.findPath(arena, myPos, retreatPos, self.getMoveSpeed(), self);
                    if (!path.isEmpty()) {
                        GridPos endPos = path.get(path.size() - 1);
                        if (AIUtils.hasCardinalLOS(arena, endPos, playerPos, range)) {
                            return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                        }
                        return new EnemyAction.Move(path);
                    }
                }
                // Cornered at melee — just spit point blank
                return new EnemyAction.Attack(self.getAttackPower());
            }

            // Too far — approach to spit range
            GridPos spitPos = findSpitPosition(self, arena, playerPos, range);
            if (spitPos != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, spitPos, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (AIUtils.hasCardinalLOS(arena, endPos, playerPos, range)) {
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

    /** Find a reachable tile within spitting range that has cardinal LOS on the player. */
    private GridPos findSpitPosition(CombatEntity self, GridArena arena, GridPos playerPos, int range) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;

        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, myPos, self.getMoveSpeed(), self.getSize(), self)) {
            if (candidate.equals(myPos)) continue;

            int distToPlayer = candidate.manhattanDistance(playerPos);
            if (distToPlayer < 1 || distToPlayer > range) continue;
            if (!AIUtils.hasCardinalLOS(arena, candidate, playerPos, range)) continue;

            // Prefer max distance within range (llamas keep their distance),
            // then shorter walks; never end on a hazard
            int score = distToPlayer * 10 - myPos.manhattanDistance(candidate);
            if (AIUtils.isHazardTile(arena, candidate)) score -= 50;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
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
