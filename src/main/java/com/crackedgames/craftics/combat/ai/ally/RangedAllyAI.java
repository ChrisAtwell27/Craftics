package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Ranged ally AI — a kiter (llama spit, snow golem snowballs). Fires from its
 * full attack range and backs away when an enemy closes to melee, snapping off
 * a parting shot while it retreats. Flees outright when badly wounded.
 *
 * @since 0.3.0
 */
public class RangedAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        GridPos pos = self.getGridPos();
        int speed = self.getMoveSpeed();
        int range = Math.max(1, self.getRange());

        CombatEntity target = AllyTargeting.nearestEnemy(pos, combatants);
        if (target == null) return new EnemyAction.Idle();

        int dist = target.minDistanceTo(pos);
        boolean lowHp = AllyTargeting.lowHp(self, 0.25f);

        // Wounded, or an enemy has closed to melee — kite away from the threat.
        if (lowHp || dist <= 1) {
            int dx = Integer.signum(pos.x() - target.getGridPos().x());
            int dz = Integer.signum(pos.z() - target.getGridPos().z());
            GridPos retreat = new GridPos(pos.x() + dx * 2, pos.z() + dz * 2);
            List<GridPos> path = Pathfinding.findPath(arena, pos, retreat, speed, false);
            if (path != null && !path.isEmpty()) {
                GridPos end = path.get(path.size() - 1);
                // Parting shot if the target stays in range and we aren't fleeing for our life.
                if (!lowHp && target.minDistanceTo(end) <= range) {
                    return new EnemyAction.MoveAndAttackMob(
                        path, target.getEntityId(), self.getAttackPower());
                }
                return new EnemyAction.Flee(path);
            }
            // Cornered — bite back.
            return new EnemyAction.MoveAndAttackMob(
                List.of(), target.getEntityId(), self.getAttackPower());
        }

        // Already at a good firing distance — shoot without moving.
        if (dist <= range) {
            return new EnemyAction.MoveAndAttackMob(
                List.of(), target.getEntityId(), self.getAttackPower());
        }

        // Out of range — close just enough to fire.
        List<GridPos> path = Pathfinding.findPath(arena, pos, target.getGridPos(), speed, false);
        if (path != null && !path.isEmpty()) {
            GridPos end = path.get(path.size() - 1);
            if (target.minDistanceTo(end) <= range) {
                return new EnemyAction.MoveAndAttackMob(
                    path, target.getEntityId(), self.getAttackPower());
            }
            return new EnemyAction.Move(path);
        }
        GridPos closest = Pathfinding.findClosestReachableTo(
            arena, pos, target.getGridPos(), speed, self);
        if (closest != null && !closest.equals(pos)) {
            List<GridPos> seek = Pathfinding.findPath(arena, pos, closest, speed, false);
            if (seek != null && !seek.isEmpty()) return new EnemyAction.Move(seek);
        }
        return new EnemyAction.Idle();
    }
}
