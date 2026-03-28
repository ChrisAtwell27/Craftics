package com.crackedgames.craftics.world;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;

public class HubRoomBuilder {

    // Main room bounds (walls inclusive)
    private static final int MAIN_MIN_X = -6, MAIN_MAX_X = 6;
    private static final int MAIN_MIN_Z = -3, MAIN_MAX_Z = 3;

    // Bump-out nook (east side)
    private static final int BUMP_MIN_X = 7, BUMP_MAX_X = 8;
    private static final int BUMP_MIN_Z = -1, BUMP_MAX_Z = 1;

    // Porch
    private static final int PORCH_MIN_X = -5, PORCH_MAX_X = 5;
    private static final int PORCH_MIN_Z = 4, PORCH_MAX_Z = 5;

    // Vertical
    private static final int FOUNDATION_Y = 62;
    private static final int FLOOR_Y = 64;
    private static final int INTERIOR_Y = 65;
    private static final int CEILING_Y = 68;

    // Outdoor area bounds (large yard around the house)
    private static final int YARD_MIN_X = -30, YARD_MAX_X = 30;
    private static final int YARD_MIN_Z = -20, YARD_MAX_Z = 40;
    private static final int DIRT_DEPTH = 3; // 3 layers of dirt
    private static final int YARD_Y = 63; // ground level (1 below floor)

    // Hub version for rebuild detection — bump to trigger rebuild
    public static final int HUB_VERSION = 3;

    // Offset applied to all block placements (set before building)
    private static int ox = 0, oz = 0;

    /** Create an offset BlockPos — all hub block placements go through this. */
    private static BlockPos bp(int x, int y, int z) {
        return new BlockPos(ox + x, y, oz + z);
    }

    /** Lobby version — bump to trigger rebuild of the central lobby. */
    public static final int LOBBY_VERSION = 1;

    /** Build the central lobby — a floating island waiting room with barrier walls. */
    public static void buildLobby(ServerWorld world) {
        CrafticsMod.LOGGER.info("Building central lobby...");
        BlockState stone = Blocks.SMOOTH_STONE.getDefaultState();
        BlockState deepslate = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState basalt = Blocks.POLISHED_BASALT.getDefaultState();
        BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
        BlockState dirt = Blocks.DIRT.getDefaultState();
        BlockState barrier = Blocks.BARRIER.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState glowstone = Blocks.SEA_LANTERN.getDefaultState();
        BlockState blackstone = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();

        int radius = 12; // island radius
        int baseY = 62;  // bottom of the island
        int floorY = 64; // walkable surface
        int spawnY = 65;  // player Y
        int wallHeight = 5; // barrier walls

        // Build the floating island disc — circular platform
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius + 0.5) continue;

                // Underside depth varies for a natural floating island look
                int depth = (int)(3 * (1.0 - dist / radius)) + 1;
                for (int y = floorY - depth; y < floorY; y++) {
                    BlockState block = (y < floorY - 1) ? deepslate : dirt;
                    world.setBlockState(new BlockPos(x, y, z), block);
                }

                // Surface: grass for inner area, stone border ring
                boolean isEdge = dist > radius - 1.5;
                world.setBlockState(new BlockPos(x, floorY, z), isEdge ? blackstone : grass);

                // Clear air above
                for (int y = spawnY; y <= spawnY + wallHeight + 2; y++) {
                    world.setBlockState(new BlockPos(x, y, z), air);
                }

                // Barrier walls at the edge (invisible but solid)
                if (dist > radius - 0.8 && dist <= radius + 0.5) {
                    for (int y = spawnY; y < spawnY + wallHeight; y++) {
                        world.setBlockState(new BlockPos(x, y, z), barrier);
                    }
                }
            }
        }

        // Under-glow: embed lights under the platform edge
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius - 3 && dist < radius - 1) {
                    world.setBlockState(new BlockPos(x, floorY - 1, z), glowstone);
                }
            }
        }

        // Center pedestal with sign/info
        world.setBlockState(new BlockPos(0, floorY, 0), basalt);
        world.setBlockState(new BlockPos(0, spawnY, 0), Blocks.SOUL_LANTERN.getDefaultState());

        // Small ring of lanterns around center
        for (int[] pos : new int[][]{{3,0},{-3,0},{0,3},{0,-3},{2,2},{-2,2},{2,-2},{-2,-2}}) {
            world.setBlockState(new BlockPos(pos[0], spawnY, pos[1]),
                Blocks.LANTERN.getDefaultState());
        }

        // Decorative corner posts (4 pillars near edges)
        for (int[] corner : new int[][]{{7,7},{-7,7},{7,-7},{-7,-7}}) {
            for (int y = spawnY; y <= spawnY + 2; y++) {
                world.setBlockState(new BlockPos(corner[0], y, corner[1]), basalt);
            }
            world.setBlockState(new BlockPos(corner[0], spawnY + 3, corner[1]),
                Blocks.SOUL_LANTERN.getDefaultState());
        }

        // Set world spawn
        world.setSpawnPos(new BlockPos(0, spawnY, 0), 0f);

        CrafticsMod.LOGGER.info("Central lobby built.");
    }

    /**
     * Check if a position is part of the central lobby platform (protected from breaking).
     * The lobby is a circular island of radius 12 centered at (0, 65, 0).
     */
    public static boolean isLobbyProtected(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        // Only protect blocks at or below floor level (Y <= 64) within the island radius
        if (y > 64) return false;
        double dist = Math.sqrt(x * x + z * z);
        return dist <= 13; // radius 12 + 1 block margin
    }

    /** Build the central lobby (legacy entry point — calls buildLobby). */
    public static void build(ServerWorld world) {
        buildLobby(world);
    }

    /** Build a simple starter hut at the specified center position. Players can modify freely. */
    public static void build(ServerWorld world, BlockPos hubCenter) {
        ox = hubCenter.getX();
        oz = hubCenter.getZ();
        CrafticsMod.LOGGER.info("Building starter hut at ({}, {})...", ox, oz);

        BlockState oak = Blocks.OAK_PLANKS.getDefaultState();
        BlockState log = Blocks.OAK_LOG.getDefaultState();
        BlockState glass = Blocks.GLASS.getDefaultState();
        BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
        BlockState dirt = Blocks.DIRT.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState door_lower = Blocks.OAK_DOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.SOUTH)
            .with(Properties.DOUBLE_BLOCK_HALF, net.minecraft.block.enums.DoubleBlockHalf.LOWER);
        BlockState door_upper = Blocks.OAK_DOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.SOUTH)
            .with(Properties.DOUBLE_BLOCK_HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER);

        // Hut: 10x10 exterior (even width so level select centers on 2 blocks)
        // Walls at -4..5 in both axes, interior -3..4
        int minX = -4, maxX = 5;
        int minZ = -4, maxZ = 5;
        int floorY = 64;
        int interiorY = 65;
        int ceilingY = 68;

        // Ground: grass platform with dirt below
        int platMin = -12, platMax = 12;
        for (int x = platMin; x <= platMax; x++) {
            for (int z = platMin; z <= platMax; z++) {
                world.setBlockState(bp(x, floorY - 2, z), dirt);
                world.setBlockState(bp(x, floorY - 1, z), dirt);
                world.setBlockState(bp(x, floorY, z), grass);
                for (int y = interiorY; y <= ceilingY + 3; y++) {
                    world.setBlockState(bp(x, y, z), air);
                }
            }
        }

        // Floor
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockState(bp(x, floorY, z), oak);
            }
        }

        // Walls (3 high)
        for (int y = interiorY; y <= interiorY + 2; y++) {
            for (int x = minX; x <= maxX; x++) {
                world.setBlockState(bp(x, y, minZ), oak);
                world.setBlockState(bp(x, y, maxZ), oak);
            }
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockState(bp(minX, y, z), oak);
                world.setBlockState(bp(maxX, y, z), oak);
            }
        }

        // Corner logs (full height)
        for (int y = interiorY; y <= interiorY + 2; y++) {
            world.setBlockState(bp(minX, y, minZ), log);
            world.setBlockState(bp(maxX, y, minZ), log);
            world.setBlockState(bp(minX, y, maxZ), log);
            world.setBlockState(bp(maxX, y, maxZ), log);
        }

        // Ceiling
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockState(bp(x, ceilingY, z), oak);
            }
        }

        // Clear interior
        for (int x = minX + 1; x <= maxX - 1; x++) {
            for (int z = minZ + 1; z <= maxZ - 1; z++) {
                for (int y = interiorY; y <= ceilingY - 1; y++) {
                    world.setBlockState(bp(x, y, z), air);
                }
            }
        }

        // Door (south wall, centered between 0 and 1)
        world.setBlockState(bp(0, interiorY, maxZ), door_lower);
        world.setBlockState(bp(0, interiorY + 1, maxZ), door_upper);

        // Windows (glass blocks, 2 wide on each wall)
        for (int wx : new int[]{0, 1}) {
            world.setBlockState(bp(wx, interiorY + 1, minZ), glass); // north
        }
        world.setBlockState(bp(minX, interiorY + 1, 0), glass); // west
        world.setBlockState(bp(minX, interiorY + 1, 1), glass);
        world.setBlockState(bp(maxX, interiorY + 1, 0), glass); // east
        world.setBlockState(bp(maxX, interiorY + 1, 1), glass);

        // Crafting table (NW corner)
        world.setBlockState(bp(minX + 1, interiorY, minZ + 1), Blocks.CRAFTING_TABLE.getDefaultState());

        // Furnace (NE corner)
        world.setBlockState(bp(maxX - 1, interiorY, minZ + 1), Blocks.FURNACE.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.SOUTH));

        // Level select block (centered on north wall — hut center is between 0 and 1)
        world.setBlockState(bp(0, interiorY, minZ + 1),
            ModBlocks.LEVEL_SELECT_BLOCK.getDefaultState()
                .with(com.crackedgames.craftics.block.LevelSelectBlock.FACING, Direction.EAST));

        // Hanging lantern (centered)
        world.setBlockState(bp(0, interiorY + 2, 1), Blocks.LANTERN.getDefaultState()
            .with(Properties.HANGING, true));

        CrafticsMod.LOGGER.info("Starter hut built at ({}, {}).", ox, oz);
    }

    /**
     * Build a large outdoor area around the hub — grass surface, 3 dirt layers, bedrock floor.
     * This gives players space for animals, farming, and general outdoor activity.
     * 60x60 block area centered roughly on the house.
     */
    private static void buildOutdoorArea(ServerWorld world) {
        CrafticsMod.LOGGER.info("Building outdoor yard area...");
        BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
        BlockState dirt = Blocks.DIRT.getDefaultState();
        BlockState bedrock = Blocks.BEDROCK.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();

        for (int x = YARD_MIN_X; x <= YARD_MAX_X; x++) {
            for (int z = YARD_MIN_Z; z <= YARD_MAX_Z; z++) {
                // Bedrock floor
                world.setBlockState(bp(x, YARD_Y - DIRT_DEPTH, z), bedrock);
                // 3 layers of dirt
                for (int d = 1; d <= DIRT_DEPTH; d++) {
                    world.setBlockState(bp(x, YARD_Y - DIRT_DEPTH + d, z), dirt);
                }
                // Grass on top
                world.setBlockState(bp(x, YARD_Y + 1, z), grass);
                // Clear air above (10 blocks high for trees, buildings, etc.)
                for (int y = YARD_Y + 2; y <= YARD_Y + 12; y++) {
                    world.setBlockState(bp(x, y, z), air);
                }
            }
        }

        // Place a fence around the yard perimeter
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();
        for (int x = YARD_MIN_X; x <= YARD_MAX_X; x++) {
            world.setBlockState(bp(x, YARD_Y + 2, YARD_MIN_Z), fence);
            world.setBlockState(bp(x, YARD_Y + 2, YARD_MAX_Z), fence);
        }
        for (int z = YARD_MIN_Z; z <= YARD_MAX_Z; z++) {
            world.setBlockState(bp(YARD_MIN_X, YARD_Y + 2, z), fence);
            world.setBlockState(bp(YARD_MAX_X, YARD_Y + 2, z), fence);
        }

        // Gate openings (south side, centered)
        world.setBlockState(bp(-1, YARD_Y + 2, YARD_MAX_Z), air);
        world.setBlockState(bp(0, YARD_Y + 2, YARD_MAX_Z), air);
        world.setBlockState(bp(1, YARD_Y + 2, YARD_MAX_Z), air);

        // A few trees scattered around
        plantSimpleTree(world, 15, YARD_Y + 2, 20);
        plantSimpleTree(world, -12, YARD_Y + 2, 25);
        plantSimpleTree(world, 20, YARD_Y + 2, 10);
        plantSimpleTree(world, -18, YARD_Y + 2, 15);
    }

    private static void plantSimpleTree(ServerWorld world, int x, int baseY, int z) {
        BlockState log = Blocks.OAK_LOG.getDefaultState();
        BlockState leaves = Blocks.OAK_LEAVES.getDefaultState();
        // Trunk (4 high)
        for (int y = 0; y < 4; y++) {
            world.setBlockState(bp(x, baseY + y, z), log);
        }
        // Leaf canopy (3x3x2 on top)
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                world.setBlockState(bp(x + lx, baseY + 3, z + lz), leaves);
                world.setBlockState(bp(x + lx, baseY + 4, z + lz), leaves);
            }
        }
        world.setBlockState(bp(x, baseY + 5, z), leaves);
    }

    private static void clearArea(ServerWorld world) {
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = -8; x <= 10; x++) {
            for (int z = -6; z <= 7; z++) {
                for (int y = 62; y <= 73; y++) {
                    world.setBlockState(bp(x, y, z), air);
                }
            }
        }
    }

    private static void buildFoundation(ServerWorld world, Random rng) {
        // Stone foundation visible from outside, 1 block wider than walls
        for (int y = FOUNDATION_Y; y <= FOUNDATION_Y + 1; y++) {
            // Main room foundation
            for (int x = MAIN_MIN_X - 1; x <= MAIN_MAX_X + 1; x++) {
                for (int z = MAIN_MIN_Z - 1; z <= MAIN_MAX_Z + 1; z++) {
                    BlockState stone = rng.nextInt(4) == 0
                        ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                        : Blocks.COBBLESTONE.getDefaultState();
                    world.setBlockState(bp(x, y, z), stone);
                }
            }
            // Bump-out foundation
            for (int x = BUMP_MIN_X - 1; x <= BUMP_MAX_X + 1; x++) {
                for (int z = BUMP_MIN_Z - 1; z <= BUMP_MAX_Z + 1; z++) {
                    BlockState stone = rng.nextInt(4) == 0
                        ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                        : Blocks.COBBLESTONE.getDefaultState();
                    world.setBlockState(bp(x, y, z), stone);
                }
            }
            // Porch foundation
            for (int x = PORCH_MIN_X - 1; x <= PORCH_MAX_X + 1; x++) {
                for (int z = PORCH_MIN_Z; z <= PORCH_MAX_Z + 1; z++) {
                    BlockState stone = rng.nextInt(3) == 0
                        ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                        : Blocks.COBBLESTONE.getDefaultState();
                    world.setBlockState(bp(x, y, z), stone);
                }
            }
        }
    }

    private static void buildFloor(ServerWorld world) {
        BlockState oakPlanks = Blocks.OAK_PLANKS.getDefaultState();
        // Main room floor
        for (int x = MAIN_MIN_X; x <= MAIN_MAX_X; x++) {
            for (int z = MAIN_MIN_Z; z <= MAIN_MAX_Z; z++) {
                world.setBlockState(bp(x, FLOOR_Y, z), oakPlanks);
            }
        }
        // Bump-out floor
        for (int x = BUMP_MIN_X; x <= BUMP_MAX_X; x++) {
            for (int z = BUMP_MIN_Z; z <= BUMP_MAX_Z; z++) {
                world.setBlockState(bp(x, FLOOR_Y, z), oakPlanks);
            }
        }
        // Clear interior air above floor
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = MAIN_MIN_X + 1; x < MAIN_MAX_X; x++) {
            for (int z = MAIN_MIN_Z + 1; z < MAIN_MAX_Z; z++) {
                for (int y = INTERIOR_Y; y <= INTERIOR_Y + 2; y++) {
                    world.setBlockState(bp(x, y, z), air);
                }
            }
        }
    }

    private static void buildWalls(ServerWorld world) {
        BlockState darkOakLog = Blocks.DARK_OAK_LOG.getDefaultState();
        BlockState oakPlanks = Blocks.OAK_PLANKS.getDefaultState();

        // Stripped oak log for wainscot (Y=65), axis depends on wall direction
        BlockState wainscotX = Blocks.STRIPPED_OAK_LOG.getDefaultState()
            .with(Properties.AXIS, Direction.Axis.X);
        BlockState wainscotZ = Blocks.STRIPPED_OAK_LOG.getDefaultState()
            .with(Properties.AXIS, Direction.Axis.Z);

        // Top beam
        BlockState beamX = Blocks.STRIPPED_OAK_LOG.getDefaultState()
            .with(Properties.AXIS, Direction.Axis.X);
        BlockState beamZ = Blocks.STRIPPED_OAK_LOG.getDefaultState()
            .with(Properties.AXIS, Direction.Axis.Z);

        // Corner posts (dark oak log, vertical)
        int[][] corners = {
            {MAIN_MIN_X, MAIN_MIN_Z}, {MAIN_MIN_X, MAIN_MAX_Z},
            {MAIN_MAX_X, MAIN_MIN_Z}, {MAIN_MAX_X, MAIN_MAX_Z}
        };
        for (int[] c : corners) {
            for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
                world.setBlockState(bp(c[0], y, c[1]), darkOakLog);
            }
        }

        // North wall (Z = MAIN_MIN_Z), runs along X
        for (int x = MAIN_MIN_X + 1; x < MAIN_MAX_X; x++) {
            world.setBlockState(bp(x, INTERIOR_Y, MAIN_MIN_Z), wainscotX);
            world.setBlockState(bp(x, INTERIOR_Y + 1, MAIN_MIN_Z), oakPlanks);
            world.setBlockState(bp(x, INTERIOR_Y + 2, MAIN_MIN_Z), oakPlanks);
            world.setBlockState(bp(x, CEILING_Y, MAIN_MIN_Z), beamX);
        }

        // South wall (Z = MAIN_MAX_Z), runs along X
        for (int x = MAIN_MIN_X + 1; x < MAIN_MAX_X; x++) {
            world.setBlockState(bp(x, INTERIOR_Y, MAIN_MAX_Z), wainscotX);
            world.setBlockState(bp(x, INTERIOR_Y + 1, MAIN_MAX_Z), oakPlanks);
            world.setBlockState(bp(x, INTERIOR_Y + 2, MAIN_MAX_Z), oakPlanks);
            world.setBlockState(bp(x, CEILING_Y, MAIN_MAX_Z), beamX);
        }

        // West wall (X = MAIN_MIN_X), runs along Z
        for (int z = MAIN_MIN_Z + 1; z < MAIN_MAX_Z; z++) {
            world.setBlockState(bp(MAIN_MIN_X, INTERIOR_Y, z), wainscotZ);
            world.setBlockState(bp(MAIN_MIN_X, INTERIOR_Y + 1, z), oakPlanks);
            world.setBlockState(bp(MAIN_MIN_X, INTERIOR_Y + 2, z), oakPlanks);
            world.setBlockState(bp(MAIN_MIN_X, CEILING_Y, z), beamZ);
        }

        // East wall (X = MAIN_MAX_X), runs along Z — but leave opening for bump-out
        for (int z = MAIN_MIN_Z + 1; z < MAIN_MAX_Z; z++) {
            if (z >= BUMP_MIN_Z && z <= BUMP_MAX_Z) continue; // opening to bump-out
            world.setBlockState(bp(MAIN_MAX_X, INTERIOR_Y, z), wainscotZ);
            world.setBlockState(bp(MAIN_MAX_X, INTERIOR_Y + 1, z), oakPlanks);
            world.setBlockState(bp(MAIN_MAX_X, INTERIOR_Y + 2, z), oakPlanks);
            world.setBlockState(bp(MAIN_MAX_X, CEILING_Y, z), beamZ);
        }
    }

    private static void buildBumpOut(ServerWorld world) {
        BlockState darkOakLog = Blocks.DARK_OAK_LOG.getDefaultState();
        BlockState oakPlanks = Blocks.OAK_PLANKS.getDefaultState();
        BlockState wainscotX = Blocks.STRIPPED_OAK_LOG.getDefaultState()
            .with(Properties.AXIS, Direction.Axis.X);
        BlockState wainscotZ = Blocks.STRIPPED_OAK_LOG.getDefaultState()
            .with(Properties.AXIS, Direction.Axis.Z);
        BlockState beamX = Blocks.STRIPPED_OAK_LOG.getDefaultState()
            .with(Properties.AXIS, Direction.Axis.X);
        BlockState beamZ = Blocks.STRIPPED_OAK_LOG.getDefaultState()
            .with(Properties.AXIS, Direction.Axis.Z);
        BlockState air = Blocks.AIR.getDefaultState();

        // Corner posts for bump-out
        int[][] bumpCorners = {
            {BUMP_MAX_X, BUMP_MIN_Z}, {BUMP_MAX_X, BUMP_MAX_Z}
        };
        for (int[] c : bumpCorners) {
            for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
                world.setBlockState(bp(c[0], y, c[1]), darkOakLog);
            }
        }
        // Transition posts where bump meets main wall
        for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
            world.setBlockState(bp(MAIN_MAX_X, y, BUMP_MIN_Z), darkOakLog);
            world.setBlockState(bp(MAIN_MAX_X, y, BUMP_MAX_Z), darkOakLog);
        }

        // East wall of bump-out (X = BUMP_MAX_X)
        for (int z = BUMP_MIN_Z + 1; z < BUMP_MAX_Z; z++) {
            world.setBlockState(bp(BUMP_MAX_X, INTERIOR_Y, z), wainscotZ);
            world.setBlockState(bp(BUMP_MAX_X, INTERIOR_Y + 1, z), oakPlanks);
            world.setBlockState(bp(BUMP_MAX_X, INTERIOR_Y + 2, z), oakPlanks);
            world.setBlockState(bp(BUMP_MAX_X, CEILING_Y, z), beamZ);
        }

        // North wall of bump-out (Z = BUMP_MIN_Z)
        for (int x = BUMP_MIN_X; x < BUMP_MAX_X; x++) {
            world.setBlockState(bp(x, INTERIOR_Y, BUMP_MIN_Z), wainscotX);
            world.setBlockState(bp(x, INTERIOR_Y + 1, BUMP_MIN_Z), oakPlanks);
            world.setBlockState(bp(x, INTERIOR_Y + 2, BUMP_MIN_Z), oakPlanks);
            world.setBlockState(bp(x, CEILING_Y, BUMP_MIN_Z), beamX);
        }

        // South wall of bump-out (Z = BUMP_MAX_Z)
        for (int x = BUMP_MIN_X; x < BUMP_MAX_X; x++) {
            world.setBlockState(bp(x, INTERIOR_Y, BUMP_MAX_Z), wainscotX);
            world.setBlockState(bp(x, INTERIOR_Y + 1, BUMP_MAX_Z), oakPlanks);
            world.setBlockState(bp(x, INTERIOR_Y + 2, BUMP_MAX_Z), oakPlanks);
            world.setBlockState(bp(x, CEILING_Y, BUMP_MAX_Z), beamX);
        }

        // Clear bump-out interior air
        for (int x = BUMP_MIN_X; x < BUMP_MAX_X; x++) {
            for (int z = BUMP_MIN_Z + 1; z < BUMP_MAX_Z; z++) {
                for (int y = INTERIOR_Y; y <= INTERIOR_Y + 2; y++) {
                    world.setBlockState(bp(x, y, z), air);
                }
            }
        }
        // Clear opening between main room and bump-out
        for (int z = BUMP_MIN_Z + 1; z < BUMP_MAX_Z; z++) {
            for (int y = INTERIOR_Y; y <= INTERIOR_Y + 2; y++) {
                world.setBlockState(bp(MAIN_MAX_X, y, z), air);
            }
        }

        // Bump-out ceiling
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.getDefaultState();
        for (int x = BUMP_MIN_X; x <= BUMP_MAX_X; x++) {
            for (int z = BUMP_MIN_Z; z <= BUMP_MAX_Z; z++) {
                world.setBlockState(bp(x, CEILING_Y, z), sprucePlanks);
            }
        }
    }

    private static void buildWindows(ServerWorld world) {
        BlockState glassPane = Blocks.GLASS_PANE.getDefaultState();

        // North wall windows (Z = MAIN_MIN_Z) — two 2-wide windows at Y=66-67
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            // Left window
            world.setBlockState(bp(-3, y, MAIN_MIN_Z), glassPane);
            world.setBlockState(bp(-2, y, MAIN_MIN_Z), glassPane);
            // Right window
            world.setBlockState(bp(2, y, MAIN_MIN_Z), glassPane);
            world.setBlockState(bp(3, y, MAIN_MIN_Z), glassPane);
        }

        // South wall windows (Z = MAIN_MAX_Z) — two windows, leaving center for door
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            // Left window
            world.setBlockState(bp(-4, y, MAIN_MAX_Z), glassPane);
            world.setBlockState(bp(-3, y, MAIN_MAX_Z), glassPane);
            // Right window
            world.setBlockState(bp(3, y, MAIN_MAX_Z), glassPane);
            world.setBlockState(bp(4, y, MAIN_MAX_Z), glassPane);
        }

        // West wall window (X = MAIN_MIN_X) — one 2-tall window
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(bp(MAIN_MIN_X, y, -1), glassPane);
            world.setBlockState(bp(MAIN_MIN_X, y, 0), glassPane);
        }

        // Bump-out east window (X = BUMP_MAX_X) — one 2-tall window
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(bp(BUMP_MAX_X, y, 0), glassPane);
        }

        // Trapdoor shutters on exterior faces of windows
        placeShutters(world);
    }

    private static void placeShutters(ServerWorld world) {
        BlockState shutterN = Blocks.SPRUCE_TRAPDOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.SOUTH)
            .with(Properties.OPEN, true)
            .with(Properties.BLOCK_HALF, BlockHalf.TOP);
        BlockState shutterS = Blocks.SPRUCE_TRAPDOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(Properties.OPEN, true)
            .with(Properties.BLOCK_HALF, BlockHalf.TOP);
        BlockState shutterW = Blocks.SPRUCE_TRAPDOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.EAST)
            .with(Properties.OPEN, true)
            .with(Properties.BLOCK_HALF, BlockHalf.TOP);
        BlockState shutterE = Blocks.SPRUCE_TRAPDOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.WEST)
            .with(Properties.OPEN, true)
            .with(Properties.BLOCK_HALF, BlockHalf.TOP);

        // North wall shutters (placed on Z = MAIN_MIN_Z - 1)
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(bp(-4, y, MAIN_MIN_Z - 1), shutterN);
            world.setBlockState(bp(-1, y, MAIN_MIN_Z - 1), shutterN);
            world.setBlockState(bp(1, y, MAIN_MIN_Z - 1), shutterN);
            world.setBlockState(bp(4, y, MAIN_MIN_Z - 1), shutterN);
        }

        // South wall shutters (placed on Z = MAIN_MAX_Z + 1)
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(bp(-5, y, MAIN_MAX_Z + 1), shutterS);
            world.setBlockState(bp(-2, y, MAIN_MAX_Z + 1), shutterS);
            world.setBlockState(bp(2, y, MAIN_MAX_Z + 1), shutterS);
            world.setBlockState(bp(5, y, MAIN_MAX_Z + 1), shutterS);
        }

        // West wall shutters (placed on X = MAIN_MIN_X - 1)
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(bp(MAIN_MIN_X - 1, y, -2), shutterW);
            world.setBlockState(bp(MAIN_MIN_X - 1, y, 1), shutterW);
        }

        // Bump-out east shutters (placed on X = BUMP_MAX_X + 1)
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(bp(BUMP_MAX_X + 1, y, -1), shutterE);
            world.setBlockState(bp(BUMP_MAX_X + 1, y, 1), shutterE);
        }
    }

    private static void buildDoor(ServerWorld world) {
        BlockState doorLower = Blocks.OAK_DOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = Blocks.OAK_DOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);

        world.setBlockState(bp(0, INTERIOR_Y, MAIN_MAX_Z), doorLower);
        world.setBlockState(bp(0, INTERIOR_Y + 1, MAIN_MAX_Z), doorUpper);
    }

    private static void buildCeiling(ServerWorld world) {
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.getDefaultState();
        // Main room ceiling
        for (int x = MAIN_MIN_X; x <= MAIN_MAX_X; x++) {
            for (int z = MAIN_MIN_Z; z <= MAIN_MAX_Z; z++) {
                world.setBlockState(bp(x, CEILING_Y, z), sprucePlanks);
            }
        }
    }

    private static void buildRoof(ServerWorld world) {
        // Gabled roof with ridge running along X axis, centered at Z=0
        // Spruce stairs slope up from eaves, spruce slabs cap the ridge

        BlockState stairsN = Blocks.SPRUCE_STAIRS.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.SOUTH)
            .with(Properties.BLOCK_HALF, BlockHalf.BOTTOM);
        BlockState stairsS = Blocks.SPRUCE_STAIRS.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(Properties.BLOCK_HALF, BlockHalf.BOTTOM);
        BlockState slab = Blocks.SPRUCE_SLAB.getDefaultState();
        BlockState oakPlanks = Blocks.OAK_PLANKS.getDefaultState(); // gable fill

        int roofMinX = MAIN_MIN_X - 1;
        int roofMaxX = MAIN_MAX_X + 1;

        // Roof layers — north and south slopes rising to meet at ridge
        // Y=68: eave overhang (already has ceiling inside)
        // North eave at Z = MAIN_MIN_Z - 1, South eave at Z = MAIN_MAX_Z + 1
        for (int x = roofMinX; x <= roofMaxX; x++) {
            // Y=68 eaves
            world.setBlockState(bp(x, 68, MAIN_MIN_Z - 1), stairsN);
            world.setBlockState(bp(x, 68, MAIN_MAX_Z + 1), stairsS);
            // Y=69
            world.setBlockState(bp(x, 69, MAIN_MIN_Z), stairsN);
            world.setBlockState(bp(x, 69, MAIN_MAX_Z), stairsS);
            // Y=70
            world.setBlockState(bp(x, 70, MAIN_MIN_Z + 1), stairsN);
            world.setBlockState(bp(x, 70, MAIN_MAX_Z - 1), stairsS);
            // Y=71 — ridge cap (slabs)
            world.setBlockState(bp(x, 71, MAIN_MIN_Z + 2), slab);
            world.setBlockState(bp(x, 71, MAIN_MAX_Z - 2), slab);
        }

        // Fill under roof with air (attic space)
        BlockState air = Blocks.AIR.getDefaultState();
        for (int y = 69; y <= 71; y++) {
            for (int x = MAIN_MIN_X; x <= MAIN_MAX_X; x++) {
                for (int z = MAIN_MIN_Z; z <= MAIN_MAX_Z; z++) {
                    BlockPos pos = bp(x, y, z);
                    if (world.getBlockState(pos).isAir()) continue;
                    // Only clear if it's not a roof stair/slab we just placed
                    if (!world.getBlockState(pos).isOf(Blocks.SPRUCE_STAIRS)
                        && !world.getBlockState(pos).isOf(Blocks.SPRUCE_SLAB)) {
                        world.setBlockState(pos, air);
                    }
                }
            }
        }

        // Gable walls (triangle fill on west and east ends)
        // West gable (X = MAIN_MIN_X)
        buildGableWall(world, MAIN_MIN_X, oakPlanks);
        // East gable (X = MAIN_MAX_X)
        buildGableWall(world, MAIN_MAX_X, oakPlanks);
    }

    private static void buildGableWall(ServerWorld world, int x, BlockState fill) {
        // Triangle fill from Y=69 upward, narrowing toward ridge
        // Y=69: Z from MAIN_MIN_Z to MAIN_MAX_Z (full width)
        for (int z = MAIN_MIN_Z; z <= MAIN_MAX_Z; z++) {
            world.setBlockState(bp(x, 69, z), fill);
        }
        // Y=70: Z from MAIN_MIN_Z+1 to MAIN_MAX_Z-1
        for (int z = MAIN_MIN_Z + 1; z <= MAIN_MAX_Z - 1; z++) {
            world.setBlockState(bp(x, 70, z), fill);
        }
        // Y=71: Z from MAIN_MIN_Z+2 to MAIN_MAX_Z-2 (just the center)
        for (int z = MAIN_MIN_Z + 2; z <= MAIN_MAX_Z - 2; z++) {
            world.setBlockState(bp(x, 71, z), fill);
        }
    }

    private static void buildPorch(ServerWorld world) {
        BlockState oakSlab = Blocks.OAK_SLAB.getDefaultState()
            .with(Properties.SLAB_TYPE, SlabType.TOP);
        BlockState fence = Blocks.DARK_OAK_FENCE.getDefaultState();
        BlockState post = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
        BlockState cobble = Blocks.COBBLESTONE.getDefaultState();

        // Porch floor (slabs on top of foundation)
        for (int x = PORCH_MIN_X; x <= PORCH_MAX_X; x++) {
            for (int z = PORCH_MIN_Z; z <= PORCH_MAX_Z; z++) {
                world.setBlockState(bp(x, FLOOR_Y, z), oakSlab);
            }
        }

        // Porch support posts
        world.setBlockState(bp(PORCH_MIN_X, INTERIOR_Y, PORCH_MAX_Z), post);
        world.setBlockState(bp(PORCH_MAX_X, INTERIOR_Y, PORCH_MAX_Z), post);
        world.setBlockState(bp(PORCH_MIN_X, INTERIOR_Y + 1, PORCH_MAX_Z), post);
        world.setBlockState(bp(PORCH_MAX_X, INTERIOR_Y + 1, PORCH_MAX_Z), post);
        world.setBlockState(bp(PORCH_MIN_X, INTERIOR_Y + 2, PORCH_MAX_Z), post);
        world.setBlockState(bp(PORCH_MAX_X, INTERIOR_Y + 2, PORCH_MAX_Z), post);

        // Fence railing along porch edge (Z = PORCH_MAX_Z), skip middle for entry
        for (int x = PORCH_MIN_X + 1; x < PORCH_MAX_X; x++) {
            if (Math.abs(x) <= 1) continue; // entry gap
            world.setBlockState(bp(x, INTERIOR_Y, PORCH_MAX_Z), fence);
        }
        // Side railings
        world.setBlockState(bp(PORCH_MIN_X, INTERIOR_Y, PORCH_MIN_Z), fence);
        world.setBlockState(bp(PORCH_MAX_X, INTERIOR_Y, PORCH_MIN_Z), fence);

        // Steps down from porch (cobble step at Z = PORCH_MAX_Z + 1)
        BlockState cobbleSlab = Blocks.COBBLESTONE_SLAB.getDefaultState()
            .with(Properties.SLAB_TYPE, SlabType.TOP);
        for (int x = -1; x <= 1; x++) {
            world.setBlockState(bp(x, FLOOR_Y, PORCH_MAX_Z + 1), cobbleSlab);
        }

        // Path from steps (a few cobblestone blocks)
        for (int x = -1; x <= 1; x++) {
            world.setBlockState(bp(x, FLOOR_Y - 1, PORCH_MAX_Z + 2), cobble);
            world.setBlockState(bp(x, FLOOR_Y - 1, PORCH_MAX_Z + 3), cobble);
        }
    }

    private static void buildChimney(ServerWorld world) {
        BlockState bricks = Blocks.BRICKS.getDefaultState();
        BlockState wall = Blocks.BRICK_WALL.getDefaultState();

        // Chimney on the bump-out roof, rising from Y=69 to Y=72
        int cx = BUMP_MAX_X, cz = 0;
        for (int y = CEILING_Y; y <= 72; y++) {
            world.setBlockState(bp(cx, y, cz), bricks);
        }
        // Chimney cap
        world.setBlockState(bp(cx, 73, cz), wall);
    }

    private static void placeFurniture(ServerWorld world) {
        // Level select block — centered on north wall, facing east so model extends sideways
        world.setBlockState(bp(0, INTERIOR_Y, MAIN_MIN_Z + 1),
            ModBlocks.LEVEL_SELECT_BLOCK.getDefaultState()
                .with(com.crackedgames.craftics.block.LevelSelectBlock.FACING, Direction.EAST));

        // Furnace — against NW interior wall, facing into room
        world.setBlockState(bp(MAIN_MIN_X + 1, INTERIOR_Y, MAIN_MIN_Z + 1),
            Blocks.FURNACE.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.EAST));

        // Crafting table — between furnace and chest on west wall
        world.setBlockState(bp(MAIN_MIN_X + 1, INTERIOR_Y, 0),
            Blocks.CRAFTING_TABLE.getDefaultState());

        // Chest — against SW interior wall
        world.setBlockState(bp(MAIN_MIN_X + 1, INTERIOR_Y, MAIN_MAX_Z - 1),
            Blocks.CHEST.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.EAST));
    }

    private static void placeLighting(ServerWorld world) {
        BlockState lantern = Blocks.LANTERN.getDefaultState()
            .with(Properties.HANGING, true);
        BlockState chain = Blocks.CHAIN.getDefaultState();

        // 4 hanging lanterns inside
        int[][] lanternPositions = {{-3, 0}, {3, 0}, {0, -1}, {0, 1}};
        for (int[] pos : lanternPositions) {
            world.setBlockState(bp(pos[0], CEILING_Y, pos[1]), chain);
            world.setBlockState(bp(pos[0], CEILING_Y - 1, pos[1]), lantern);
        }

        // Soul lanterns on porch posts
        BlockState soulLantern = Blocks.SOUL_LANTERN.getDefaultState()
            .with(Properties.HANGING, false);
        world.setBlockState(bp(PORCH_MIN_X, INTERIOR_Y + 3, PORCH_MAX_Z), soulLantern);
        world.setBlockState(bp(PORCH_MAX_X, INTERIOR_Y + 3, PORCH_MAX_Z), soulLantern);
    }

    private static void setSpawn(ServerWorld world) {
        BlockPos spawnPos = new BlockPos(0, INTERIOR_Y, 0);
        world.setSpawnPos(spawnPos, 0f);
    }

    /** Check if a position is part of the central lobby hub shell. */
    public static boolean isHubShell(BlockPos pos) {
        return isHubShell(pos, new BlockPos(0, INTERIOR_Y, 0));
    }

    /**
     * Check if a position is part of a hub shell centered at hubCenter (protected from breaking).
     * Players can modify the interior but not the structure itself.
     */
    public static boolean isHubShell(BlockPos pos, BlockPos hubCenter) {
        int x = pos.getX() - hubCenter.getX(), y = pos.getY(), z = pos.getZ() - hubCenter.getZ();

        // Foundation and below — always protected
        if (y <= FLOOR_Y && isWithinFootprint(x, z)) return true;

        // Ceiling level — protected
        if (y == CEILING_Y && isWithinFootprint(x, z)) return true;

        // Roof (Y > CEILING_Y) — protected
        if (y > CEILING_Y && isWithinRoofprint(x, y, z)) return true;

        // Main room walls (Y = 65-67)
        if (y >= INTERIOR_Y && y <= INTERIOR_Y + 2) {
            // West wall
            if (x == MAIN_MIN_X && z >= MAIN_MIN_Z && z <= MAIN_MAX_Z) return true;
            // East wall (excluding bump-out opening)
            if (x == MAIN_MAX_X && z >= MAIN_MIN_Z && z <= MAIN_MAX_Z
                && !(z > BUMP_MIN_Z && z < BUMP_MAX_Z)) return true;
            // North wall
            if (z == MAIN_MIN_Z && x >= MAIN_MIN_X && x <= MAIN_MAX_X) return true;
            // South wall
            if (z == MAIN_MAX_Z && x >= MAIN_MIN_X && x <= MAIN_MAX_X) return true;

            // Bump-out walls
            if (x == BUMP_MAX_X && z >= BUMP_MIN_Z && z <= BUMP_MAX_Z) return true;
            if (z == BUMP_MIN_Z && x >= MAIN_MAX_X && x <= BUMP_MAX_X) return true;
            if (z == BUMP_MAX_Z && x >= MAIN_MAX_X && x <= BUMP_MAX_X) return true;
        }

        // Porch posts
        if ((x == PORCH_MIN_X || x == PORCH_MAX_X) && z == PORCH_MAX_Z
            && y >= INTERIOR_Y && y <= INTERIOR_Y + 2) return true;

        return false;
    }

    private static boolean isWithinFootprint(int x, int z) {
        boolean inMain = x >= MAIN_MIN_X && x <= MAIN_MAX_X
            && z >= MAIN_MIN_Z && z <= MAIN_MAX_Z;
        boolean inBump = x >= BUMP_MIN_X && x <= BUMP_MAX_X
            && z >= BUMP_MIN_Z && z <= BUMP_MAX_Z;
        boolean inPorch = x >= PORCH_MIN_X && x <= PORCH_MAX_X
            && z >= PORCH_MIN_Z && z <= PORCH_MAX_Z;
        // Include foundation overhang (1 block wider)
        boolean inMainFound = x >= MAIN_MIN_X - 1 && x <= MAIN_MAX_X + 1
            && z >= MAIN_MIN_Z - 1 && z <= MAIN_MAX_Z + 1;
        boolean inBumpFound = x >= BUMP_MIN_X - 1 && x <= BUMP_MAX_X + 1
            && z >= BUMP_MIN_Z - 1 && z <= BUMP_MAX_Z + 1;
        return inMain || inBump || inPorch || inMainFound || inBumpFound;
    }

    private static boolean isWithinRoofprint(int x, int y, int z) {
        int roofMinX = MAIN_MIN_X - 1;
        int roofMaxX = MAIN_MAX_X + 1;
        if (x < roofMinX || x > roofMaxX) return false;

        // Check if the position is part of the gabled roof
        // Roof narrows as Y increases
        int roofY = y - CEILING_Y; // 0 = ceiling, 1 = first slope, etc.
        int halfWidth = (MAIN_MAX_Z - MAIN_MIN_Z + 2) / 2 - roofY + 1;
        if (halfWidth < 0) return false;
        int center = (MAIN_MIN_Z + MAIN_MAX_Z) / 2;
        return z >= center - halfWidth && z <= center + halfWidth;
    }
}
