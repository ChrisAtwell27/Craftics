package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Mounted AI: Cavalry charger (e.g., rider on Camel).
 * - LANCE CHARGE: bonus damage based on tiles traveled before hitting (+1 per tile moved)
 * - DISMOUNT: when below 50% HP, mount dies — speed drops from 4 to 2
 * - Prefers straight-line charges for maximum lance damage
 * - Speed 4 mounted / 2 dismounted
 */
public class MountedAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Mounted (HP > 50%) = fast, dismounted = slower
        boolean mounted = self.getCurrentHp() > self.getMaxHp() / 2;
        int effectiveSpeed = mounted ? self.getMoveSpeed() : Math.max(2, self.getMoveSpeed() - 2);

        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // LANCE CHARGE: prefer straight-line for bonus damage
        GridPos chargeTarget = findChargePath(arena, myPos, playerPos, effectiveSpeed);
        if (chargeTarget != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, chargeTarget, effectiveSpeed, self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) == 1) {
                    // Lance damage: base + tiles traveled
                    int lanceDamage = self.getAttackPower() + path.size();
                    return new EnemyAction.MoveAndAttack(path, lanceDamage);
                }
                return new EnemyAction.Move(path);
            }
        }

        // Normal approach
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, effectiveSpeed);
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, effectiveSpeed, self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) == 1) {
            int damage = self.getAttackPower() + (mounted ? path.size() : 0);
            return new EnemyAction.MoveAndAttack(path, damage);
        }

        return new EnemyAction.Move(path);
    }

    private GridPos findChargePath(GridArena arena, GridPos self, GridPos playerPos, int maxSteps) {
        // Check cardinal straight lines toward player
        if (self.x() == playerPos.x()) {
            int dz = Integer.signum(playerPos.z() - self.z());
            int steps = Math.min(maxSteps, Math.abs(playerPos.z() - self.z()) - 1);
            if (steps > 0) {
                GridPos target = new GridPos(self.x(), self.z() + dz * steps);
                if (arena.isInBounds(target) && !arena.isEnemyOccupied(target)) return target;
            }
        } else if (self.z() == playerPos.z()) {
            int dx = Integer.signum(playerPos.x() - self.x());
            int steps = Math.min(maxSteps, Math.abs(playerPos.x() - self.x()) - 1);
            if (steps > 0) {
                GridPos target = new GridPos(self.x() + dx * steps, self.z());
                if (arena.isInBounds(target) && !arena.isEnemyOccupied(target)) return target;
            }
        }
        return null;
    }
}
