package com.crackedgames.craftics.scene;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
                npcX, oy, npcZ - 1, 0f,
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
    /** Safety cap on the flood fill, so a leak into open terrain can't wall half the world. */
    private static final int MAX_FLOOR_TILES = 20_000;

    /**
     * Wall the scene's FALL EDGES so a player can't drop off it - following the terrain's actual
     * shape, including steps up and down.
     *
     * <p>How it works: flood-fill the walkable floor outward from {@code spawn}, letting the fill
     * step up or down one block like a walking player, and tracking each tile's own height. Then
     * raise a barrier column on every neighboring cell that is a genuine fall hazard: open at foot
     * level with no ground to land on within two blocks below. Cells blocked by real architecture
     * (a stall counter, a building wall, a cliff face going UP) are left alone - the architecture
     * already stops you, and walling them was what entombed booth NPCs and boxed players in.
     *
     * <p>Hard-learned rules encoded here:
     * <ul>
     *   <li><b>Collision shapes, not isSolidBlock.</b> Dirt paths, slabs and stairs are not "solid
     *       full cubes", so a solidity check read every village path as unwalkable - the fill
     *       couldn't cross them, and walls got drawn through the middle of the village.
     *   <li><b>Height-aware.</b> A single-plane fill treated every one-block step as the edge of
     *       the world.
     *   <li><b>Never replace a block, never dig below the floor.</b> The wall only fills AIR at and
     *       above foot level. An earlier version overwrote terrain three blocks down with barriers,
     *       which read as invisible holes punched in the ground.
     * </ul>
     *
     * <p>Every placed block goes through {@code snapshot}, so the wall comes down with the scene.
     *
     * @param floorY the level entities STAND on at the spawn tile (the air block, not the slab)
     * @param spawn  a tile known to be on the floor - the fill starts here
     */
    public static void sealPerimeter(ServerWorld world, BlockPos spawn, int floorY,
                                     Map<BlockPos, BlockState> snapshot) {
        // 1. Height-aware flood fill of everywhere a player can walk (4-way, +-1 step).
        Map<Long, Integer> floor = new HashMap<>();
        Deque<int[]> queue = new ArrayDeque<>();
        floor.put(key(spawn.getX(), spawn.getZ()), floorY);
        queue.add(new int[]{spawn.getX(), floorY, spawn.getZ()});

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && floor.size() < MAX_FLOOR_TILES) {
            int[] cur = queue.poll();
            for (int[] d : dirs) {
                int nx = cur[0] + d[0], nz = cur[2] + d[1];
                long k = key(nx, nz);
                if (floor.containsKey(k)) continue;
                for (int dy : new int[]{0, -1, 1}) {
                    int ny = cur[1] + dy;
                    if (isStandable(world, nx, ny, nz)) {
                        floor.put(k, ny);
                        queue.add(new int[]{nx, ny, nz});
                        break;
                    }
                }
            }
        }
        if (floor.size() >= MAX_FLOOR_TILES) {
            com.crackedgames.craftics.CrafticsMod.LOGGER.warn(
                "Scene floor fill hit its {}-tile cap; the perimeter wall may be incomplete.",
                MAX_FLOOR_TILES);
        }

        // 2. Wall only the FALL edges. 8-way, so a corner can't be cut diagonally.
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        Set<Long> walled = new HashSet<>();
        for (Map.Entry<Long, Integer> e : floor.entrySet()) {
            int x = keyX(e.getKey()), z = keyZ(e.getKey());
            int h = e.getValue();
            for (int[] d : around) {
                int nx = x + d[0], nz = z + d[1];
                long nk = key(nx, nz);
                if (floor.containsKey(nk) || walled.contains(nk)) continue;
                if (!isFallEdge(world, nx, h, nz)) continue;
                walled.add(nk);
                for (int dy = 0; dy < PERIMETER_WALL_HEIGHT; dy++) {
                    BlockPos p = new BlockPos(nx, h + dy, nz);
                    // Fill AIR only. Anything already there is scenery, and replacing scenery
                    // with invisible blocks is how the "holes in the floor" happened.
                    if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty()) continue;
                    setTracked(world, p, Blocks.BARRIER.getDefaultState(), snapshot);
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
