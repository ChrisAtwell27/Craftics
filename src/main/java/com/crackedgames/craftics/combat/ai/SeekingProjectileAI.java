package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * AI for homing projectile entities (shulker bullets, grave skulls, scarabs).
 *
 * <p>Unlike {@link ProjectileAI}'s straight-line flight, a seeker re-aims at the player every
 * turn and takes up to {@code SPEED} cardinal steps along the shortest clear route to them,
 * impacting when a step lands on their tile.</p>
 *
 * <h2>Why this searches instead of stepping greedily</h2>
 *
 * <p>It used to pick each step by hand: the axis with more distance to cover, then the other
 * axis as a sidestep. Two candidates, and both can be unavailable at once - one case guarantees
 * it. A seeker lined up on the player's own row or column has a zero-length "other axis", so a
 * single block between the two left it with exactly one candidate and no legal move. It then
 * hovered, and kept hovering: a seeker re-aims from scratch each turn, so an obstacle that does
 * not move produces a projectile that never moves again. That is what Grave Skulls sitting
 * still in the Revenant fight were.</p>
 *
 * <p>Patching that with a sideways step does not work either, and the failure is instructive:
 * after one lateral step the toward-the-player candidate on that axis points straight back into
 * the tile it just left, so the seeker rocks between two squares. Remembering a committed
 * detour direction does not save it, because the reversing step is an <em>approach</em> step,
 * which looks like progress. The greedy rule has no way to tell "back where I started" from
 * "closer to the player", because on that axis they are the same move.</p>
 *
 * <p>A breadth-first search over the walkable grid has neither problem. It routes around any
 * obstacle it can get around, it cannot oscillate because it follows one shortest path rather
 * than re-deciding per step, and when genuinely walled in it returns nothing - which is the
 * one case where holding still is the right answer. The grid is a few hundred tiles, so the
 * search costs less than the reasoning about why the heuristic was wrong.</p>
 *
 * <p>The counterplay is HP, not deflection: seekers spawn with 1 HP, so any attack swats them
 * (the ghast-fireball redirect special case deliberately does not apply).</p>
 */
public class SeekingProjectileAI implements EnemyAI {
    private static final int SPEED = 2;

    private static final GridPos[] CARDINALS = {
        new GridPos(1, 0), new GridPos(-1, 0), new GridPos(0, 1), new GridPos(0, -1)
    };

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos start = self.getGridPos();
        if (start.equals(playerPos)) return new EnemyAction.Idle();

        List<GridPos> route = shortestRoute(arena, start, playerPos);
        if (route.isEmpty()) {
            // Walled in on every side. Hold position - it has nowhere legal to go, and it
            // never detonates on terrain.
            return new EnemyAction.Idle();
        }

        List<GridPos> path = new ArrayList<>();
        for (GridPos step : route) {
            path.add(step);
            if (step.equals(playerPos)) {
                return new EnemyAction.ProjectileMove(path, true, step);
            }
            if (path.size() >= SPEED) break;
        }
        return new EnemyAction.ProjectileMove(path, false, null);
    }

    /**
     * Shortest step-by-step route from {@code start} to {@code goal}, excluding the start tile.
     *
     * <p>The goal is reachable even though something is standing on it - that something is the
     * player, and arriving is the whole point. Every other occupied tile is treated as solid,
     * so a seeker will not fly through its own siblings.
     *
     * @return the steps to walk, or an empty list when no route exists
     */
    private static List<GridPos> shortestRoute(GridArena arena, GridPos start, GridPos goal) {
        Map<GridPos, GridPos> cameFrom = new HashMap<>();
        Queue<GridPos> frontier = new ArrayDeque<>();
        frontier.add(start);
        cameFrom.put(start, null);

        boolean found = false;
        while (!frontier.isEmpty() && !found) {
            GridPos current = frontier.poll();
            for (GridPos dir : CARDINALS) {
                GridPos next = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (cameFrom.containsKey(next)) continue;
                if (!next.equals(goal) && !passable(arena, next)) continue;
                if (next.equals(goal) && !arena.isInBounds(next)) continue;
                cameFrom.put(next, current);
                if (next.equals(goal)) { found = true; break; }
                frontier.add(next);
            }
        }
        if (!found) return List.of();

        List<GridPos> route = new ArrayList<>();
        for (GridPos at = goal; at != null && !at.equals(start); at = cameFrom.get(at)) {
            route.add(at);
        }
        Collections.reverse(route);
        return route;
    }

    /** A tile a seeker may fly onto: in bounds, walkable, and nobody standing there. */
    private static boolean passable(GridArena arena, GridPos pos) {
        if (!arena.isInBounds(pos)) return false;
        if (arena.getTile(pos) == null || !arena.getTile(pos).isWalkable()) return false;
        return !arena.isOccupied(pos);
    }

    /**
     * Danger highlight: everywhere the seeker can reach next turn. The generic
     * speed+range diamond under-reports a 2-tiles-per-turn homing bullet.
     */
    @Override
    public java.util.Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        java.util.Set<GridPos> tiles = new java.util.HashSet<>();
        GridPos myPos = self.getGridPos();
        for (int dx = -SPEED; dx <= SPEED; dx++) {
            for (int dz = -SPEED; dz <= SPEED; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > SPEED) continue;
                GridPos p = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (arena.isInBounds(p)) tiles.add(p);
            }
        }
        return tiles;
    }
}
