package com.crackedgames.craftics.scene;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a small code-defined merchant scene (no .schem needed) for Stage 1. Splits the
 * pure layout math ({@link #buildLayout}) from world block placement ({@link #place}) so the
 * layout is unit-testable without a Minecraft bootstrap. A user-authored
 * craftics_scenes/&lt;name&gt;.schem still overrides this via SceneScanner when present.
 */
public final class CodeSceneBuilder {
    private CodeSceneBuilder() {}

    // Scene footprint: a straight walkway along +x with booths on the +z side.
    public static final int FLOOR_DEPTH = 8;    // z span - fixed; only the width grows
    private static final int BOOTH_SPACING = 4;  // x distance between booth anchors
    private static final int BOOTH_FIRST_X = 3;  // x offset of the first booth from origin
    /** Walkway left beyond the last booth, so the hall doesn't end flush against a stall. */
    private static final int TRAILING_MARGIN = 3;

    /**
     * How many booths {@code sceneName} has: one per registered merchant, so registering a trader
     * or a barter category also adds its stall. Was a hardcoded 3, which meant a player who had
     * met more than three merchants could never see them all.
     */
    public static int boothCount(String sceneName) {
        return switch (sceneName) {
            case "village" -> com.crackedgames.craftics.api.registry.TraderCategoryRegistry.count();
            case "barter_station" -> com.crackedgames.craftics.api.registry.BarterCategoryRegistry.all().size();
            default -> 0;
        };
    }

    /**
     * The hall's x span for {@code sceneName}: wide enough to hold every booth plus a margin.
     * Dynamic because the booth count is, so an addon's extra trader widens the building rather
     * than falling off the end of it.
     */
    public static int floorWidth(String sceneName) {
        int count = boothCount(sceneName);
        if (count <= 0) return 16;
        return BOOTH_FIRST_X + (count - 1) * BOOTH_SPACING + 1 + TRAILING_MARGIN;
    }

    /** Pure: compute the scene's spawn pose + booth slots for {@code sceneName}. No world access. */
    public static SceneLayout buildLayout(int ox, int oy, int oz, String sceneName) {
        int count = boothCount(sceneName);
        int floorWidth = floorWidth(sceneName);
        List<StandSlot> stands = new ArrayList<>();
        // Booths sit along the +z wall; the NPC faces -z (yaw 180) toward the walkway,
        // the player stands one tile in front (npcZ - 1) facing +z (yaw 0).
        int npcZ = oz + FLOOR_DEPTH - 2;
        for (int i = 0; i < count; i++) {
            int npcX = ox + BOOTH_FIRST_X + i * BOOTH_SPACING;
            int minX = npcX - 1, maxX = npcX + 1;
            int minZ = npcZ - 1, maxZ = npcZ + 1;
            stands.add(new StandSlot(
                minX, minZ, maxX, maxZ, oy,
                npcX, oy, npcZ, 180f,
                npcX, oy, npcZ - 2, 0f,
                SceneBooths.occupantFor(sceneName, i), StandSlot.Kind.DEDICATED));
        }
        // Player spawns at the front-center of the walkway, facing +z toward the booths.
        int spawnX = ox + floorWidth / 2;
        int spawnZ = oz + 1;
        return new SceneLayout(spawnX, oy, spawnZ, 0f, stands);
    }

    /** Place floor + simple booth blocks for {@code layout}, recording overwritten states into
     *  {@code snapshot} so the scene can be restored on leave. */
    public static void place(ServerWorld world, BlockPos origin, SceneLayout layout,
                             String sceneName, Map<BlockPos, BlockState> snapshot) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        int floorWidth = floorWidth(sceneName);
        // Floor slab of stone bricks across the footprint.
        for (int dx = 0; dx < floorWidth; dx++) {
            for (int dz = 0; dz < FLOOR_DEPTH; dz++) {
                setTracked(world, new BlockPos(ox + dx, oy - 1, oz + dz),
                    Blocks.STONE_BRICKS.getDefaultState(), snapshot);
                // Clear the air column above the floor so the scene isn't buried.
                for (int dy = 0; dy < 4; dy++) {
                    setTracked(world, new BlockPos(ox + dx, oy + dy, oz + dz),
                        Blocks.AIR.getDefaultState(), snapshot);
                }
            }
        }
        // A counter block behind each booth NPC (at npcZ + 1) so booths read visually.
        for (StandSlot s : layout.stands()) {
            setTracked(world, new BlockPos(s.npcX(), oy, s.npcZ() + 1),
                Blocks.OAK_PLANKS.getDefaultState(), snapshot);
        }
    }

    private static void setTracked(ServerWorld world, BlockPos pos, BlockState state,
                                   Map<BlockPos, BlockState> snapshot) {
        snapshot.putIfAbsent(pos.toImmutable(), world.getBlockState(pos));
        world.setBlockState(pos, state, net.minecraft.block.Block.FORCE_STATE);
    }

    /** How high the invisible wall at a fall edge rises above the local floor. */
    public static final int PERIMETER_WALL_HEIGHT = 8;

    /** How far above/below the scene's base standing level the floor scan looks. */
    private static final int FLOOR_SCAN_RANGE = 6;

    /**
     * Wall every fall edge of the scene's TERRAIN so a player can't drop off it.
     *
     * <p>This outlines the BLOCKS, not a walk from the spawn: every column in the scene's
     * footprint (plus a margin) is scanned for standable surfaces at any height near the base
     * level, and every 8-neighbor of a standable cell that is a genuine fall hazard - open at
     * foot level with nothing to land on within two blocks below - gets a barrier column.
     *
     * <p>The previous version flood-filled outward from the spawn tile and only walled what the
     * fill reached. That left every surface the fill couldn't WALK to unwalled - and since the
     * click-to-walk mover is a straight-line lerp, not a pathfind, players could be carried
     * across unfilled ground straight to an unwalled edge and fall. Reachability was the wrong
     * question; exposure is the right one.
     *
     * <p>Still true from hard experience: collision shapes not isSolidBlock (paths and slabs are
     * ground), never replace a block, never dig below the floor - the wall only fills air.
     *
     * <p>Every placed block goes through {@code snapshot}, so the wall comes down with the scene.
     *
     * @param origin the scene's floor-surface origin corner (min x/z), at standing level
     * @param baseY  the standing level at the spawn - the scan covers +-{@value #FLOOR_SCAN_RANGE}
     * @param width  footprint x span
     * @param depth  footprint z span
     */
    public static void sealPerimeter(ServerWorld world, BlockPos origin, int baseY,
                                     int width, int depth, Map<BlockPos, BlockState> snapshot) {
        int margin = 4;
        int minX = origin.getX() - margin, maxX = origin.getX() + width + margin;
        int minZ = origin.getZ() - margin, maxZ = origin.getZ() + depth + margin;

        // 1. Every standable surface cell in the footprint, at every height near base level.
        //    A column can hold several (a bridge over a path), and each gets its own outline.
        Map<Long, int[]> floorHeights = new HashMap<>(); // key(x,z) -> heights found
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int[] heights = new int[2 * FLOOR_SCAN_RANGE + 1];
                int n = 0;
                for (int y = baseY - FLOOR_SCAN_RANGE; y <= baseY + FLOOR_SCAN_RANGE; y++) {
                    if (isStandable(world, x, y, z)) heights[n++] = y;
                }
                if (n > 0) floorHeights.put(key(x, z), java.util.Arrays.copyOf(heights, n));
            }
        }

        // 2. Wall every 8-neighbor of a floor cell that is a fall hazard at that cell's height.
        //    8-way so a corner can't be cut diagonally.
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        Set<Long> walled = new HashSet<>(); // key(x,z) ^ height, dedup per column+level
        for (Map.Entry<Long, int[]> e : floorHeights.entrySet()) {
            int x = keyX(e.getKey()), z = keyZ(e.getKey());
            for (int h : e.getValue()) {
                for (int[] d : around) {
                    int nx = x + d[0], nz = z + d[1];
                    long wk = key(nx, nz) * 31 + h;
                    if (walled.contains(wk)) continue;
                    // A neighbor that is itself floor at (or one step from) this height is a
                    // walkable step, not an edge.
                    int[] nh = floorHeights.get(key(nx, nz));
                    boolean neighborWalkable = false;
                    if (nh != null) {
                        for (int y : nh) {
                            if (Math.abs(y - h) <= 1) { neighborWalkable = true; break; }
                        }
                    }
                    if (neighborWalkable) continue;
                    if (!isFallEdge(world, nx, h, nz)) continue;
                    walled.add(wk);
                    for (int dy = 0; dy < PERIMETER_WALL_HEIGHT; dy++) {
                        BlockPos p = new BlockPos(nx, h + dy, nz);
                        // Fill AIR only. Anything already there is scenery, and replacing
                        // scenery with invisible blocks reads as holes punched in the world.
                        if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty()) continue;
                        setTracked(world, p, Blocks.BARRIER.getDefaultState(), snapshot);
                    }
                }
            }
        }
    }

    /** Whether this block cell has ANY collision - the walkability currency here. Unlike
     *  isSolidBlock it counts paths, slabs, stairs and fences, which are all real ground/obstacles. */
    private static boolean hasCollision(ServerWorld world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    /**
     * Whether a player can stand with their feet at {@code (x, y, z)}: something to stand ON,
     * and two open cells to stand IN.
     */
    private static boolean isStandable(ServerWorld world, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        // A barrier from a previous seal must not read as ground or open space, or a re-entered
        // scene would flood straight through last visit's wall.
        if (world.getBlockState(feet).isOf(Blocks.BARRIER)) return false;
        return hasCollision(world, feet.down())
            && !hasCollision(world, feet)
            && !hasCollision(world, feet.up());
    }

    /**
     * A cell is a fall hazard when it is open at foot level AND has nothing to land on within two
     * blocks below - stepping there means falling. A cell blocked at foot level is architecture
     * (wall, counter, rising cliff) and needs no barrier; a cell with ground one below is just a
     * step down, which the fill already walked.
     */
    private static boolean isFallEdge(ServerWorld world, int x, int h, int z) {
        BlockPos feet = new BlockPos(x, h, z);
        return !hasCollision(world, feet)
            && !hasCollision(world, feet.down())
            && !hasCollision(world, feet.down(2));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int keyX(long k) {
        return (int) (k >> 32);
    }

    private static int keyZ(long k) {
        return (int) k;
    }
}
