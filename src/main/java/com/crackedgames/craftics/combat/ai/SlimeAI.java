package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Slime AI: a simple, relentless beeline mob. It walks straight at the player
 * each turn (no pouncing, no kiting) and attacks the moment it's adjacent.
 * Individually weak and slow, but dangerous in numbers - a split slime swarm
 * can overwhelm by sheer count. Move speed comes from the entity's stats
 * (mediums move 1 tile/turn), so the threat is the swarm, not any single hop.
 */
public class SlimeAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        // Adjacent - slam attack.
        if (self.minDistanceTo(playerPos) == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Otherwise close the gap in a straight line toward the player. Slimes are
        // 2x2, so use the size-aware target picker and pathfinder - otherwise the
        // slime's footprint can land on top of the player (clipping into it).
        int size = self.getSize();
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed(), size);
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPathSized(arena, myPos, target, self.getMoveSpeed(), self, size);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        // Measure from the slime's would-be footprint, not just its anchor corner.
        if (CombatEntity.minDistanceFromSizedEntity(endPos, size, playerPos) == 1) {
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
        }
        return new EnemyAction.Move(path);
    }
}
