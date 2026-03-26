package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Husk AI: Desert zombie variant — tougher, applies Weakness on hit.
 * - DESPERATION CHARGE: +1 speed when below 50% HP
 * - Hits harder than regular zombies (+1 hunger damage)
 * - Always move+attack, never wastes a turn
 */
public class HuskAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Desperation: +1 speed when wounded below 50%
        int effectiveSpeed = self.getMoveSpeed();
        if (self.getCurrentHp() < self.getMaxHp() / 2) {
            effectiveSpeed += 1;
        }

        int damage = self.getAttackPower() + 1; // hunger bonus

        if (dist <= 1) {
            return new EnemyAction.Attack(damage);
        }

        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, effectiveSpeed);
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, effectiveSpeed, self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) <= 1) {
            return new EnemyAction.MoveAndAttack(path, damage);
        }
        return new EnemyAction.Move(path);
    }
}
