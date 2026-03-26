package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;

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
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (GridPos dir : CARDINALS) {
            GridPos adj = new GridPos(playerPos.x() + dir.x(), playerPos.z() + dir.z());
            if (!arena.isInBounds(adj)) continue;
            GridTile tile = arena.getTile(adj);
            if (tile == null || !tile.isWalkable()) continue;
            if (arena.isEnemyOccupied(adj)) continue;

            int dist = self.manhattanDistance(adj);
            if (dist < bestDist) {
                bestDist = dist;
                best = adj;
            }
        }
        return best;
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
     * Universal AI fallback: move toward the player if possible, otherwise wander.
     * Should be called by any AI that would otherwise return Idle.
     * Returns a Move action, or Idle only if truly boxed in.
     */
    public static EnemyAction seekOrWander(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Try to find the closest reachable tile to the player
        GridPos closest = Pathfinding.findClosestReachableTo(
            arena, self.getGridPos(), playerPos, self.getMoveSpeed(), self);

        if (closest != null && !closest.equals(self.getGridPos())) {
            List<GridPos> path = Pathfinding.findPath(arena, self.getGridPos(), closest, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                // Check if we end up adjacent — attack too
                GridPos endPos = path.get(path.size() - 1);
                if (CombatEntity.minDistanceFromSizedEntity(endPos, self.getSize(), playerPos) <= 1) {
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
