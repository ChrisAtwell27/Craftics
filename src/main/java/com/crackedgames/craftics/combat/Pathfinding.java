package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.*;

public class Pathfinding {

    private static final GridPos[] DIRECTIONS = {
        new GridPos(0, 1), new GridPos(0, -1),
        new GridPos(1, 0), new GridPos(-1, 0)
    };

    /**
     * A* pathfinding. Returns path (excluding start, including end).
     * Blocks enemy-occupied tiles. Player tile is passable.
     */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps) {
        return findPath(arena, from, to, maxSteps, null, false);
    }

    /** Player pathfinding with boat access for water tiles. */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, boolean hasBoat) {
        return findPath(arena, from, to, maxSteps, null, hasBoat);
    }

    /** Enemy pathfinding (no boat access). */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, CombatEntity self) {
        return findPath(arena, from, to, maxSteps, self, false);
    }

    /**
     * A* pathfinding with self-exclusion and optional boat access.
     * The 'self' entity's tile is ignored in occupancy checks.
     * When hasBoat is true, water tiles are considered walkable.
     */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, CombatEntity self, boolean hasBoat) {
        if (from.equals(to)) return List.of();
        if (!arena.isInBounds(to)) return List.of();

        var tile = arena.getTile(to);
        if (tile == null || !tile.isWalkable(hasBoat)) return List.of();
        if (isBlockedBy(arena, to, self)) return List.of();

        Map<GridPos, GridPos> cameFrom = new HashMap<>();
        Map<GridPos, Integer> gScore = new HashMap<>();
        gScore.put(from, 0);

        PriorityQueue<GridPos> open = new PriorityQueue<>(
            Comparator.comparingInt(p -> gScore.getOrDefault(p, Integer.MAX_VALUE) + p.manhattanDistance(to))
        );
        open.add(from);

        Set<GridPos> closed = new HashSet<>();

        while (!open.isEmpty()) {
            GridPos current = open.poll();
            if (current.equals(to)) {
                return reconstructPath(cameFrom, to, maxSteps, arena, self);
            }

            if (closed.contains(current)) continue;
            closed.add(current);

            int currentG = gScore.getOrDefault(current, Integer.MAX_VALUE);
            if (currentG >= maxSteps) continue;

            for (GridPos dir : DIRECTIONS) {
                GridPos neighbor = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (!arena.isInBounds(neighbor)) continue;
                if (closed.contains(neighbor)) continue;

                var neighborTile = arena.getTile(neighbor);
                if (neighborTile == null || !neighborTile.isWalkable(hasBoat)) continue;
                // Block enemy-occupied tiles (excluding self and the destination)
                if (!neighbor.equals(to) && isBlockedBy(arena, neighbor, self)) continue;

                int tentativeG = currentG + 1;
                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeG);
                    open.add(neighbor);
                }
            }
        }

        return List.of();
    }

    private static List<GridPos> reconstructPath(Map<GridPos, GridPos> cameFrom, GridPos end,
                                                   int maxSteps, GridArena arena, CombatEntity self) {
        List<GridPos> path = new ArrayList<>();
        GridPos current = end;
        while (cameFrom.containsKey(current)) {
            path.add(current);
            current = cameFrom.get(current);
        }
        Collections.reverse(path);

        if (path.size() > maxSteps) {
            path = new ArrayList<>(path.subList(0, maxSteps));
        }

        // Trim path if the truncated endpoint is occupied by another entity
        while (!path.isEmpty() && isBlockedBy(arena, path.get(path.size() - 1), self)) {
            path.remove(path.size() - 1);
        }

        return path;
    }

    /**
     * Check if a tile is blocked by an enemy (excluding 'self' if non-null).
     */
    private static boolean isBlockedBy(GridArena arena, GridPos pos, CombatEntity self) {
        CombatEntity occupant = arena.getOccupant(pos);
        if (occupant == null) return false;
        // Don't block on our own tile
        return self == null || occupant != self;
    }

    /**
     * Get all tiles reachable within maxSteps from the starting position.
     */
    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps) {
        Set<GridPos> reachable = new HashSet<>();
        Map<GridPos, Integer> dist = new HashMap<>();
        Queue<GridPos> queue = new LinkedList<>();

        dist.put(from, 0);
        queue.add(from);

        while (!queue.isEmpty()) {
            GridPos current = queue.poll();
            int currentDist = dist.get(current);

            if (currentDist >= maxSteps) continue;

            for (GridPos dir : DIRECTIONS) {
                GridPos neighbor = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (!arena.isInBounds(neighbor)) continue;
                if (dist.containsKey(neighbor)) continue;

                var tile = arena.getTile(neighbor);
                if (tile == null || !tile.isWalkable()) continue;
                if (arena.isOccupied(neighbor)) continue;

                dist.put(neighbor, currentDist + 1);
                reachable.add(neighbor);
                queue.add(neighbor);
            }
        }

        return reachable;
    }

    /**
     * Find the reachable tile within maxSteps that is closest to the target position.
     * Used as an AI fallback — "get as close as possible to the player."
     * Returns null if no tiles are reachable.
     */
    public static GridPos findClosestReachableTo(GridArena arena, GridPos from, GridPos target,
                                                   int maxSteps, CombatEntity self) {
        Set<GridPos> reachable = new HashSet<>();
        Map<GridPos, Integer> dist = new HashMap<>();
        Queue<GridPos> queue = new LinkedList<>();

        dist.put(from, 0);
        queue.add(from);

        while (!queue.isEmpty()) {
            GridPos current = queue.poll();
            int currentDist = dist.get(current);

            if (currentDist >= maxSteps) continue;

            for (GridPos dir : DIRECTIONS) {
                GridPos neighbor = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (!arena.isInBounds(neighbor)) continue;
                if (dist.containsKey(neighbor)) continue;

                var tile = arena.getTile(neighbor);
                if (tile == null || !tile.isWalkable()) continue;
                if (isBlockedBy(arena, neighbor, self)) continue;

                dist.put(neighbor, currentDist + 1);
                reachable.add(neighbor);
                queue.add(neighbor);
            }
        }

        // Find the reachable tile closest to the target
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos pos : reachable) {
            int d = pos.manhattanDistance(target);
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }
}
