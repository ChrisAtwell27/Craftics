package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Shulker AI: Immobile turret — fires homing bullets at any range up to 5.
 * - HOMING BULLET: attacks at range 5 (any direction — ignores LOS)
 * - LOCKDOWN: applies Slowness on hit (levitation effect = movement debuff)
 * - SHELL: very rarely moves (speed 1), high defense
 * - Only repositions if player is adjacent and threatens melee
 */
public class ShulkerAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();

        // EMERGENCY RETREAT: if adjacent, try to scoot away
        if (dist <= 1) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, 1);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, 1, self);
                if (!path.isEmpty()) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
            }
            // Can't flee — shoot at point blank
            return new EnemyAction.RangedAttack(self.getAttackPower(), "shulker_bullet");
        }

        // In range — fire homing bullet (ignores LOS — shulker bullets track)
        if (dist <= range) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "shulker_bullet");
        }

        // Out of range — reluctantly reposition (speed 1)
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, 1);
        if (target != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, target, 1, self);
            if (!path.isEmpty()) return new EnemyAction.Move(path);
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }
}
