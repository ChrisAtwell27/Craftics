package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Shared target-selection and movement helpers for the ally-AI archetypes.
 * All pathfinding here is size-aware, so 2x2 allies (polar bear, camel) route
 * their full footprint correctly.
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

    /** Size-aware path from the ally's tile to {@code to} (delegates to 1x1 routing for small allies). */
    static List<GridPos> pathTo(CombatEntity self, GridArena arena, GridPos to) {
        return Pathfinding.findPathSized(
            arena, self.getGridPos(), to, self.getMoveSpeed(), self, self.getSize());
    }

    /**
     * Standard "advance on a target, attack when reachable" action. Attacks in
     * place when already in range; when an approach path lands in range it
     * returns a move-and-attack so the ally moves and strikes in one turn;
     * otherwise it moves toward / seeks the closest reachable tile.
     */
    static EnemyAction advance(CombatEntity self, GridArena arena, CombatEntity target) {
        GridPos pos = self.getGridPos();

        if (target.minDistanceTo(pos) <= self.getRange()) {
            return new EnemyAction.MoveAndAttackMob(
                List.of(), target.getEntityId(), self.getAttackPower());
        }

        List<GridPos> path = pathTo(self, arena, target.getGridPos());
        if (path != null && !path.isEmpty()) {
            GridPos end = path.get(path.size() - 1);
            if (target.minDistanceTo(end) <= self.getRange()) {
                // Move-and-attack: walk the approach path, then strike this turn.
                return new EnemyAction.MoveAndAttackMob(
                    path, target.getEntityId(), self.getAttackPower());
            }
            return new EnemyAction.Move(path);
        }

        GridPos closest = Pathfinding.findClosestReachableTo(
            arena, pos, target.getGridPos(), self.getMoveSpeed(), self, self.getSize());
        if (closest != null && !closest.equals(pos)) {
            List<GridPos> seek = pathTo(self, arena, closest);
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
        List<GridPos> path = pathTo(self, arena, retreat);
        if (path != null && !path.isEmpty()) return new EnemyAction.Flee(path);
        return null;
    }
}
