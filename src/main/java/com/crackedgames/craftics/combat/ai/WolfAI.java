package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wolf AI: Predator — hunts sheep, chickens, and skeletons.
 * If attacked by the player, becomes permanently agro (enraged) and untamable.
 * When not hunting or agro, wanders like a farm animal.
 */
public class WolfAI implements EnemyAI {

    private static final Set<String> PREY = Set.of(
        "minecraft:sheep", "minecraft:chicken", "minecraft:skeleton"
    );

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        // If hit by player, become permanently agro
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged()) {
            self.setEnraged(true);
        }

        // ENRAGED: hunt the player
        if (self.isEnraged()) {
            return huntPlayer(self, arena, playerPos);
        }

        // Look for prey to hunt (configurable)
        if (com.crackedgames.craftics.CrafticsMod.CONFIG.predatorHuntingEnabled()) {
            CombatEntity prey = findNearestPrey(self, arena);
            if (prey != null) {
                return huntPrey(self, arena, prey);
            }
        }

        // No prey, no agro — wander (configurable chance)
        if (ThreadLocalRandom.current().nextFloat() < com.crackedgames.craftics.CrafticsMod.CONFIG.passiveMobWanderChance()) {
            return AIUtils.wander(self, arena); // wander aimlessly
        }
        return new EnemyAction.Idle();
    }

    private EnemyAction huntPlayer(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);
        int speed = self.getMoveSpeed();

        if (dist <= 1) {
            // Already adjacent: bite, then reposition if possible.
            EnemyAction combo = buildHitAndRun(self, arena, playerPos, List.of());
            return combo != null ? combo : new EnemyAction.Attack(self.getAttackPower());
        }

        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
        if (target != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, target, speed, self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= 1) {
                    EnemyAction combo = buildHitAndRun(self, arena, playerPos, path);
                    return combo != null ? combo : new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }
        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    private EnemyAction huntPrey(CombatEntity self, GridArena arena, CombatEntity prey) {
        GridPos myPos = self.getGridPos();
        GridPos preyPos = prey.getGridPos();
        int dist = myPos.manhattanDistance(preyPos);
        int speed = self.getMoveSpeed();

        // Adjacent to prey — attack it
        if (dist <= 1) {
            EnemyAction combo = buildHitAndRun(self, arena, preyPos, List.of());
            if (combo != null) return combo;
            return new EnemyAction.AttackMob(prey.getEntityId(), self.getAttackPower());
        }

        // Move toward prey
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, preyPos, speed);
        if (target != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, target, speed, self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(preyPos) <= 1) {
                    EnemyAction combo = buildHitAndRun(self, arena, preyPos, path);
                    if (combo != null) return combo;
                    return new EnemyAction.MoveAndAttackMob(path, prey.getEntityId(), self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }
        return new EnemyAction.Idle();
    }

    /**
     * Build a wolf hit-and-run action: move in, attack, then reposition with remaining movement.
     * Returns null when no retreat is possible.
     */
    private EnemyAction buildHitAndRun(CombatEntity self, GridArena arena, GridPos focusTarget,
                                       List<GridPos> approachPath) {
        int speed = self.getMoveSpeed();
        int approachSteps = approachPath != null ? approachPath.size() : 0;
        int remaining = Math.max(0, speed - approachSteps);
        if (remaining <= 0) return null;

        GridPos attackPos = approachSteps > 0 ? approachPath.get(approachSteps - 1) : self.getGridPos();
        java.util.Set<GridPos> reachable = Pathfinding.getReachableTiles(arena, attackPos, remaining, 1, self);
        if (reachable.isEmpty()) return null;

        GridPos retreatTarget = null;
        int bestDist = attackPos.manhattanDistance(focusTarget);
        for (GridPos pos : reachable) {
            int d = pos.manhattanDistance(focusTarget);
            if (d > bestDist) {
                bestDist = d;
                retreatTarget = pos;
            }
        }
        if (retreatTarget == null) return null;

        List<GridPos> retreatPath = Pathfinding.findPath(arena, attackPos, retreatTarget, remaining, self);
        if (retreatPath.isEmpty()) return null;
        return new EnemyAction.MoveAttackMove(approachPath, self.getAttackPower(), retreatPath);
    }

    private CombatEntity findNearestPrey(CombatEntity self, GridArena arena) {
        GridPos myPos = self.getGridPos();
        CombatEntity nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (CombatEntity entity : arena.getOccupants().values()) {
            if (!entity.isAlive() || entity == self) continue;
            if (!PREY.contains(entity.getEntityTypeId())) continue;
            int dist = myPos.manhattanDistance(entity.getGridPos());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }
}
