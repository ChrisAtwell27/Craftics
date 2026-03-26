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
 * Bee AI: Passive normally. If ANY bee in the level is attacked, ALL bees
 * become permanently agro and poison on hit.
 * When a level spawns bees, all other passives should be replaced with bees.
 */
public class BeeAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);

        // Check if any bee in the arena has been hit — if so, ALL bees agro
        if (!self.isEnraged()) {
            if (self.wasDamagedSinceLastTurn() || anyBeeHit(arena)) {
                // Enrage ALL bees
                enrageAllBees(arena);
            }
        }

        // AGRO SWARM: chase and sting
        if (self.isEnraged()) {
            if (dist <= 1) {
                // Sting attack — poison effect handled by applyEnemyHitEffect in CombatManager
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

        // Passive: configurable wander chance
        if (ThreadLocalRandom.current().nextFloat() < com.crackedgames.craftics.CrafticsMod.CONFIG.passiveMobWanderChance()) {
            return tryWander(self, arena);
        }
        return new EnemyAction.Idle();
    }

    /** Check if any bee in the arena has been damaged this turn. */
    private boolean anyBeeHit(GridArena arena) {
        for (CombatEntity entity : arena.getOccupants().values()) {
            if (!entity.isAlive()) continue;
            if ("minecraft:bee".equals(entity.getEntityTypeId()) && entity.wasDamagedSinceLastTurn()) {
                return true;
            }
        }
        return false;
    }

    /** Enrage every bee in the arena. */
    private void enrageAllBees(GridArena arena) {
        for (CombatEntity entity : arena.getOccupants().values()) {
            if (!entity.isAlive()) continue;
            if ("minecraft:bee".equals(entity.getEntityTypeId())) {
                entity.setEnraged(true);
            }
        }
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
