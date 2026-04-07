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
        return findPath(arena, from, to, maxSteps, null, hasBoat, false);
    }

    /** Player pathfinding with boat access and optional obstacle ignoring (Pathfinder set bonus). */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, boolean hasBoat, boolean ignoreObstacles) {
        return findPath(arena, from, to, maxSteps, null, hasBoat, ignoreObstacles);
    }

    /** Enemy pathfinding (no boat access). */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, CombatEntity self) {
        return findPath(arena, from, to, maxSteps, self, false);
    }

    /** Size-aware enemy pathfinding. For entities > 1x1, checks full footprint at each position. */
    public static List<GridPos> findPathSized(GridArena arena, GridPos from, GridPos to,
                                               int maxSteps, CombatEntity self, int entitySize) {
        if (entitySize <= 1) return findPath(arena, from, to, maxSteps, self, false);
        return findPathSizedInternal(arena, from, to, maxSteps, self, entitySize);
    }

    /**
     * A* pathfinding with self-exclusion and optional boat access.
     * The 'self' entity's tile is ignored in occupancy checks.
     * When hasBoat is true, water tiles are considered walkable.
     */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, CombatEntity self, boolean hasBoat) {
        return findPath(arena, from, to, maxSteps, self, hasBoat, false);
    }

    /**
     * A* pathfinding with self-exclusion, optional boat access, and optional obstacle ignoring.
     * When ignoreObstacles is true (Pathfinder set bonus), OBSTACLE tiles are walkable.
     */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, CombatEntity self, boolean hasBoat, boolean ignoreObstacles) {
        if (from.equals(to)) return List.of();
        if (!arena.isInBounds(to)) return List.of();

        var tile = arena.getTile(to);
        if (tile == null || !tile.isWalkableEx(hasBoat, ignoreObstacles)) return List.of();
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
                if (neighborTile == null || !neighborTile.isWalkableEx(hasBoat, ignoreObstacles)) continue;
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
        if (self != null && occupant == self) return false;
        // Boss entities can walk through passable entities (e.g., egg sacs)
        if (self != null && self.isBoss() && occupant.isPassableForBoss()) return false;
        return true;
    }

    /**
     * Check if ALL tiles of a sized entity's footprint at a given anchor position are valid.
     * For size 1, this is equivalent to checking a single tile.
     */
    private static boolean canPlaceSizedEntity(GridArena arena, GridPos anchor, int entitySize,
                                                CombatEntity self, boolean hasBoat) {
        return canPlaceSizedEntity(arena, anchor, entitySize, self, hasBoat, false);
    }

    private static boolean canPlaceSizedEntity(GridArena arena, GridPos anchor, int entitySize,
                                                CombatEntity self, boolean hasBoat, boolean ignoreObstacles) {
        for (GridPos tile : GridArena.getOccupiedTiles(anchor, entitySize)) {
            if (!arena.isInBounds(tile)) return false;
            var gridTile = arena.getTile(tile);
            if (gridTile == null || !gridTile.isWalkableEx(hasBoat, ignoreObstacles)) return false;
            if (isBlockedBy(arena, tile, self)) return false;
        }
        return true;
    }

    /**
     * Get all tiles reachable within maxSteps from the starting position (1x1 entities).
     */
    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps) {
        return getReachableTiles(arena, from, maxSteps, 1, null, false);
    }

    /** Overload with hasBoat for player movement highlights. */
    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps, boolean hasBoat) {
        return getReachableTiles(arena, from, maxSteps, 1, null, hasBoat);
    }

    /** Overload with hasBoat and ignoreObstacles for Pathfinder set bonus. */
    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps, boolean hasBoat, boolean ignoreObstacles) {
        return getReachableTiles(arena, from, maxSteps, 1, null, hasBoat, ignoreObstacles);
    }

    /**
     * Get all tiles reachable within maxSteps, accounting for entity size.
     * For a 2x2 entity, each candidate position is checked to ensure ALL footprint
     * tiles are in-bounds, walkable, and unoccupied (except by self).
     */
    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   int entitySize, CombatEntity self) {
        return getReachableTiles(arena, from, maxSteps, entitySize, self, false);
    }

    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   int entitySize, CombatEntity self, boolean hasBoat) {
        return getReachableTiles(arena, from, maxSteps, entitySize, self, hasBoat, false);
    }

    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   int entitySize, CombatEntity self, boolean hasBoat, boolean ignoreObstacles) {
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
                if (dist.containsKey(neighbor)) continue;

                // For sized entities, check ALL footprint tiles at this anchor position
                if (!canPlaceSizedEntity(arena, neighbor, entitySize, self, hasBoat, ignoreObstacles)) continue;

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

    /**
     * Size-aware version of findClosestReachableTo.
     * For entities > 1x1, checks full footprint at each candidate position.
     */
    public static GridPos findClosestReachableTo(GridArena arena, GridPos from, GridPos target,
                                                   int maxSteps, CombatEntity self, int entitySize) {
        if (entitySize <= 1) return findClosestReachableTo(arena, from, target, maxSteps, self);

        Set<GridPos> reachable = getReachableTiles(arena, from, maxSteps, entitySize, self);

        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos pos : reachable) {
            int d = CombatEntity.minDistanceFromSizedEntity(pos, entitySize, target);
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }

    /**
     * A* pathfinding for multi-tile entities.
     * Checks all footprint tiles at each candidate position.
     */
    private static List<GridPos> findPathSizedInternal(GridArena arena, GridPos from, GridPos to,
                                                         int maxSteps, CombatEntity self, int entitySize) {
        if (from.equals(to)) return List.of();
        if (!canPlaceSizedEntity(arena, to, entitySize, self, false)) return List.of();

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
                return reconstructSizedPath(cameFrom, to, maxSteps, arena, self, entitySize);
            }

            if (closed.contains(current)) continue;
            closed.add(current);

            int currentG = gScore.getOrDefault(current, Integer.MAX_VALUE);
            if (currentG >= maxSteps) continue;

            for (GridPos dir : DIRECTIONS) {
                GridPos neighbor = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (closed.contains(neighbor)) continue;

                // Check all footprint tiles at this anchor (except allow destination)
                if (!neighbor.equals(to) && !canPlaceSizedEntity(arena, neighbor, entitySize, self, false)) continue;
                if (neighbor.equals(to) && !canPlaceSizedEntity(arena, neighbor, entitySize, self, false)) continue;

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

    private static List<GridPos> reconstructSizedPath(Map<GridPos, GridPos> cameFrom, GridPos end,
                                                        int maxSteps, GridArena arena,
                                                        CombatEntity self, int entitySize) {
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

        // Trim if the truncated endpoint is occupied
        while (!path.isEmpty() && !canPlaceSizedEntity(arena, path.get(path.size() - 1), entitySize, self, false)) {
            path.remove(path.size() - 1);
        }

        return path;
    }
}
