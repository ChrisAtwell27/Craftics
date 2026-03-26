package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Places and removes colored stained glass blocks above the arena floor
 * to show reachable (blue) and attackable (red) tiles.
 * Uses Y = origin.Y + 1 (same level as entities walk on) — actually uses
 * a dedicated highlight layer slightly above: we use light blocks at floor+1
 * but since entities stand there, we use carpet-like approach.
 *
 * Actually: place LIGHT_BLUE_STAINED_GLASS and RED_STAINED_GLASS at floor level (Y=origin.Y)
 * and swap the actual floor block back when clearing. But this destroys the floor pattern.
 *
 * Better approach: use colored CARPET blocks at Y = origin.Y + 1 (where entities walk).
 * Carpet is flat and walkable, so entities can stand on it.
 */
public class TileHighlightManager {

    private static final Set<BlockPos> activeHighlights = new HashSet<>();
    private static final int SET_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    /**
     * Clear all current highlights, restoring to air.
     * Only removes blocks that are actually carpet (avoids deleting placed tile effects).
     */
    public static void clearHighlights(ServerWorld world) {
        for (BlockPos pos : activeHighlights) {
            if (world.getBlockState(pos).getBlock() instanceof net.minecraft.block.CarpetBlock) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), SET_FLAGS);
            }
        }
        activeHighlights.clear();
    }

    /**
     * Show blue highlights for reachable movement tiles.
     */
    public static void showMoveHighlights(ServerWorld world, GridArena arena, int movePoints) {
        clearHighlights(world);
        if (movePoints <= 0) return;

        Set<GridPos> reachable = Pathfinding.getReachableTiles(arena, arena.getPlayerGridPos(), movePoints);
        for (GridPos gp : reachable) {
            BlockPos pos = new BlockPos(
                arena.getOrigin().getX() + gp.x(),
                arena.getOrigin().getY() + 1,
                arena.getOrigin().getZ() + gp.z()
            );
            // Only place if the space is air (don't overwrite entities/obstacles)
            if (world.getBlockState(pos).isAir()) {
                boolean cb = com.crackedgames.craftics.CrafticsMod.CONFIG.colorblindMode();
                world.setBlockState(pos, (cb ? Blocks.LIME_CARPET : Blocks.LIGHT_BLUE_CARPET).getDefaultState(), SET_FLAGS);
                activeHighlights.add(pos);
            }
        }
    }

    /** Track a single manually-placed highlight block. */
    public static void trackHighlight(BlockPos pos) {
        activeHighlights.add(pos);
    }

    /**
     * Show red highlights for tiles with attackable enemies.
     * range == PlayerCombatStats.RANGE_CROSSBOW_ROOK (-1) triggers rook pattern.
     */
    public static void showAttackHighlights(ServerWorld world, GridArena arena, int range) {
        clearHighlights(world);
        GridPos playerPos = arena.getPlayerGridPos();

        // Collect unique alive enemies, then highlight all their occupied tiles
        java.util.Set<CombatEntity> highlighted = new java.util.HashSet<>();
        for (var entry : arena.getOccupants().entrySet()) {
            CombatEntity enemy = entry.getValue();
            if (!enemy.isAlive() || highlighted.contains(enemy)) continue;

            // Check range using minimum distance to any occupied tile
            boolean inRange;
            if (range == PlayerCombatStats.RANGE_CROSSBOW_ROOK) {
                // Crossbow: check if any occupied tile is in a crossbow line
                inRange = false;
                for (var tile : com.crackedgames.craftics.core.GridArena.getOccupiedTiles(enemy.getGridPos(), enemy.getSize())) {
                    if (PlayerCombatStats.isInCrossbowLine(arena, playerPos, tile)) { inRange = true; break; }
                }
            } else if (range <= 1) {
                inRange = enemy.minDistanceTo(playerPos) <= range;
            } else {
                inRange = enemy.minDistanceTo(playerPos) <= range;
            }

            if (inRange) {
                highlighted.add(enemy);
                // Place red carpet on ALL tiles this enemy occupies
                for (var tile : com.crackedgames.craftics.core.GridArena.getOccupiedTiles(enemy.getGridPos(), enemy.getSize())) {
                    BlockPos pos = new BlockPos(
                        arena.getOrigin().getX() + tile.x(),
                        arena.getOrigin().getY() + 1,
                        arena.getOrigin().getZ() + tile.z()
                    );
                    if (world.getBlockState(pos).isAir()) {
                        boolean cbAtk = com.crackedgames.craftics.CrafticsMod.CONFIG.colorblindMode();
                        world.setBlockState(pos, (cbAtk ? Blocks.YELLOW_CARPET : Blocks.RED_CARPET).getDefaultState(), SET_FLAGS);
                        activeHighlights.add(pos);
                    }
                }
            }
        }
    }

    /**
     * Show orange danger zones — tiles that enemies can reach and attack on their next turn.
     * Call this alongside normal highlights when showEnemyRangeHints is enabled.
     */
    public static void showEnemyDangerZones(ServerWorld world, GridArena arena, java.util.List<CombatEntity> enemies) {
        GridPos playerPos = arena.getPlayerGridPos();
        Set<GridPos> dangerTiles = new HashSet<>();

        for (CombatEntity enemy : enemies) {
            if (!enemy.isAlive() || enemy.isAlly()) continue;
            GridPos ePos = enemy.getGridPos();
            int speed = enemy.getMoveSpeed();
            int range = enemy.getRange();

            // All tiles within move range + attack range of this enemy
            for (int dx = -(speed + range); dx <= speed + range; dx++) {
                for (int dz = -(speed + range); dz <= speed + range; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > speed + range) continue;
                    GridPos tile = new GridPos(ePos.x() + dx, ePos.z() + dz);
                    if (!arena.isInBounds(tile)) continue;
                    if (tile.equals(playerPos)) continue; // don't highlight player's own tile
                    dangerTiles.add(tile);
                }
            }
        }

        // Place orange carpet on danger tiles (only on air, don't overwrite existing highlights)
        for (GridPos gp : dangerTiles) {
            BlockPos pos = new BlockPos(
                arena.getOrigin().getX() + gp.x(),
                arena.getOrigin().getY() + 1,
                arena.getOrigin().getZ() + gp.z()
            );
            if (world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, Blocks.ORANGE_CARPET.getDefaultState(), SET_FLAGS);
                activeHighlights.add(pos);
            }
        }
    }
}
