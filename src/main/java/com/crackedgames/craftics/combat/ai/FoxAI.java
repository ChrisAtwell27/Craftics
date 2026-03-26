package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fox AI: Predator — hunts sheep and chickens.
 * If attacked by the player, becomes permanently agro (enraged) and untamable.
 * When not hunting or agro, wanders like a farm animal.
 */
public class FoxAI implements EnemyAI {

    private static final Set<String> PREY = Set.of(
        "minecraft:sheep", "minecraft:chicken"
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
            return AIUtils.wander(self, arena);
        }
        return new EnemyAction.Idle();
    }

    private EnemyAction huntPlayer(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);
        int speed = self.getMoveSpeed();

        if (dist <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
        if (target != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, target, speed, self);
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

    private EnemyAction huntPrey(CombatEntity self, GridArena arena, CombatEntity prey) {
        GridPos myPos = self.getGridPos();
        GridPos preyPos = prey.getGridPos();
        int dist = myPos.manhattanDistance(preyPos);
        int speed = self.getMoveSpeed();

        if (dist <= 1) {
            return new EnemyAction.AttackMob(prey.getEntityId(), self.getAttackPower());
        }

        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, preyPos, speed);
        if (target != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, target, speed, self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(preyPos) <= 1) {
                    return new EnemyAction.MoveAndAttackMob(path, prey.getEntityId(), self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }
        return new EnemyAction.Idle();
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
