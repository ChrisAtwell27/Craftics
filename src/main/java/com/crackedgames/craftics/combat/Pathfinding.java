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

    // ── Jumping ──────────────────────────────────────────────────────────────

    /** Widest gap a player can clear. 2 tiles => the jump lands 3 away and costs 4. */
    public static final int MAX_JUMP_GAP = 2;

    /**
     * Speed a jump costs: what the walk WOULD have cost with no gap in the way, plus one.
     * A gap of N tiles means landing N+1 tiles away, so the cost is (N + 1) + 1.
     *
     * <p>Deliberately independent of what is IN the gap. Charging the skipped tiles' own move
     * cost would make hopping lava more expensive than wading through it, which defeats the
     * entire point; a flat "distance + 1" is also a rule a player can hold in their head.
     */
    public static int jumpCost(int gapTiles) {
        return gapTiles + 2;
    }

    /**
     * Tiles a player may jump OVER.
     *
     * <p>Deliberately excludes OBSTACLE (it stands above the arena floor - you would be jumping
     * through it, not over it) and POWDER_SNOW (a concealed trap; letting players hop it for a
     * flat cost would defuse the one thing it is for). Everything else here is either lethal to
     * enter (VOID, DEEP_WATER) or punishing (LAVA, FIRE, WATER), so clearing it should be a real
     * option rather than a forced detour.
     */
    private static boolean isJumpableGap(GridArena arena, GridPos pos) {
        var tile = arena.getTile(pos);
        if (tile == null) return false;
        return switch (tile.getType()) {
            case VOID, DEEP_WATER, LAVA, FIRE, WATER -> true;
            default -> false;
        };
    }

    /**
     * A resolved move: the tiles walked or landed on, and the REAL speed it costs.
     *
     * <p>The cost has to be carried explicitly. Before jumps existed, every step cost 1 and
     * callers could infer the price from {@code steps.size()} - and they did, all over
     * CombatManager. A jump breaks that: clearing a one-tile pit is a SINGLE entry in the step
     * list but costs 3 speed. Anything that charges movement must read {@link #cost()}.
     *
     * @param steps      tiles to move through, in order, excluding the start tile
     * @param cost       speed actually spent
     * @param jumpedOver tiles cleared mid-air - never stood on. Drawn as arrows, not path dots.
     */
    public record Path(List<GridPos> steps, int cost, List<GridPos> jumpedOver) {
        public static final Path EMPTY = new Path(List.of(), 0, List.of());
        public boolean isEmpty() { return steps.isEmpty(); }
    }

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

    /**
     * Player pathfinding with boat access, optional obstacle ignoring, and optional
     * phase-through-enemies mode (Helium Flamingo). When {@code phaseThroughEnemies}
     * is true, occupied tiles are walkable for traversal but not as the final destination.
     */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, boolean hasBoat, boolean ignoreObstacles, boolean phaseThroughEnemies) {
        return findPathFull(arena, from, to, maxSteps, null, hasBoat, ignoreObstacles, false, phaseThroughEnemies, false);
    }

    /**
     * Player-facing pathfinding that treats hazard tiles (LAVA/FIRE) as cost 1
     * so the player can walk freely across magma and fire. Damage still applies
     * at move-tick time - this only removes the path-disruption behavior.
     * Enemy pathfinding retains the high hazard cost so AI avoids stepping into hazards.
     */
    public static List<GridPos> findPathPlayer(GridArena arena, GridPos from, GridPos to, int maxSteps, boolean hasBoat, boolean ignoreObstacles, boolean phaseThroughEnemies) {
        return findPathFull(arena, from, to, maxSteps, null, hasBoat, ignoreObstacles, false, phaseThroughEnemies, true);
    }

    /** Enemy pathfinding (no boat access). */
    public static List<GridPos> findPath(GridArena arena, GridPos from, GridPos to, int maxSteps, CombatEntity self) {
        return findPath(arena, from, to, maxSteps, self, false);
    }

    /** Size-aware enemy pathfinding. For entities > 1x1, checks full footprint at each position. */
    public static List<GridPos> findPathSized(GridArena arena, GridPos from, GridPos to,
                                               int maxSteps, CombatEntity self, int entitySize) {
        return findPathSized(arena, from, to, maxSteps, self, entitySize, entitySize, false);
    }

    /** Footprint-aware enemy pathfinding using the entity's own (possibly rectangular) footprint. */
    public static List<GridPos> findPathSized(GridArena arena, GridPos from, GridPos to,
                                               int maxSteps, CombatEntity self) {
        return findPathSized(arena, from, to, maxSteps, self, self.getSizeX(), self.getSizeZ(), false);
    }

    /**
     * Size-aware pathfinding with optional obstacle bypass.
     * When {@code ignoreObstacles} is true, OBSTACLE tiles are walkable (used by spiders that jump over them).
     */
    public static List<GridPos> findPathSized(GridArena arena, GridPos from, GridPos to,
                                               int maxSteps, CombatEntity self, int entitySize, boolean ignoreObstacles) {
        return findPathSized(arena, from, to, maxSteps, self, entitySize, entitySize, ignoreObstacles);
    }

    /** Rectangular-footprint pathfinding - the variant everything else delegates to. */
    public static List<GridPos> findPathSized(GridArena arena, GridPos from, GridPos to,
                                               int maxSteps, CombatEntity self, int sizeX, int sizeZ, boolean ignoreObstacles) {
        if (sizeX <= 1 && sizeZ <= 1) return findPath(arena, from, to, maxSteps, self, false, ignoreObstacles);
        return findPathSizedInternal(arena, from, to, maxSteps, self, sizeX, sizeZ, ignoreObstacles);
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
        return findPathFull(arena, from, to, maxSteps, self, hasBoat, ignoreObstacles, aquatic, false, false);
    }

    /**
     * A* pathfinding with the full option set including {@code phaseThroughEnemies}
     * (Helium Flamingo). When phaseThroughEnemies is true, occupant blocking is
     * skipped for intermediate path tiles - the destination still cannot be
     * occupied (can't stop on top of an enemy).
     */
    public static List<GridPos> findPathFull(GridArena arena, GridPos from, GridPos to, int maxSteps, CombatEntity self, boolean hasBoat, boolean ignoreObstacles, boolean aquatic, boolean phaseThroughEnemies) {
        return findPathFull(arena, from, to, maxSteps, self, hasBoat, ignoreObstacles, aquatic, phaseThroughEnemies, false);
    }

    /**
     * A* pathfinding with full options. When {@code ignoreHazardCost} is true,
     * LAVA/FIRE tiles cost 1 to step through instead of 50 - used for player
     * movement so magma/fire behave like cobweb (damage on step but no path
     * disruption) while leaving AI pathfinding untouched so enemies still
     * naturally avoid hazards.
     */
    /**
     * Player pathfinding that may JUMP gaps it cannot walk through.
     *
     * <p>Jumps are ordinary graph edges - a hop to the tile N+1 away, costing {@link #jumpCost}
     * - so A* takes one only when it genuinely beats walking around. There is no "should I jump?"
     * heuristic to get wrong: if the detour is cheaper, it walks.
     *
     * <p>A jump is legal when every tile in the gap is {@link #isJumpableGap}, none of them is
     * occupied (you cannot vault over an enemy), and the landing tile is one you could normally
     * stand on. You may jump OVER lava; you may not land IN it.
     *
     * <p>AI keeps using the plain {@code findPathFull} - enemies do not jump.
     */
    public static Path findPlayerPathWithJumps(GridArena arena, GridPos from, GridPos to, int maxSpeed,
                                               boolean hasBoat, boolean ignoreObstacles,
                                               boolean phaseThroughEnemies) {
        if (from.equals(to) || !arena.isInBounds(to)) return Path.EMPTY;
        var destTile = arena.getTile(to);
        if (destTile == null || !destTile.isWalkableEx(hasBoat, ignoreObstacles, false)) return Path.EMPTY;
        if (isBlockedBy(arena, to, null)) return Path.EMPTY;

        Map<GridPos, GridPos> cameFrom = new HashMap<>();
        Map<GridPos, Integer> gScore = new HashMap<>();
        gScore.put(from, 0);

        PriorityQueue<GridPos> open = new PriorityQueue<>(
            Comparator.comparingInt(p -> gScore.getOrDefault(p, Integer.MAX_VALUE) + p.manhattanDistance(to)));
        open.add(from);
        Set<GridPos> closed = new HashSet<>();

        while (!open.isEmpty()) {
            GridPos current = open.poll();
            if (current.equals(to)) break;
            if (!closed.add(current)) continue;

            int currentG = gScore.getOrDefault(current, Integer.MAX_VALUE);
            if (currentG >= maxSpeed) continue;

            for (GridPos dir : DIRECTIONS) {
                // --- ordinary 1-tile step ---
                GridPos step = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (arena.isInBounds(step) && !closed.contains(step)) {
                    var t = arena.getTile(step);
                    boolean walkable = t != null && t.isWalkableEx(hasBoat, ignoreObstacles, false);
                    boolean free = step.equals(to)
                        || (phaseThroughEnemies ? !step.equals(arena.getPlayerGridPos())
                                                : !isBlockedBy(arena, step, null, false));
                    if (walkable && free) {
                        // Player movement treats LAVA/FIRE as cost 1 (they damage on step but
                        // must not warp pathing), matching findPathPlayer's ignoreHazardCost.
                        relax(open, gScore, cameFrom, current, step, currentG + 1, maxSpeed);
                    }
                }

                // --- jump over a gap of 1..MAX_JUMP_GAP tiles, landing beyond it ---
                for (int gap = 1; gap <= MAX_JUMP_GAP; gap++) {
                    // Every tile in the gap must be jumpable AND empty.
                    boolean clear = true;
                    for (int g = 1; g <= gap && clear; g++) {
                        GridPos over = new GridPos(current.x() + dir.x() * g, current.z() + dir.z() * g);
                        if (!arena.isInBounds(over) || !isJumpableGap(arena, over)
                                || arena.getOccupant(over) != null) {
                            clear = false;
                        }
                    }
                    if (!clear) continue;

                    GridPos land = new GridPos(current.x() + dir.x() * (gap + 1),
                                               current.z() + dir.z() * (gap + 1));
                    if (!arena.isInBounds(land) || closed.contains(land)) continue;
                    var lt = arena.getTile(land);
                    // Land ON solid ground only: you may clear lava, never end your jump in it.
                    if (lt == null || !lt.isWalkableEx(hasBoat, ignoreObstacles, false)) continue;
                    if (isJumpableGap(arena, land)) continue;
                    if (isBlockedBy(arena, land, null)) continue;

                    relax(open, gScore, cameFrom, current, land, currentG + jumpCost(gap), maxSpeed);
                }
            }
        }

        if (!cameFrom.containsKey(to)) return Path.EMPTY;
        return buildJumpPath(cameFrom, gScore, from, to);
    }

    /** Standard A* edge relaxation, bounded by the mover's speed budget. */
    private static void relax(PriorityQueue<GridPos> open, Map<GridPos, Integer> gScore,
                              Map<GridPos, GridPos> cameFrom, GridPos from, GridPos to,
                              int tentativeG, int maxSpeed) {
        if (tentativeG > maxSpeed) return;
        if (tentativeG >= gScore.getOrDefault(to, Integer.MAX_VALUE)) return;
        cameFrom.put(to, from);
        gScore.put(to, tentativeG);
        open.add(to);
    }

    /**
     * Walk the parent chain back and split it into steps + the tiles that were vaulted.
     * The cost is read from gScore, NOT inferred from the step count - that is the whole
     * point of the Path record.
     */
    private static Path buildJumpPath(Map<GridPos, GridPos> cameFrom, Map<GridPos, Integer> gScore,
                                      GridPos from, GridPos to) {
        List<GridPos> steps = new ArrayList<>();
        List<GridPos> jumped = new ArrayList<>();
        GridPos current = to;
        while (!current.equals(from)) {
            GridPos prev = cameFrom.get(current);
            if (prev == null) return Path.EMPTY; // broken chain - refuse rather than half-move
            steps.add(current);
            int dx = Integer.signum(current.x() - prev.x());
            int dz = Integer.signum(current.z() - prev.z());
            int dist = Math.abs(current.x() - prev.x()) + Math.abs(current.z() - prev.z());
            for (int g = 1; g < dist; g++) { // the tiles between prev and current were jumped
                jumped.add(new GridPos(prev.x() + dx * g, prev.z() + dz * g));
            }
            current = prev;
        }
        Collections.reverse(steps);
        return new Path(steps, gScore.getOrDefault(to, 0), jumped);
    }

    public static List<GridPos> findPathFull(GridArena arena, GridPos from, GridPos to, int maxSteps, CombatEntity self, boolean hasBoat, boolean ignoreObstacles, boolean aquatic, boolean phaseThroughEnemies, boolean ignoreHazardCost) {
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
                // Allies are passable for intermediate tiles - you can move through them but not stop on them.
                // Helium Flamingo: also pass through enemies on intermediate tiles.
                if (!neighbor.equals(to)) {
                    if (!phaseThroughEnemies && isBlockedBy(arena, neighbor, self, false)) continue;
                    if (phaseThroughEnemies && neighbor.equals(arena.getPlayerGridPos())) continue;
                }

                int moveCost = ignoreHazardCost ? 1 : neighborTile.getMoveCost();
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
     * Defaults to treating the tile as a final destination - any occupant blocks.
     */
    private static boolean isBlockedBy(GridArena arena, GridPos pos, CombatEntity self) {
        return isBlockedBy(arena, pos, self, true);
    }

    /**
     * Check if a tile is blocked. When {@code isFinalDestination} is false, allies (and the player,
     * for ally-controlled selves) are passable - units can move through them but cannot stop on them.
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
        // The netherite mount's 1x3 wall sentinel never blocks the rider's own pathing.
        // Player movement pathfinds with self == null; the sentinel is the rider's own
        // footprint, so the rider may traverse AND stop on its side tiles (and they stay
        // in the move-range highlight). Enemies pass a non-null hostile self and fall
        // through to the ally block below, so the wall still blocks them.
        if (occupant.isMountWall() && self == null) return false;
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
    private static boolean canPlaceSizedEntity(GridArena arena, GridPos anchor, int sizeX, int sizeZ,
                                                CombatEntity self, boolean hasBoat) {
        return canPlaceSizedEntity(arena, anchor, sizeX, sizeZ, self, hasBoat, false, true);
    }

    private static boolean canPlaceSizedEntity(GridArena arena, GridPos anchor, int sizeX, int sizeZ,
                                                CombatEntity self, boolean hasBoat, boolean ignoreObstacles) {
        return canPlaceSizedEntity(arena, anchor, sizeX, sizeZ, self, hasBoat, ignoreObstacles, true);
    }

    /**
     * @param isFinalDestination when false, allies do not block (passable for traversal).
     */
    private static boolean canPlaceSizedEntity(GridArena arena, GridPos anchor, int sizeX, int sizeZ,
                                                CombatEntity self, boolean hasBoat, boolean ignoreObstacles,
                                                boolean isFinalDestination) {
        for (GridPos tile : GridArena.getOccupiedTiles(anchor, sizeX, sizeZ)) {
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
        return getReachableTiles(arena, from, maxSteps, entitySize, entitySize, self, false, false, false);
    }

    /** Footprint-aware reachability using the entity's own (possibly rectangular) footprint. */
    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   CombatEntity self) {
        return getReachableTiles(arena, from, maxSteps, self.getSizeX(), self.getSizeZ(), self, false, false, false);
    }

    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   int entitySize, CombatEntity self, boolean hasBoat) {
        return getReachableTiles(arena, from, maxSteps, entitySize, entitySize, self, hasBoat, false, false);
    }

    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   int entitySize, CombatEntity self, boolean hasBoat, boolean ignoreObstacles) {
        return getReachableTiles(arena, from, maxSteps, entitySize, entitySize, self, hasBoat, ignoreObstacles, false);
    }

    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   int entitySize, CombatEntity self, boolean hasBoat, boolean ignoreObstacles, boolean ignoreHazardCost) {
        return getReachableTiles(arena, from, maxSteps, entitySize, entitySize, self, hasBoat, ignoreObstacles, ignoreHazardCost);
    }

    /**
     * Player reachability INCLUDING tiles only reachable by jumping a gap.
     *
     * <p>Must mirror {@link #findPlayerPathWithJumps} exactly: a tile the pathfinder would
     * happily route to but that is not highlighted here can never be clicked, and a tile
     * highlighted here that the pathfinder refuses would be a dead click. Same edges, same costs.
     */
    public static Set<GridPos> getPlayerReachableTilesWithJumps(GridArena arena, GridPos from,
                                                                int maxSpeed, boolean hasBoat,
                                                                boolean ignoreObstacles) {
        Set<GridPos> reachable = new HashSet<>();
        Map<GridPos, Integer> dist = new HashMap<>();
        PriorityQueue<GridPos> open = new PriorityQueue<>(
            Comparator.comparingInt(p -> dist.getOrDefault(p, Integer.MAX_VALUE)));
        dist.put(from, 0);
        open.add(from);

        while (!open.isEmpty()) {
            GridPos current = open.poll();
            int currentDist = dist.getOrDefault(current, Integer.MAX_VALUE);
            if (currentDist >= maxSpeed) continue;

            for (GridPos dir : DIRECTIONS) {
                // 1-tile step
                GridPos step = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (arena.isInBounds(step)) {
                    var t = arena.getTile(step);
                    if (t != null && t.isWalkableEx(hasBoat, ignoreObstacles, false)
                            && !isBlockedBy(arena, step, null, false)) {
                        int nd = currentDist + 1;
                        if (nd <= maxSpeed && nd < dist.getOrDefault(step, Integer.MAX_VALUE)) {
                            dist.put(step, nd);
                            open.add(step);
                            if (!isBlockedBy(arena, step, null)) reachable.add(step);
                        }
                    }
                }

                // jump over a gap
                for (int gap = 1; gap <= MAX_JUMP_GAP; gap++) {
                    boolean clear = true;
                    for (int g = 1; g <= gap && clear; g++) {
                        GridPos over = new GridPos(current.x() + dir.x() * g, current.z() + dir.z() * g);
                        if (!arena.isInBounds(over) || !isJumpableGap(arena, over)
                                || arena.getOccupant(over) != null) {
                            clear = false;
                        }
                    }
                    if (!clear) continue;

                    GridPos land = new GridPos(current.x() + dir.x() * (gap + 1),
                                               current.z() + dir.z() * (gap + 1));
                    if (!arena.isInBounds(land)) continue;
                    var lt = arena.getTile(land);
                    if (lt == null || !lt.isWalkableEx(hasBoat, ignoreObstacles, false)) continue;
                    if (isJumpableGap(arena, land)) continue;
                    if (isBlockedBy(arena, land, null)) continue;

                    int nd = currentDist + jumpCost(gap);
                    if (nd <= maxSpeed && nd < dist.getOrDefault(land, Integer.MAX_VALUE)) {
                        dist.put(land, nd);
                        open.add(land);
                        reachable.add(land);
                    }
                }
            }
        }
        return reachable;
    }

    /** Rectangular-footprint reachability - the variant everything else delegates to. */
    public static Set<GridPos> getReachableTiles(GridArena arena, GridPos from, int maxSteps,
                                                   int sizeX, int sizeZ, CombatEntity self, boolean hasBoat, boolean ignoreObstacles, boolean ignoreHazardCost) {
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
                if (!canPlaceSizedEntity(arena, neighbor, sizeX, sizeZ, self, hasBoat, ignoreObstacles, false)) continue;

                // Hazard cost: use highest move cost across the footprint
                int moveCost = 1;
                if (!ignoreHazardCost) {
                    for (GridPos ft : GridArena.getOccupiedTiles(neighbor, sizeX, sizeZ)) {
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
                if (canPlaceSizedEntity(arena, neighbor, sizeX, sizeZ, self, hasBoat, ignoreObstacles, true)) {
                    reachable.add(neighbor);
                }
            }
        }

        return reachable;
    }

    /**
     * Find the reachable tile within maxSteps that is closest to the target position.
     * Used as an AI fallback - "get as close as possible to the player."
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
        return findClosestReachableTo(arena, from, target, maxSteps, self, entitySize, entitySize);
    }

    /** Rectangular-footprint variant of findClosestReachableTo. */
    public static GridPos findClosestReachableTo(GridArena arena, GridPos from, GridPos target,
                                                   int maxSteps, CombatEntity self, int sizeX, int sizeZ) {
        if (sizeX <= 1 && sizeZ <= 1) return findClosestReachableTo(arena, from, target, maxSteps, self);

        Set<GridPos> reachable = getReachableTiles(arena, from, maxSteps, sizeX, sizeZ, self, false, false, false);

        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos pos : reachable) {
            int d = CombatEntity.minDistanceFromSizedEntity(pos, sizeX, sizeZ, target);
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
                                                         int maxSteps, CombatEntity self, int sizeX, int sizeZ,
                                                         boolean ignoreObstacles) {
        if (from.equals(to)) return List.of();
        if (!canPlaceSizedEntity(arena, to, sizeX, sizeZ, self, false, ignoreObstacles)) return List.of();

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
                return reconstructSizedPath(cameFrom, to, maxSteps, arena, self, sizeX, sizeZ);
            }

            if (closed.contains(current)) continue;
            closed.add(current);

            int currentG = gScore.getOrDefault(current, Integer.MAX_VALUE);
            if (currentG >= maxSteps) continue;

            for (GridPos dir : DIRECTIONS) {
                GridPos neighbor = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (closed.contains(neighbor)) continue;

                // Check all footprint tiles at this anchor (except allow destination)
                if (!canPlaceSizedEntity(arena, neighbor, sizeX, sizeZ, self, false, ignoreObstacles)) continue;

                // Use highest move cost across the footprint
                int moveCost = 1;
                for (GridPos ft : GridArena.getOccupiedTiles(neighbor, sizeX, sizeZ)) {
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
     * (the endpoints themselves are not checked). Used for ranged attacks - projectiles
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
            // Reached the target - no obstacle was in the way
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
                                                        CombatEntity self, int sizeX, int sizeZ) {
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
        while (!path.isEmpty() && !canPlaceSizedEntity(arena, path.get(path.size() - 1), sizeX, sizeZ, self, false)) {
            path.remove(path.size() - 1);
        }

        return path;
    }
}
