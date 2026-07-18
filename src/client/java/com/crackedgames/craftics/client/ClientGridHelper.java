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
                if (!CombatState.isInPolygon(neighbor.x(), neighbor.z())) continue;
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
                if (!CombatState.isInPolygon(x, z)) continue;
                if (blockers.contains(tile)) continue;
                if (!isTileWalkable(client, tile)) continue;
                result.add(tile);
            }
        }
        return result;
    }

    /**
     * Pounce (spider): the four tiles adjacent to the player that are walkable.
     * The spider's own current tile is irrelevant, pounce range is essentially "land next to you".
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
        if (pos.x() < 0 || pos.x() >= w || pos.z() < 0 || pos.z() >= h) return false;
        // Polygon arenas: the rectangle check is necessary but not sufficient.
        // Without this, dash/charge/bounce/blink/pounce previews would spill into
        // the bbox corners that lie outside the actual shape.
        return CombatState.isInPolygon(pos.x(), pos.z());
    }

    /**
     * Whether a tile is one the player may JUMP OVER. Client-side mirror of
     * {@code Pathfinding.isJumpableGap}: void, deep water, lava, fire, and shallow water.
     *
     * <p>Deliberately NOT obstacles (they stand above the floor - you would be jumping through
     * them) and not powder snow (a concealed trap; hopping it for a flat cost would defuse it).
     * Both sides must agree exactly, or the previewed path will not be the path the server walks.
     */
    public static boolean isJumpableGap(MinecraftClient client, GridPos pos) {
        if (client.world == null) return false;
        int wx = CombatState.getArenaOriginX() + pos.x();
        int wy = CombatState.getArenaOriginY();
        int wz = CombatState.getArenaOriginZ() + pos.z();

        var above = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy + 1, wz));
        // Something solid at body height = an obstacle, not a gap. Never jumpable.
        if (!above.isAir() && !isPassableAtBodyHeight(above)) return false;

        var floor = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy, wz));
        if (floor.isOf(net.minecraft.block.Blocks.LAVA)
                || floor.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
                || floor.isOf(net.minecraft.block.Blocks.FIRE)
                || floor.isOf(net.minecraft.block.Blocks.SOUL_FIRE)
                || floor.isOf(net.minecraft.block.Blocks.WATER)) {
            return true;
        }
        if (floor.isAir()) {
            // Void: air with nothing solid beneath. (Air over solid ground is LOW_GROUND -
            // walkable, so it is a step, not a gap.)
            var below = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy - 1, wz));
            return below.isAir() || !below.getFluidState().isEmpty();
        }
        // Powder snow is deliberately excluded - see the javadoc.
        return false;
    }

    public static boolean isTileWalkable(MinecraftClient client, GridPos pos) {
        if (client.world == null) return false;
        int wx = CombatState.getArenaOriginX() + pos.x();
        int wy = CombatState.getArenaOriginY();
        int wz = CombatState.getArenaOriginZ() + pos.z();

        // Check the block above the floor: solid blocks there are obstacles
        var blockState = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy + 1, wz));
        if (!blockState.isAir() && !isPassableAtBodyHeight(blockState)) return false;

        // Floor-level check: void (air with nothing below) is not walkable,
        // but hazard blocks (magma, lava, fire) at floor level ARE walkable
        var floorState = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy, wz));
        if (floorState.isAir()) {
            // Check if there's solid ground below (low ground) or true void
            var belowState = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy - 1, wz));
            return !belowState.isAir() && belowState.getFluidState().isEmpty();
        }
        // Deep water (2+ blocks) is DEEP_WATER server-side: not walkable, instant
        // kill. Shallow water (solid below) stays walkable.
        if (floorState.isOf(net.minecraft.block.Blocks.WATER)) {
            var belowState = client.world.getBlockState(new net.minecraft.util.math.BlockPos(wx, wy - 1, wz));
            return belowState.getFluidState().isEmpty();
        }
        return true;
    }

    /**
     * Grid tiles occupied by ally-side mobs (entity ids present in the synced
     * ally HP map). Used by the path preview to mirror the server rule that
     * the player can move THROUGH allies but not stop on them.
     */
    private static Set<GridPos> getAllyGridPositions(MinecraftClient client) {
        Set<GridPos> positions = new HashSet<>();
        if (client.world == null) return positions;
        int ox = CombatState.getArenaOriginX();
        int oz = CombatState.getArenaOriginZ();
        for (Integer id : CombatState.getAllyHpMap().keySet()) {
            Entity e = client.world.getEntityById(id);
            if (e == null || !e.isAlive()) continue;
            positions.add(new GridPos(
                (int) Math.floor(e.getX()) - ox,
                (int) Math.floor(e.getZ()) - oz));
        }
        return positions;
    }

    /**
     * Blocks at body height (floor + 1) that the server still classifies as
     * walkable tiles: stealth plants ({@code TALL_GRASS}/{@code TALL_FERN}),
     * cobweb traps, and stair ramps ({@code STAIR}). Mirrors ArenaBuilder's
     * post-placement tile scan so client-side previews (enemy movement ranges,
     * the player's path preview) agree with the server's pathfinding.
     */
    private static boolean isPassableAtBodyHeight(net.minecraft.block.BlockState state) {
        if (state.isOf(net.minecraft.block.Blocks.TALL_GRASS)
            || state.isOf(net.minecraft.block.Blocks.LARGE_FERN)
            || state.isOf(net.minecraft.block.Blocks.COBWEB)) {
            return true;
        }
        if (state.getBlock() instanceof net.minecraft.block.StairsBlock) return true;
        return state.getBlock() instanceof net.minecraft.block.SlabBlock
            && state.get(net.minecraft.block.SlabBlock.TYPE) == net.minecraft.block.enums.SlabType.BOTTOM;
    }

    /**
     * BFS shortest path from {@code from} to {@code target} under the same rules
     * as the server's player pathfinding: cardinal steps, obstacles block, enemy
     * tiles block, ally tiles are passable for INTERMEDIATE steps but can't be
     * the destination. Returns the tile sequence excluding {@code from} (so
     * {@code size()} equals the step cost), or {@code null} when the target
     * can't be reached within {@code maxSteps}. Drives the move-path preview.
     */
    public static List<GridPos> getPathTo(MinecraftClient client, GridPos from, GridPos target, int maxSteps) {
        if (client == null || client.world == null || from == null || target == null) return null;
        if (from.equals(target)) return List.of();
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        if (w <= 0 || h <= 0) return null;

        Set<GridPos> blockers = getEnemyGridPositions(client);
        Set<GridPos> allyTiles = getAllyGridPositions(client);
        Map<GridPos, GridPos> cameFrom = new HashMap<>();
        Map<GridPos, Integer> dist = new HashMap<>();
        // Dijkstra, not a plain BFS queue: jumps cost more than one speed, so a FIFO would
        // settle tiles at the wrong cost and preview a cheaper route than the server will take.
        java.util.PriorityQueue<GridPos> open = new java.util.PriorityQueue<>(
            java.util.Comparator.comparingInt(p -> dist.getOrDefault(p, Integer.MAX_VALUE)));
        dist.put(from, 0);
        open.add(from);
        Set<GridPos> settled = new HashSet<>();

        while (!open.isEmpty()) {
            GridPos current = open.poll();
            if (!settled.add(current)) continue;
            int currentDist = dist.getOrDefault(current, Integer.MAX_VALUE);
            if (current.equals(target)) {
                List<GridPos> path = new ArrayList<>();
                for (GridPos p = current; !p.equals(from); p = cameFrom.get(p)) {
                    if (p == null) return null;
                    path.add(p);
                }
                Collections.reverse(path);
                return path;
            }
            if (currentDist >= maxSteps) continue;

            for (GridPos dir : DIRECTIONS) {
                // --- 1-tile step ---
                GridPos neighbor = new GridPos(current.x() + dir.x(), current.z() + dir.z());
                if (inBounds(neighbor, w, h) && !settled.contains(neighbor)) {
                    boolean ok = true;
                    if (blockers.contains(neighbor)) {
                        // Allies are pass-through (server: move through, never stop on).
                        ok = allyTiles.contains(neighbor) && !neighbor.equals(target);
                    }
                    if (ok && CombatState.isInPolygon(neighbor.x(), neighbor.z())
                            && isTileWalkable(client, neighbor)) {
                        relaxClient(open, dist, cameFrom, current, neighbor, currentDist + 1, maxSteps);
                    }
                }

                // --- jump a gap, landing beyond it (mirrors Pathfinding.findPlayerPathWithJumps) ---
                boolean vault = hasPoleVault(client);
                for (int gap = 1; gap <= jumpMaxGap(client); gap++) {
                    boolean clear = true;
                    for (int g = 1; g <= gap && clear; g++) {
                        GridPos over = new GridPos(current.x() + dir.x() * g, current.z() + dir.z() * g);
                        // Mirrors Pathfinding.canJumpOver: a hazard gap must be empty (unless
                        // Pole Vault clears heads), and Pole Vault may also treat an OCCUPIED
                        // ordinary tile as a vaultable gap.
                        boolean occupied = blockers.contains(over);
                        boolean overOk = isJumpableGap(client, over)
                            ? (!occupied || vault)
                            : (vault && occupied);
                        if (!inBounds(over, w, h) || !overOk) {
                            clear = false;
                        }
                    }
                    if (!clear) continue;

                    GridPos land = new GridPos(current.x() + dir.x() * (gap + 1),
                                               current.z() + dir.z() * (gap + 1));
                    if (!inBounds(land, w, h) || settled.contains(land)) continue;
                    if (blockers.contains(land)) continue;
                    if (!CombatState.isInPolygon(land.x(), land.z())) continue;
                    // Land ON solid ground: you may clear lava, never end a jump in it.
                    if (!isTileWalkable(client, land) || isJumpableGap(client, land)) continue;

                    relaxClient(open, dist, cameFrom, current, land,
                        currentDist + jumpCost(client, gap), maxSteps);
                }
            }
        }
        return null;
    }

    /** Widest gap the player can clear. Must match {@code Pathfinding.MAX_JUMP_GAP}. */
    public static final int JUMP_MAX_GAP = 2;

    /** Speed a jump costs: the walk it replaces, plus one. Must match {@code Pathfinding.jumpCost}. */
    public static int jumpCost(int gapTiles) {
        return gapTiles + 2;
    }

    // ── Client mirror of the server's JumpProfile ───────────────────────────
    // The server builds its profile from the acting player's held weapon and worn leggings
    // (CombatManager.playerJumpProfile). The client reads ITS OWN copies of those same
    // stacks, so the preview and the resolved route can never disagree.

    /** Pole Vault held: jumps cost the plain walk price and may clear enemy-occupied tiles. */
    public static boolean hasPoleVault(MinecraftClient client) {
        if (client == null || client.player == null) return false;
        return com.crackedgames.craftics.combat.PlayerCombatStats.getEnchantLevel(
            client.player.getMainHandStack(),
            com.crackedgames.craftics.combat.CrafticsEnchantments.POLE_VAULT.fullId()) > 0;
    }

    /** Longstride leggings worn: jumps clear gaps up to 3 tiles. */
    public static boolean hasLongstride(MinecraftClient client) {
        if (client == null || client.player == null) return false;
        return com.crackedgames.craftics.combat.PlayerCombatStats.getEnchantLevel(
            client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),
            com.crackedgames.craftics.combat.CrafticsEnchantments.LONGSTRIDE.fullId()) > 0;
    }

    /** The widest clearable gap for the local player. Must match the server profile. */
    public static int jumpMaxGap(MinecraftClient client) {
        return hasLongstride(client)
            ? com.crackedgames.craftics.combat.SwordAxeEnchantEffects.LONGSTRIDE_MAX_GAP
            : JUMP_MAX_GAP;
    }

    /** Jump cost for the local player: Pole Vault drops the +1. Must match the server profile. */
    public static int jumpCost(MinecraftClient client, int gapTiles) {
        return hasPoleVault(client) ? gapTiles + 1 : jumpCost(gapTiles);
    }

    private static boolean inBounds(GridPos p, int w, int h) {
        return p.x() >= 0 && p.x() < w && p.z() >= 0 && p.z() < h;
    }

    private static void relaxClient(java.util.PriorityQueue<GridPos> open, Map<GridPos, Integer> dist,
                                    Map<GridPos, GridPos> cameFrom, GridPos from, GridPos to,
                                    int newDist, int maxSteps) {
        if (newDist > maxSteps) return;
        if (newDist >= dist.getOrDefault(to, Integer.MAX_VALUE)) return;
        dist.put(to, newDist);
        cameFrom.put(to, from);
        open.add(to);
    }

    /**
     * The tiles a path VAULTS over - the ones between two steps more than one tile apart.
     * Drawn as arrows rather than path dots, since the player never touches them.
     */
    public static List<GridPos> jumpedTilesOf(GridPos from, List<GridPos> path) {
        List<GridPos> jumped = new ArrayList<>();
        if (path == null || path.isEmpty()) return jumped;
        GridPos prev = from;
        for (GridPos step : path) {
            int dx = Integer.signum(step.x() - prev.x());
            int dz = Integer.signum(step.z() - prev.z());
            int span = Math.abs(step.x() - prev.x()) + Math.abs(step.z() - prev.z());
            for (int g = 1; g < span; g++) {
                jumped.add(new GridPos(prev.x() + dx * g, prev.z() + dz * g));
            }
            prev = step;
        }
        return jumped;
    }

    /**
     * Dispatch the correct hover-preview pattern for an enemy based on its movement style.
     * Each {@link com.crackedgames.craftics.combat.MoveStyle} draws a different shape, see
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
                if (!CombatState.isInPolygon(neighbor.x(), neighbor.z())) continue;
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
