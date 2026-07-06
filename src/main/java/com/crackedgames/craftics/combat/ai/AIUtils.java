package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

public class AIUtils {

    private static final GridPos[] CARDINALS = {
        new GridPos(0, 1), new GridPos(0, -1),
        new GridPos(1, 0), new GridPos(-1, 0)
    };

    /**
     * Get walkable, unoccupied tiles adjacent to a position.
     */
    public static List<GridPos> getAdjacentTiles(GridArena arena, GridPos pos) {
        List<GridPos> result = new ArrayList<>();
        for (GridPos dir : CARDINALS) {
            GridPos neighbor = new GridPos(pos.x() + dir.x(), pos.z() + dir.z());
            if (arena.isInBounds(neighbor)) {
                GridTile tile = arena.getTile(neighbor);
                if (tile != null && tile.isWalkable() && !arena.isOccupied(neighbor)) {
                    result.add(neighbor);
                }
            }
        }
        return result;
    }

    /**
     * Find the best tile adjacent to the player that this entity can path to.
     */
    public static GridPos findBestAdjacentTarget(GridArena arena, GridPos self, GridPos playerPos, int maxSteps) {
        return findBestAdjacentTarget(arena, self, playerPos, maxSteps, 1);
    }

    /**
     * Size-aware version: checks that ALL footprint tiles at each candidate are valid.
     */
    public static GridPos findBestAdjacentTarget(GridArena arena, GridPos self, GridPos playerPos, int maxSteps, int entitySize) {
        return findBestAdjacentTarget(arena, self, playerPos, maxSteps, entitySize, entitySize);
    }

    /** Rectangular-footprint version of findBestAdjacentTarget. */
    public static GridPos findBestAdjacentTarget(GridArena arena, GridPos self, GridPos playerPos, int maxSteps, int sizeX, int sizeZ) {
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (GridPos dir : CARDINALS) {
            GridPos adj = new GridPos(playerPos.x() + dir.x(), playerPos.z() + dir.z());
            if (!canPlaceFootprint(arena, adj, sizeX, sizeZ)) continue;

            int dist = self.manhattanDistance(adj);
            if (dist < bestDist) {
                bestDist = dist;
                best = adj;
            }
        }
        return best;
    }

    /**
     * Check if a sized entity can be placed at an anchor position (all footprint tiles valid).
     */
    public static boolean canPlaceFootprint(GridArena arena, GridPos anchor, int entitySize) {
        return canPlaceFootprint(arena, anchor, entitySize, entitySize);
    }

    /** Rectangular-footprint version of canPlaceFootprint. */
    public static boolean canPlaceFootprint(GridArena arena, GridPos anchor, int sizeX, int sizeZ) {
        for (GridPos tile : GridArena.getOccupiedTiles(anchor, sizeX, sizeZ)) {
            if (!arena.isInBounds(tile)) return false;
            GridTile gridTile = arena.getTile(tile);
            if (gridTile == null || !gridTile.isWalkable()) return false;
            if (arena.isEnemyOccupied(tile)) return false;
        }
        return true;
    }

    /**
     * Footprint check for a mob's own charge/bounce landing: tiles the mob
     * currently occupies count as free (it vacates them by moving). Without
     * this, any multi-tile mob's short hop overlaps its own footprint and is
     * wrongly rejected. The player's tile still blocks.
     */
    public static boolean canPlaceFootprintIgnoringSelf(GridArena arena, GridPos anchor, CombatEntity self) {
        for (GridPos tile : GridArena.getOccupiedTiles(anchor, self.getSizeX(), self.getSizeZ())) {
            if (!arena.isInBounds(tile)) return false;
            GridTile gridTile = arena.getTile(tile);
            if (gridTile == null || !gridTile.isWalkable()) return false;
            if (tile.equals(arena.getPlayerGridPos())) return false;
            CombatEntity occupant = arena.getOccupant(tile);
            if (occupant != null && occupant != self && !occupant.isBackgroundBoss()) return false;
        }
        return true;
    }

    /**
     * Check cardinal line of sight between two positions on the same axis.
     * Returns true if they share an axis and all tiles between them are clear.
     */
    public static boolean hasCardinalLOS(GridArena arena, GridPos from, GridPos to, int maxRange) {
        if (from.x() != to.x() && from.z() != to.z()) return false; // not on same axis
        if (from.equals(to)) return false;

        int dist = from.manhattanDistance(to);
        if (dist > maxRange) return false;

        int dx = Integer.signum(to.x() - from.x());
        int dz = Integer.signum(to.z() - from.z());

        GridPos current = new GridPos(from.x() + dx, from.z() + dz);
        while (!current.equals(to)) {
            GridTile tile = arena.getTile(current);
            if (tile == null || !tile.isWalkable()) return false;
            if (arena.isEnemyOccupied(current)) return false;
            current = new GridPos(current.x() + dx, current.z() + dz);
        }
        return true;
    }

    /**
     * Find a flee target: 1-2 tiles away from the threat.
     */
    public static GridPos getFleeTarget(GridArena arena, GridPos self, GridPos threat, int maxSteps) {
        int dx = self.x() - threat.x();
        int dz = self.z() - threat.z();

        // Normalize to primary flee direction
        if (Math.abs(dx) >= Math.abs(dz)) {
            dx = Integer.signum(dx);
            dz = 0;
        } else {
            dx = 0;
            dz = Integer.signum(dz);
        }

        // Try fleeing in primary direction, then secondary
        for (int dist = maxSteps; dist >= 1; dist--) {
            GridPos target = new GridPos(self.x() + dx * dist, self.z() + dz * dist);
            if (arena.isInBounds(target) && !arena.isOccupied(target)) {
                GridTile tile = arena.getTile(target);
                if (tile != null && tile.isWalkable()) {
                    List<GridPos> path = Pathfinding.findPath(arena, self, target, maxSteps);
                    if (!path.isEmpty()) return target;
                }
            }
        }

        // Try perpendicular directions
        int altDx = dz != 0 ? 1 : 0;
        int altDz = dx != 0 ? 1 : 0;
        for (int sign : new int[]{1, -1}) {
            GridPos target = new GridPos(self.x() + altDx * sign, self.z() + altDz * sign);
            if (arena.isInBounds(target) && !arena.isOccupied(target)) {
                GridTile tile = arena.getTile(target);
                if (tile != null && tile.isWalkable()) return target;
            }
        }

        return null; // stuck, can't flee
    }

    /**
     * All positions that currently threaten an enemy in melee: every party
     * player plus every live ally pet. Used by kiting AIs so an archer backs
     * away from the wolf gnawing on it, not just from the player it happens
     * to be targeting.
     */
    public static List<GridPos> threatPositions(GridArena arena, GridPos primaryTarget) {
        List<GridPos> threats = new ArrayList<>(arena.getAllPlayerGridPositions());
        if (threats.isEmpty()) threats.add(primaryTarget);
        java.util.Set<CombatEntity> seen = new java.util.HashSet<>();
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlly() && e.isAlive() && !e.isMountWall() && seen.add(e)) {
                threats.add(e.getGridPos());
            }
        }
        if (!threats.contains(primaryTarget)) threats.add(primaryTarget);
        return threats;
    }

    /** Smallest manhattan distance from {@code pos} to any threat in the list. */
    public static int minThreatDistance(GridPos pos, List<GridPos> threats) {
        int min = Integer.MAX_VALUE;
        for (GridPos t : threats) {
            min = Math.min(min, pos.manhattanDistance(t));
        }
        return min;
    }

    /** True when stepping on (or ending on) this tile hurts - lava, fire, powder snow. */
    public static boolean isHazardTile(GridArena arena, GridPos pos) {
        GridTile tile = arena.getTile(pos);
        return tile != null
            && (tile.getType().damageOnStep > 0 || tile.getType() == TileType.POWDER_SNOW);
    }

    /**
     * Pick the best retreat tile among the tiles actually reachable this turn:
     * maximizes distance from every threat (player + ally pets), refuses tiles
     * that don't gain ground on the nearest threat, and avoids ending on hazards.
     * Unlike {@link #getFleeTarget} this is path-validated, so it finds the
     * around-the-corner escape a straight-line flee misses.
     */
    public static GridPos bestRetreatTile(CombatEntity self, GridArena arena, List<GridPos> threats) {
        GridPos myPos = self.getGridPos();
        int currentMin = minThreatDistance(myPos, threats);
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, myPos, self.getMoveSpeed(), self)) {
            if (candidate.equals(myPos)) continue;
            int minDist = minThreatDistance(candidate, threats);
            if (minDist <= currentMin) continue; // must actually gain ground
            int score = minDist * 10;
            if (isHazardTile(arena, candidate)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Path-validated flee for skittish mobs: run to the reachable tile that
     * maximizes distance from the threat. Falls back to the straight-line
     * {@link #getFleeTarget} only when nothing reachable gains ground, so
     * rabbits and farm animals stop pinning themselves in corners while an
     * open diagonal escape exists.
     */
    public static EnemyAction fleeReachable(CombatEntity self, GridArena arena, GridPos threat, int maxSteps) {
        GridPos myPos = self.getGridPos();
        int currentDist = myPos.manhattanDistance(threat);
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (GridPos candidate : Pathfinding.getReachableTiles(arena, myPos, maxSteps, self.getSizeX(), self.getSizeZ(), self, false, false, false)) {
            if (candidate.equals(myPos)) continue;
            int dist = candidate.manhattanDistance(threat);
            if (dist <= currentDist) continue;
            int score = dist * 10;
            if (isHazardTile(arena, candidate)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            best = getFleeTarget(arena, myPos, threat, maxSteps);
        }
        if (best != null) {
            List<GridPos> path = Pathfinding.findPathSized(arena, myPos, best, maxSteps, self);
            if (!path.isEmpty()) return new EnemyAction.Flee(path);
        }
        return null;
    }

    /**
     * Hit-and-run combo: approach (optional), strike, then spend leftover
     * movement backing away from the victim. Shared by the skirmisher mobs
     * (wolf, fox, ocelot, cave spider, agro cat). Returns {@code null} when
     * there's no movement left or nowhere better to stand - callers fall back
     * to a plain attack.
     */
    public static EnemyAction hitAndRun(CombatEntity self, GridArena arena, GridPos victimPos,
                                        List<GridPos> approachPath, int damage) {
        int speed = self.getMoveSpeed();
        int approachSteps = approachPath != null ? approachPath.size() : 0;
        int remaining = Math.max(0, speed - approachSteps);
        if (remaining <= 0) return null;

        GridPos attackPos = approachSteps > 0 ? approachPath.get(approachSteps - 1) : self.getGridPos();
        java.util.Set<GridPos> reachable = Pathfinding.getReachableTiles(
            arena, attackPos, remaining, self);
        GridPos retreatTarget = null;
        int bestScore = Integer.MIN_VALUE;
        int currentDist = attackPos.manhattanDistance(victimPos);
        for (GridPos pos : reachable) {
            int d = pos.manhattanDistance(victimPos);
            if (d <= currentDist) continue;
            int score = d * 10;
            if (isHazardTile(arena, pos)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                retreatTarget = pos;
            }
        }
        if (retreatTarget == null) return null;

        List<GridPos> retreatPath = Pathfinding.findPath(arena, attackPos, retreatTarget, remaining, self);
        if (retreatPath.isEmpty()) return null;
        return new EnemyAction.MoveAttackMove(
            approachPath != null ? approachPath : List.of(), damage, retreatPath);
    }

    /**
     * Universal AI fallback: move toward the player if possible, otherwise wander.
     * Should be called by any AI that would otherwise return Idle.
     * Returns a Move action, or Idle only if truly boxed in.
     */
    public static EnemyAction seekOrWander(CombatEntity self, GridArena arena, GridPos playerPos) {
        int sizeX = self.getSizeX();
        int sizeZ = self.getSizeZ();
        // Already adjacent? Attack instead of wandering - the path search below
        // returns empty when the only reachable best tile IS the current tile
        // (e.g. blocked in by other party members), and the wander fallback
        // would otherwise make the mob sit there or shuffle off uselessly while
        // the player stands next to it.
        if (CombatEntity.minDistanceFromSizedEntity(self.getGridPos(), sizeX, sizeZ, playerPos) <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }
        // Try to find the closest reachable tile to the player
        GridPos closest = Pathfinding.findClosestReachableTo(
            arena, self.getGridPos(), playerPos, self.getMoveSpeed(), self, sizeX, sizeZ);

        if (closest != null && !closest.equals(self.getGridPos())) {
            List<GridPos> path = Pathfinding.findPathSized(
                arena, self.getGridPos(), closest, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                // Check if we end up adjacent - attack too
                GridPos endPos = path.get(path.size() - 1);
                if (CombatEntity.minDistanceFromSizedEntity(endPos, sizeX, sizeZ, playerPos) <= 1) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }

        return wander(self, arena);
    }

    /**
     * Pure wander: move to a random adjacent walkable tile. Never attacks.
     * For neutral/passive mobs that aren't targeting anyone.
     */
    public static EnemyAction wander(CombatEntity self, GridArena arena) {
        List<GridPos> adjacent = getAdjacentTiles(arena, self.getGridPos());
        if (!adjacent.isEmpty()) {
            // Pick a random adjacent tile (no bias toward any target)
            GridPos pick = adjacent.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(adjacent.size()));
            return new EnemyAction.Move(List.of(pick));
        }
        return new EnemyAction.Idle();
    }
}
