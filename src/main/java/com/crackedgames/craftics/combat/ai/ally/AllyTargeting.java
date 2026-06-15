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

    /**
     * Distance between {@code from} and the closest tile an enemy actually
     * occupies, including the many manually-registered tiles a background
     * boss (Ghast, Ender Dragon) lives on. {@link CombatEntity#minDistanceTo}
     * only sees the single anchor tile for size-1 entities, so it always
     * reports a huge distance for background bosses and allies never read
     * them as adjacent.
     */
    static int distanceToTarget(CombatEntity target, GridArena arena, GridPos from) {
        if (!target.isBackgroundBoss()) return target.minDistanceTo(from);
        int best = Integer.MAX_VALUE;
        for (var entry : arena.getOccupants().entrySet()) {
            if (entry.getValue() != target) continue;
            GridPos tile = entry.getKey();
            int d = Math.abs(tile.x() - from.x()) + Math.abs(tile.z() - from.z());
            if (d < best) best = d;
        }
        return best == Integer.MAX_VALUE ? target.minDistanceTo(from) : best;
    }

    /** A tile the target actually occupies that's closest to {@code from}. */
    static GridPos nearestTileOnTarget(CombatEntity target, GridArena arena, GridPos from) {
        if (!target.isBackgroundBoss()) return target.nearestTileTo(from);
        GridPos best = target.getGridPos();
        int bestDist = Integer.MAX_VALUE;
        for (var entry : arena.getOccupants().entrySet()) {
            if (entry.getValue() != target) continue;
            GridPos tile = entry.getKey();
            int d = Math.abs(tile.x() - from.x()) + Math.abs(tile.z() - from.z());
            if (d < bestDist) { bestDist = d; best = tile; }
        }
        return best;
    }

    /** Live enemy closest to the player - the biggest threat to body-block. */
    static CombatEntity nearestEnemyToPlayer(GridArena arena, List<CombatEntity> combatants) {
        return nearestEnemy(arena.getPlayerGridPos(), combatants);
    }

    /** Live enemy with the lowest current HP - a finisher's prey. */
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

        if (distanceToTarget(target, arena, pos) <= self.getRange()) {
            return new EnemyAction.MoveAndAttackMob(
                List.of(), target.getEntityId(), self.getAttackPower());
        }

        // pathTo(target.getGridPos()) usually returns empty because the target's
        // own tile is reported blocked by the target. Route to the closest tile
        // we can actually stand on instead, and if that tile lands within attack
        // range, walk-and-strike in the same turn so the ally doesn't end its
        // turn parked next to an enemy without hitting it. For background bosses
        // (Ghast, Ender Dragon) the boss's anchor tile is at the arena corner
        // and bears no relation to where the boss actually sits, so steer toward
        // the boss tile that is genuinely closest to us instead.
        GridPos aim = nearestTileOnTarget(target, arena, pos);
        GridPos closest = Pathfinding.findClosestReachableTo(
            arena, pos, aim, self.getMoveSpeed(), self, self.getSize());
        if (closest != null && !closest.equals(pos)) {
            List<GridPos> seek = pathTo(self, arena, closest);
            if (seek != null && !seek.isEmpty()) {
                if (distanceToTarget(target, arena, closest) <= self.getRange()) {
                    return new EnemyAction.MoveAndAttackMob(
                        seek, target.getEntityId(), self.getAttackPower());
                }
                return new EnemyAction.Move(seek);
            }
        }
        return new EnemyAction.Idle();
    }

    /**
     * Flee from {@code threat}: try two tiles directly away first, then fall
     * back to the reachable tile that gains the most distance - so a wounded
     * ally escapes around a corner instead of standing still because the
     * straight line out happened to be blocked. {@code null} if truly boxed in.
     */
    static EnemyAction fleeFrom(CombatEntity self, GridArena arena, CombatEntity threat) {
        GridPos pos = self.getGridPos();
        GridPos threatPos = threat.getGridPos();
        int dx = Integer.signum(pos.x() - threatPos.x());
        int dz = Integer.signum(pos.z() - threatPos.z());
        GridPos retreat = new GridPos(pos.x() + dx * 2, pos.z() + dz * 2);
        List<GridPos> path = pathTo(self, arena, retreat);
        if (path != null && !path.isEmpty()) return new EnemyAction.Flee(path);

        // Straight line blocked - take the best reachable escape instead.
        GridPos best = null;
        int bestDist = pos.manhattanDistance(threatPos);
        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, pos, self.getMoveSpeed(), self.getSize(), self)) {
            int d = candidate.manhattanDistance(threatPos);
            if (d > bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        if (best != null) {
            List<GridPos> escape = pathTo(self, arena, best);
            if (escape != null && !escape.isEmpty()) return new EnemyAction.Flee(escape);
        }
        return null;
    }
}
