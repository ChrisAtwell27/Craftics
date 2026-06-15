package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Wither Skeleton AI: Wither-striking patrol fighter.
 * - Patrols in straight lines (cardinal), reverses direction at walls.
 *   Patrol heading lives in the entity's AI memory - the old instance fields
 *   sat on the shared AI object, so every wither skeleton on the server
 *   marched in lockstep and one reversal flipped them all.
 * - WITHER STRIKE: melee applies permanent -1 max HP per hit
 * - Below 50% HP: starts throwing wither skulls at range 2
 * - Speed 2, always aggressive when player is in range
 */
public class WitherSkeletonAI implements EnemyAI {
    private static final String PATROL_DX = "wsk_patrol_dx";
    private static final String PATROL_DZ = "wsk_patrol_dz";

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int damage = self.getAttackPower(); // wither effect applied via hit effect system
        int patrolDx = self.getAiMemory(PATROL_DX, 1);
        int patrolDz = self.getAiMemory(PATROL_DZ, 0);

        // Adjacent - wither strike
        if (dist == 1) {
            return new EnemyAction.Attack(damage);
        }

        // Below 50% HP - throw wither skulls at range 2
        if (self.getCurrentHp() < self.getMaxHp() / 2 && dist <= 2 && dist > 1) {
            if (AIUtils.hasCardinalLOS(arena, myPos, playerPos, 2)) {
                return new EnemyAction.RangedAttack(damage, "wither_skull");
            }
        }

        // Player within striking distance - rush in
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

        // Patrol - walk in a straight line, reverse at walls
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
        self.setAiMemory(PATROL_DX, patrolDx);
        self.setAiMemory(PATROL_DZ, patrolDz);

        if (arena.isInBounds(patrolTarget) && !arena.isOccupied(patrolTarget)
                && arena.getTile(patrolTarget) != null && arena.getTile(patrolTarget).isWalkable()) {
            return new EnemyAction.Move(List.of(patrolTarget));
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }
}
