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
 * Goat AI: Farm animal behavior normally.
 * When hit by the player at melee range (1 block), counterattacks with +2 knockback
 * and becomes permanently agro (enraged), charging in straight lines toward the player.
 */
public class GoatAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // If hit and not yet enraged, become enraged and counterattack with knockback
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged()) {
            self.setEnraged(true);
            // Counterattack if adjacent
            if (dist <= 1) {
                return new EnemyAction.AttackWithKnockback(self.getAttackPower() + 2, com.crackedgames.craftics.CrafticsMod.CONFIG.knockbackDistance());
            }
        }

        // ENRAGED: permanently agro — charge toward player in straight lines
        if (self.isEnraged()) {
            return chargePlayer(self, arena, playerPos);
        }

        // Normal farm animal: configurable wander chance
        if (ThreadLocalRandom.current().nextFloat() < com.crackedgames.craftics.CrafticsMod.CONFIG.passiveMobWanderChance()) {
            return tryWander(self, arena);
        }
        return new EnemyAction.Idle();
    }

    /**
     * Charge toward player, preferring straight-line approaches for the ram attack.
     */
    private EnemyAction chargePlayer(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);
        int speed = self.getMoveSpeed();

        // Adjacent: ram with knockback
        if (dist <= 1) {
            return new EnemyAction.AttackWithKnockback(self.getAttackPower() + 2, com.crackedgames.craftics.CrafticsMod.CONFIG.knockbackDistance());
        }

        // Try to charge in a straight line (same row or column as player)
        if (myPos.x() == playerPos.x() || myPos.z() == playerPos.z()) {
            // Already aligned — charge straight
            GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
            if (target != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, target, speed, self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (endPos.manhattanDistance(playerPos) <= 1) {
                        return new EnemyAction.MoveAndAttackWithKnockback(path, self.getAttackPower() + 2, com.crackedgames.craftics.CrafticsMod.CONFIG.knockbackDistance());
                    }
                    return new EnemyAction.Move(path);
                }
            }
        }

        // Not aligned — move toward player to set up a charge
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
        if (target != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, target, speed, self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= 1) {
                    return new EnemyAction.MoveAndAttackWithKnockback(path, self.getAttackPower() + 2, com.crackedgames.craftics.CrafticsMod.CONFIG.knockbackDistance());
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
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
                if (!path.isEmpty()) {
                    return new EnemyAction.Move(path);
                }
            }
        }
        return new EnemyAction.Idle();
    }
}
