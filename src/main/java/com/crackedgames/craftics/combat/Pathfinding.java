package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

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
        return findPathSized(arena, from, to, maxSteps, self, entitySize, false);
    }

    /**
     * Size-aware pathfinding with optional obstacle bypass.
     * When {@code ignoreObstacles} is true, OBSTACLE tiles are walkable (used by spiders that jump over them).
     */
    public static List<GridPos> findPathSized(GridArena arena, GridPos from, GridPos to,
                                               int maxSteps, CombatEntity self, int entitySize, boolean ignoreObstacles) {
        if (entitySize <= 1) return findPath(arena, from, to, maxSteps, self, false, ignoreObstacles);
        return findPathSizedInternal(arena, from, to, maxSteps, self, entitySize, ignoreObstacles);
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
        return findPath(arena, from, to, maxSteps, self, hasBoat, ignoreObstacles, false);
    }

    /**
     * A* pathfinding with full options including aquatic mode.
     * When aquatic is true, WATER and DEEP_WATER tiles are walkable.
     */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, CombatEntity self, boolean hasBoat, boolean ignoreObstacles, boolean aquatic) {
        if (from.equals(to)) return List.of();
        if (!arena.isInBounds(to)) return List.of();

        var tile = arena.getTile(to);
        if (tile == null || !tile.isWalkableEx(hasBoat, ignoreObstacles, aquatic)) return List.of();
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
                if (neighborTile == null || !neighborTile.isWalkableEx(hasBoat, ignoreObstacles, aquatic)) continue;
                // Block enemy-occupied tiles (excluding self and the destination).
                // Allies are passable for intermediate tiles — you can move through them but not stop on them.
                if (!neighbor.equals(to) && isBlockedBy(arena, neighbor, self, false)) continue;

                int moveCost = neighborTile.getMoveCost();
                int tentativeG = currentG + moveCost;
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
     * Check if a tile is blocked by an entity or the player (excluding 'self' if non-null).
     * Defaults to treating the tile as a final destination — any occupant blocks.
     */
    private static boolean isBlockedBy(GridArena arena, GridPos pos, CombatEntity self) {
        return isBlockedBy(arena, pos, self, true);
    }

    /**
     * Check if a tile is blocked. When {@code isFinalDestination} is false, allies (and the player,
     * for ally-controlled selves) are passable — units can move through them but cannot stop on them.
     */
    private static boolean isBlockedBy(GridArena arena, GridPos pos, CombatEntity self, boolean isFinalDestination) {
        // Player tile: allies passing through (not stopping) may step onto it; everyone else blocks
        if (pos.equals(arena.getPlayerGridPos())) {
            if (!isFinalDestination && self != null && self.isAlly()) return false;
            return true;
        }
        CombatEntity occupant = arena.getOccupant(pos);
        if (occupant == null) return false;
        // Don't block on our own tile
        if (self != null && occupant == self) return false;
        // Boss entities can walk through passable entities (e.g., egg sacs)
        if (self != null && self.isBoss() && occupant.isPassableForBoss()) return false;
        // Allies are passable for the player and other allies during traversal,
        // but still block as a final destination (you can't stop on them).
        if (!isFinalDestination && occupant.isAlly() && (self == null || self.isAlly())) {
            return false;
        }
        return true;
    }

    /**
     * Check if ALL tiles of a sized entity's footprint at a given anchor position are valid.
     * For size 1, this is equivalent to checking a single tile.
     */
    private static boolean canPlaceSizedEntity(GridArena arena, GridPos anchor, int entitySize,
                                                CombatEntity self, boolean hasBoat) {
        return canPlaceSizedEntity(arena, anchor, entitySize, self, hasBoat, false, true);
    }

    private static boolean canPlaceSizedEntity(GridArena arena, GridPos anchor, int entitySize,
                                                CombatEntity self, boolean hasBoat, boolean ignoreObstacles) {
        return canPlaceSizedEntity(arena, anchor, entitySize, self, hasBoat, ignoreObstacles, true);
    }

    /**
     * @param isFinalDestination when false, allies do not block (passable for traversal).
     */
    private static boolean canPlaceSizedEntity(GridArena arena, GridPos anchor, int entitySize,
                                                CombatEntity self, boolean hasBoat, boolean ignoreObstacles,
                                                boolean isFinalDestination) {
        for (GridPos tile : GridArena.getOccupiedTiles(anchor, entitySize)) {
            if (!arena.isInBounds(tile)) return false;
            var gridTile = arena.getTile(tile);
            if (gridTile == null || !gridTile.isWalkableEx(hasBoat, ignoreObstacles)) return false;
            if (isBlockedBy(arena, tile, self, isFinalDestination)) return false;
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
        return getReachableTiles(arena, from, maxSteps, 1, null, hasBoat, ignoreObstacles, false);
    }

    /** Overload with hasBoat, ignoreObstacles, and ignoreHazardCost for player movement highlights. */
    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps, boolean hasBoat, boolean ignoreObstacles, boolean ignoreHazardCost) {
        return getReachableTiles(arena, from, maxSteps, 1, null, hasBoat, ignoreObstacles, ignoreHazardCost);
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
        return getReachableTiles(arena, from, maxSteps, entitySize, self, hasBoat, false, false);
    }

    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   int entitySize, CombatEntity self, boolean hasBoat, boolean ignoreObstacles) {
        return getReachableTiles(arena, from, maxSteps, entitySize, self, hasBoat, ignoreObstacles, false);
    }

    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   int entitySize, CombatEntity self, boolean hasBoat, boolean ignoreObstacles, boolean ignoreHazardCost) {
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

                // Traversal check: allies are passable so paths can route through them.
                if (!canPlaceSizedEntity(arena, neighbor, entitySize, self, hasBoat, ignoreObstacles, false)) continue;

                // Hazard cost: use highest move cost across the footprint
                int moveCost = 1;
                if (!ignoreHazardCost) {
                    for (GridPos ft : GridArena.getOccupiedTiles(neighbor, entitySize)) {
                        var ft_tile = arena.getTile(ft);
                        if (ft_tile != null) moveCost = Math.max(moveCost, ft_tile.getMoveCost());
                    }
                }
                int newDist = currentDist + moveCost;
                if (newDist > maxSteps) continue;
                if (dist.containsKey(neighbor) && dist.get(neighbor) <= newDist) continue;
                dist.put(neighbor, newDist);
                queue.add(neighbor);
                // Stop check: ally tiles are not valid landing spots, so they aren't highlighted.
                if (canPlaceSizedEntity(arena, neighbor, entitySize, self, hasBoat, ignoreObstacles, true)) {
                    reachable.add(neighbor);
                }
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

                int stepCost = tile.getMoveCost();
                int newDist = currentDist + stepCost;
                if (newDist > maxSteps) continue;
                dist.put(neighbor, newDist);
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
                                                         int maxSteps, CombatEntity self, int entitySize,
                                                         boolean ignoreObstacles) {
        if (from.equals(to)) return List.of();
        if (!canPlaceSizedEntity(arena, to, entitySize, self, false, ignoreObstacles)) return List.of();

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
                if (!canPlaceSizedEntity(arena, neighbor, entitySize, self, false, ignoreObstacles)) continue;

                // Use highest move cost across the footprint
                int moveCost = 1;
                for (GridPos ft : GridArena.getOccupiedTiles(neighbor, entitySize)) {
                    var ft_tile = arena.getTile(ft);
                    if (ft_tile != null) moveCost = Math.max(moveCost, ft_tile.getMoveCost());
                }
                int tentativeG = currentG + moveCost;
                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeG);
                    open.add(neighbor);
                }
            }
        }

        return List.of();
    }

    /**
     * Checks line-of-sight between two grid positions using a Bresenham-style trace.
     * Returns false if any OBSTACLE tile lies between {@code from} and {@code to}
     * (the endpoints themselves are not checked). Used for ranged attacks — projectiles
     * cannot fly over obstacles.
     */
    public static boolean hasLineOfSight(GridArena arena, GridPos from, GridPos to) {
        if (from.equals(to)) return true;
        int x0 = from.x();
        int z0 = from.z();
        int x1 = to.x();
        int z1 = to.z();

        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        int x = x0;
        int z = z0;
        while (true) {
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x += sx; }
            if (e2 < dx) { err += dx; z += sz; }
            // Reached the target — no obstacle was in the way
            if (x == x1 && z == z1) return true;
            // Inspect the intermediate tile
            GridPos step = new GridPos(x, z);
            if (!arena.isInBounds(step)) return false;
            var tile = arena.getTile(step);
            if (tile != null && tile.getType() == TileType.OBSTACLE) return false;
        }
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
