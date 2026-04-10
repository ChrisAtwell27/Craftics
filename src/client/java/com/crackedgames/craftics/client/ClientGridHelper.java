package com.crackedgames.craftics.client;

import com.crackedgames.craftics.core.GridPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Box;

import java.util.*;

/**
 * Client-side grid helper that scans the world to determine
 * walkable tiles, enemy positions, and computes reachability.
 */
public class ClientGridHelper {

    private static final GridPos[] DIRECTIONS = {
        new GridPos(0, 1), new GridPos(0, -1),
        new GridPos(1, 0), new GridPos(-1, 0)
    };

    /**
     * Get all tiles reachable from the player's current position within maxSteps.
     */
    public static Set<GridPos> getReachableTiles(MinecraftClient client, int maxSteps) {
        if (client.player == null || client.world == null) return Set.of();

        GridPos playerPos = getPlayerGridPos(client);
        if (playerPos == null) return Set.of();

        Set<GridPos> enemyPositions = getEnemyGridPositions(client);

        Set<GridPos> reachable = new HashSet<>();
        Map<GridPos, Integer> dist = new HashMap<>();
        Queue<GridPos> queue = new LinkedList<>();

        dist.put(playerPos, 0);
        queue.add(playerPos);

        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();

        while (!queue.isEmpty()) {
            GridPos current = queue.poll();
            int currentDist = dist.get(current);
            if (currentDist >= maxSteps) continue;

            for (GridPos dir : DIRECTIONS) {
                GridPos neighbor = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (neighbor.x() < 0 || neighbor.x() >= w || neighbor.z() < 0 || neighbor.z() >= h) continue;
                if (dist.containsKey(neighbor)) continue;
                if (enemyPositions.contains(neighbor)) continue;

                // Check walkability from world blocks - obstacle blocks aren't walkable
                if (!isTileWalkable(client, neighbor)) continue;

                dist.put(neighbor, currentDist + 1);
                reachable.add(neighbor);
                queue.add(neighbor);
            }
        }

        return reachable;
    }

    /**
     * Get tiles containing enemies within attack range.
     */
    public static Set<GridPos> getAttackableTiles(MinecraftClient client, int range) {
        if (client.player == null) return Set.of();

        GridPos playerPos = getPlayerGridPos(client);
        if (playerPos == null) return Set.of();

        Set<GridPos> enemyPositions = getEnemyGridPositions(client);
        Set<GridPos> attackable = new HashSet<>();

        for (GridPos enemyPos : enemyPositions) {
            // Melee uses Chebyshev (8-directional), ranged uses Manhattan
            int dist = range <= 1
                ? Math.max(Math.abs(playerPos.x() - enemyPos.x()), Math.abs(playerPos.z() - enemyPos.z()))
                : playerPos.manhattanDistance(enemyPos);
            if (dist <= range) {
                attackable.add(enemyPos);
            }
        }

        return attackable;
    }

    public static GridPos getPlayerGridPos(MinecraftClient client) {
        if (client.player == null) return null;
        int gx = (int) Math.floor(client.player.getX()) - CombatState.getArenaOriginX();
        int gz = (int) Math.floor(client.player.getZ()) - CombatState.getArenaOriginZ();
        return new GridPos(gx, gz);
    }

    private static Set<GridPos> getEnemyGridPositions(MinecraftClient client) {
        Set<GridPos> positions = new HashSet<>();
        if (client.world == null) return positions;

        int ox = CombatState.getArenaOriginX();
        int oy = CombatState.getArenaOriginY();
        int oz = CombatState.getArenaOriginZ();
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();

        Box arenaBox = new Box(ox - 1, oy, oz - 1, ox + w + 1, oy + 4, oz + h + 1);
        List<Entity> entities = client.world.getOtherEntities(client.player, arenaBox);
        for (Entity entity : entities) {
            if (entity instanceof MobEntity) {
                int gx = (int) Math.floor(entity.getX()) - ox;
                int gz = (int) Math.floor(entity.getZ()) - oz;
                if (gx >= 0 && gx < w && gz >= 0 && gz < h) {
                    positions.add(new GridPos(gx, gz));
                }
            }
        }
        return positions;
    }

    /**
     * Rook dash + adjustment ring (vindicator, piglin brute).
     * Casts 4 cardinal rays from {@code from} until each one hits an obstacle, an arena edge,
     * or another mob. Then expands a small adjustment ring (default 2 tiles, BFS-restricted)
     * around every dash landing tile so the player can see "where it could end up after a
     * dash + adjust this turn".
     */
    public static Set<GridPos> getRookDashTilesFrom(MinecraftClient client, GridPos from, int adjustRange) {
        Set<GridPos> result = new HashSet<>();
        if (client == null || client.world == null || from == null) return result;
        Set<GridPos> blockers = new HashSet<>(getEnemyGridPositions(client));
        blockers.remove(from);

        // Step 1: cast cardinal rays
        Set<GridPos> dashEndpoints = new HashSet<>();
        for (GridPos dir : DIRECTIONS) {
            GridPos current = from;
            while (true) {
                GridPos next = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (!inArena(next)) break;
                if (blockers.contains(next)) break;
                if (!isTileWalkable(client, next)) break;
                result.add(next);
                current = next;
            }
            if (!current.equals(from)) dashEndpoints.add(current);
        }

        // Step 2: BFS-expand each dash endpoint by the adjustment range
        for (GridPos endpoint : dashEndpoints) {
            result.addAll(getReachableTilesFrom(client, endpoint, adjustRange));
        }

        return result;
    }

    /**
     * Cardinal charge up to {@code chargeLen} tiles per direction (hoglin, ravager),
     * unioned with the regular walk reach as a fallback (these mobs walk if they can't charge).
     */
    public static Set<GridPos> getChargeTilesFrom(MinecraftClient client, GridPos from,
                                                    int walkSteps, int chargeLen) {
        Set<GridPos> result = new HashSet<>(getReachableTilesFrom(client, from, walkSteps));
        if (client == null || client.world == null || from == null) return result;
        Set<GridPos> blockers = new HashSet<>(getEnemyGridPositions(client));
        blockers.remove(from);
        for (GridPos dir : DIRECTIONS) {
            GridPos current = from;
            for (int i = 0; i < chargeLen; i++) {
                GridPos next = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (!inArena(next)) break;
                if (blockers.contains(next)) break;
                if (!isTileWalkable(client, next)) break;
                result.add(next);
                current = next;
            }
        }
        return result;
    }

    /**
     * Free-flight bounce: manhattan diamond ignoring obstacles entirely (magma cube, slime, phantom).
     */
    public static Set<GridPos> getBounceTilesFrom(GridPos from, int radius) {
        Set<GridPos> result = new HashSet<>();
        if (from == null || radius <= 0) return result;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > radius || (dx == 0 && dz == 0)) continue;
                GridPos tile = new GridPos(from.x() + dx, from.z() + dz);
                if (inArena(tile)) result.add(tile);
            }
        }
        return result;
    }

    /**
     * Short-range teleport (endermite, breeze): all walkable tiles within manhattan radius,
     * ignoring whether the path between is clear (it's a hop, not a walk).
     */
    public static Set<GridPos> getBlinkTilesFrom(MinecraftClient client, GridPos from, int radius) {
        Set<GridPos> result = new HashSet<>();
        if (client == null || client.world == null || from == null) return result;
        Set<GridPos> blockers = new HashSet<>(getEnemyGridPositions(client));
        blockers.remove(from);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > radius || (dx == 0 && dz == 0)) continue;
                GridPos tile = new GridPos(from.x() + dx, from.z() + dz);
                if (!inArena(tile)) continue;
                if (blockers.contains(tile)) continue;
                if (!isTileWalkable(client, tile)) continue;
                result.add(tile);
            }
        }
        return result;
    }

    /**
     * Long-range teleport (enderman): every walkable, unoccupied tile in the arena.
     */
    public static Set<GridPos> getTeleportTilesFrom(MinecraftClient client, GridPos from) {
        Set<GridPos> result = new HashSet<>();
        if (client == null || client.world == null) return result;
        Set<GridPos> blockers = new HashSet<>(getEnemyGridPositions(client));
        if (from != null) blockers.remove(from);
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                GridPos tile = new GridPos(x, z);
                if (from != null && tile.equals(from)) continue;
                if (blockers.contains(tile)) continue;
                if (!isTileWalkable(client, tile)) continue;
                result.add(tile);
            }
        }
        return result;
    }

    /**
     * Pounce (spider): the four tiles adjacent to the player that are walkable.
     * The spider's own current tile is irrelevant — pounce range is essentially "land next to you".
     */
    public static Set<GridPos> getPounceTilesFrom(MinecraftClient client, GridPos playerPos) {
        Set<GridPos> result = new HashSet<>();
        if (client == null || client.world == null || playerPos == null) return result;
        Set<GridPos> blockers = new HashSet<>(getEnemyGridPositions(client));
        for (GridPos dir : DIRECTIONS) {
            GridPos tile = new GridPos(playerPos.x() + dir.x(), playerPos.z() + dir.z());
            if (!inArena(tile)) continue;
            if (blockers.contains(tile)) continue;
            if (!isTileWalkable(client, tile)) continue;
            result.add(tile);
        }
        return result;
    }

    private static boolean inArena(GridPos pos) {
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        return pos.x() >= 0 && pos.x() < w && pos.z() >= 0 && pos.z() < h;
    }

    public static boolean isTileWalkable(MinecraftClient client, GridPos pos) {
        if (client.world == null) return false;
        int wx = CombatState.getArenaOriginX() + pos.x();
        int wy = CombatState.getArenaOriginY();
        int wz = CombatState.getArenaOriginZ() + pos.z();

        // Check the block above the floor — solid blocks there are obstacles
        var blockState = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy + 1, wz));
        if (!blockState.isAir()) return false;

        // Floor-level check: void (air with nothing below) is not walkable,
        // but hazard blocks (magma, lava, fire) at floor level ARE walkable
        var floorState = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy, wz));
        if (floorState.isAir()) {
            // Check if there's solid ground below (low ground) or true void
            var belowState = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy - 1, wz));
            return !belowState.isAir() && belowState.getFluidState().isEmpty();
        }
        return true;
    }

    /**
     * Dispatch the correct hover-preview pattern for an enemy based on its movement style.
     * Each {@link com.crackedgames.craftics.combat.MoveStyle} draws a different shape — see
     * each helper method below for details.
     */
    public static Set<GridPos> getMovePatternTiles(MinecraftClient client, GridPos from,
                                                     int maxSteps,
                                                     com.crackedgames.craftics.combat.MoveStyle style,
                                                     GridPos playerPos) {
        if (style == null) style = com.crackedgames.craftics.combat.MoveStyle.WALK;
        return switch (style) {
            case WALK, CARDINAL_WALK -> getReachableTilesFrom(client, from, maxSteps);
            case ROOK_DASH -> getRookDashTilesFrom(client, from, 2);
            case CHARGE -> getChargeTilesFrom(client, from, maxSteps, 3);
            case BOUNCE_FREE -> getBounceTilesFrom(from, maxSteps);
            case BLINK -> getBlinkTilesFrom(client, from, Math.max(maxSteps, 3));
            case TELEPORT -> getTeleportTilesFrom(client, from);
            case POUNCE -> getPounceTilesFrom(client, playerPos);
            case STATIONARY -> Set.of();
        };
    }

    /**
     * BFS over the arena grid starting from {@code from}, returning all tiles that can be
     * reached within {@code maxSteps} movement points. Obstacles (solid blocks at Y+1) block
     * the path, and other entities (except {@code from} itself) block destinations as well.
     *
     * Used by enemy hover highlights: shows where an enemy could actually path to this turn,
     * not a raw manhattan diamond.
     */
    public static Set<GridPos> getReachableTilesFrom(MinecraftClient client, GridPos from, int maxSteps) {
        Set<GridPos> reachable = new HashSet<>();
        if (client == null || client.world == null || from == null || maxSteps <= 0) return reachable;

        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        if (w <= 0 || h <= 0) return reachable;

        // Treat other entity tiles as blockers so the path can't route through them,
        // but keep the starting tile passable even though it's occupied by the hovered mob.
        Set<GridPos> blockers = new HashSet<>(getEnemyGridPositions(client));
        blockers.remove(from);

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
                if (neighbor.x() < 0 || neighbor.x() >= w || neighbor.z() < 0 || neighbor.z() >= h) continue;
                if (dist.containsKey(neighbor)) continue;
                if (blockers.contains(neighbor)) continue;
                if (!isTileWalkable(client, neighbor)) continue;

                dist.put(neighbor, currentDist + 1);
                reachable.add(neighbor);
                queue.add(neighbor);
            }
        }

        return reachable;
    }
}
