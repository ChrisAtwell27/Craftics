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

    private static boolean isTileWalkable(MinecraftClient client, GridPos pos) {
        if (client.world == null) return false;
        int wx = CombatState.getArenaOriginX() + pos.x();
        int wy = CombatState.getArenaOriginY();
        int wz = CombatState.getArenaOriginZ() + pos.z();

        // Check the block at floor level — obstacles are non-walkable
        var blockState = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy + 1, wz));
        // If there's a solid block above the floor, it's an obstacle
        return blockState.isAir();
    }
}
