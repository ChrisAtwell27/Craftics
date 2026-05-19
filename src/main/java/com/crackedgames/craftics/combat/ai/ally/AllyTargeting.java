package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Shared target-selection and movement helpers for the ally-AI archetypes
 * ({@link RangedAllyAI}, {@link FlyerAllyAI}, {@link TankAllyAI},
 * {@link SupportAllyAI}, {@link FarmAnimalAllyAI}).
 *
 * @since 0.3.0
 */
final class AllyTargeting {
    private AllyTargeting() {}

    /** True if {@code self} is at or below {@code frac} of its max HP. */
    static boolean lowHp(CombatEntity self, float frac) {
        return self.getMaxHp() > 0
            && (float) self.getCurrentHp() / self.getMaxHp() <= frac;
    }

    /** Live enemy nearest to {@code from}, or {@code null} if none remain. */
    static CombatEntity nearestEnemy(GridPos from, List<CombatEntity> combatants) {
        CombatEntity best = null;
        int bestDist = Integer.MAX_VALUE;
        for (CombatEntity e : combatants) {
            if (!e.isAlive() || e.isAlly()) continue;
            int d = e.minDistanceTo(from);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    /** Live enemy closest to the player — the biggest threat to body-block. */
    static CombatEntity nearestEnemyToPlayer(GridArena arena, List<CombatEntity> combatants) {
        return nearestEnemy(arena.getPlayerGridPos(), combatants);
    }

    /** Live enemy with the lowest current HP — a finisher's prey. */
    static CombatEntity weakestEnemy(List<CombatEntity> combatants) {
        CombatEntity best = null;
        int bestHp = Integer.MAX_VALUE;
        for (CombatEntity e : combatants) {
            if (!e.isAlive() || e.isAlly()) continue;
            if (e.getCurrentHp() < bestHp) { bestHp = e.getCurrentHp(); best = e; }
        }
        return best;
    }

    /**
     * Standard "advance on a target, attack when reachable" action used by the
     * aggressive archetypes. Attacks in place when already in range, moves into
     * range when a path lands adjacent, otherwise seeks the closest reachable tile.
     */
    static EnemyAction advance(CombatEntity self, GridArena arena, CombatEntity target) {
        GridPos pos = self.getGridPos();
        int speed = self.getMoveSpeed();

        if (target.minDistanceTo(pos) <= self.getRange()) {
            return new EnemyAction.MoveAndAttackMob(
                List.of(), target.getEntityId(), self.getAttackPower());
        }

        List<GridPos> path = Pathfinding.findPath(arena, pos, target.getGridPos(), speed, false);
        if (path != null && !path.isEmpty()) {
            GridPos end = path.get(path.size() - 1);
            if (target.minDistanceTo(end) <= self.getRange()) {
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

    /** Flee two tiles directly away from {@code threat}; {@code null} if boxed in. */
    static EnemyAction fleeFrom(CombatEntity self, GridArena arena, CombatEntity threat) {
        GridPos pos = self.getGridPos();
        int dx = Integer.signum(pos.x() - threat.getGridPos().x());
        int dz = Integer.signum(pos.z() - threat.getGridPos().z());
        GridPos retreat = new GridPos(pos.x() + dx * 2, pos.z() + dz * 2);
        List<GridPos> path = Pathfinding.findPath(arena, pos, retreat, self.getMoveSpeed(), false);
        if (path != null && !path.isEmpty()) return new EnemyAction.Flee(path);
        return null;
    }
}
