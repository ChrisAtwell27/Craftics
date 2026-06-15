package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wolf AI: Predator - hunts sheep, chickens, and skeletons.
 * If attacked by the player, becomes permanently agro (enraged) and untamable.
 * - PACK TACTICS: +1 damage per other enraged wolf already in melee contact
 *   with the target - a riled pack tears harder than the sum of its bites
 * - HIT AND RUN: bites the player then circles back out with leftover movement
 * - Prey is simply lunged at (no retreat) - dinner doesn't fight back
 * When not hunting or agro, wanders like a farm animal.
 */
public class WolfAI implements EnemyAI {

    /** Wolves are neutral toward the player - only a threat once provoked. */
    @Override
    public boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return self.isEnraged();
    }

    private static final Set<String> PREY = Set.of(
        "minecraft:sheep", "minecraft:chicken", "minecraft:skeleton"
    );

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
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

        // No prey, no agro - wander (configurable chance)
        if (ThreadLocalRandom.current().nextFloat() < com.crackedgames.craftics.CrafticsMod.CONFIG.passiveMobWanderChance()) {
            return AIUtils.wander(self, arena); // wander aimlessly
        }
        return new EnemyAction.Idle();
    }

    private EnemyAction huntPlayer(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);
        int speed = self.getMoveSpeed();
        int damage = self.getAttackPower() + packBonus(self, arena, playerPos);

        if (dist <= 1) {
            // Already adjacent: bite, then reposition if possible.
            EnemyAction combo = AIUtils.hitAndRun(self, arena, playerPos, List.of(), damage);
            return combo != null ? combo : new EnemyAction.Attack(damage);
        }

        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
        if (target != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, target, speed, self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= 1) {
                    EnemyAction combo = AIUtils.hitAndRun(self, arena, playerPos, path, damage);
                    return combo != null ? combo : new EnemyAction.MoveAndAttack(path, damage);
                }
                return new EnemyAction.Move(path);
            }
        }
        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /** +1 damage per OTHER enraged wolf already in melee contact with the victim. */
    private int packBonus(CombatEntity self, GridArena arena, GridPos victimPos) {
        int bonus = 0;
        java.util.Set<CombatEntity> seen = new java.util.HashSet<>();
        for (CombatEntity other : arena.getOccupants().values()) {
            if (!other.isAlive() || other == self || other.isAlly()) continue;
            if (!seen.add(other)) continue;
            if ("minecraft:wolf".equals(other.getEntityTypeId()) && other.isEnraged()
                    && other.getGridPos().manhattanDistance(victimPos) == 1) {
                bonus++;
            }
        }
        return bonus;
    }

    private EnemyAction huntPrey(CombatEntity self, GridArena arena, CombatEntity prey) {
        GridPos myPos = self.getGridPos();
        GridPos preyPos = prey.getGridPos();
        int dist = myPos.manhattanDistance(preyPos);
        int speed = self.getMoveSpeed();

        // Adjacent to prey - attack it. (AttackMob, not the hit-and-run combo:
        // MoveAttackMove always resolves its bite against the player/aggro pet,
        // so using it on prey made the wolf bite the wrong victim.)
        if (dist <= 1) {
            return new EnemyAction.AttackMob(prey.getEntityId(), self.getAttackPower());
        }

        // Move toward prey
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
