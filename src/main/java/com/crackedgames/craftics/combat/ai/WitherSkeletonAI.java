package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Wither Skeleton AI: Wither-striking patrol fighter.
 * - Patrols in straight lines (cardinal), reverses direction at walls
 * - WITHER STRIKE: melee applies permanent -1 max HP per hit
 * - Below 50% HP: starts throwing wither skulls at range 2
 * - Speed 2, always aggressive when player is in range
 */
public class WitherSkeletonAI implements EnemyAI {
    // Patrol direction persists between turns
    private int patrolDx = 1, patrolDz = 0;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int damage = self.getAttackPower(); // wither effect applied via hit effect system

        // Adjacent — wither strike
        if (dist == 1) {
            return new EnemyAction.Attack(damage);
        }

        // Below 50% HP — throw wither skulls at range 2
        if (self.getCurrentHp() < self.getMaxHp() / 2 && dist <= 2 && dist > 1) {
            if (AIUtils.hasCardinalLOS(arena, myPos, playerPos, 2)) {
                return new EnemyAction.RangedAttack(damage, "wither_skull");
            }
        }

        // Player within striking distance — rush in
        if (dist <= self.getMoveSpeed() + 1) {
            GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (target == null) target = playerPos;
            List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) == 1) {
                    return new EnemyAction.MoveAndAttack(path, damage);
                }
                return new EnemyAction.Move(path);
            }
        }

        // Patrol — walk in a straight line, reverse at walls
        GridPos patrolTarget = new GridPos(myPos.x() + patrolDx, myPos.z() + patrolDz);
        if (!arena.isInBounds(patrolTarget) || arena.isOccupied(patrolTarget)
                || arena.getTile(patrolTarget) == null || !arena.getTile(patrolTarget).isWalkable()) {
            // Reverse direction
            patrolDx = -patrolDx;
            patrolDz = -patrolDz;
            patrolTarget = new GridPos(myPos.x() + patrolDx, myPos.z() + patrolDz);
            if (!arena.isInBounds(patrolTarget) || arena.isOccupied(patrolTarget)) {
                // Try perpendicular
                int temp = patrolDx;
                patrolDx = patrolDz;
                patrolDz = temp;
                patrolTarget = new GridPos(myPos.x() + patrolDx, myPos.z() + patrolDz);
            }
        }

        if (arena.isInBounds(patrolTarget) && !arena.isOccupied(patrolTarget)
                && arena.getTile(patrolTarget) != null && arena.getTile(patrolTarget).isWalkable()) {
            return new EnemyAction.Move(List.of(patrolTarget));
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }
}
