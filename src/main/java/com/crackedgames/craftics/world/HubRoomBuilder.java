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

    public static void build(ServerWorld world) {
        CrafticsMod.LOGGER.info("Building Craftics hub cottage...");
        Random rng = world.getRandom();

        buildOutdoorArea(world);
        clearArea(world);
        buildFoundation(world, rng);
        buildFloor(world);
        buildWalls(world);
        buildBumpOut(world);
        buildWindows(world);
        buildDoor(world);
        buildCeiling(world);
        buildRoof(world);
        buildPorch(world);
        buildChimney(world);
        placeFurniture(world);
        placeLighting(world);
        setSpawn(world);

        CrafticsMod.LOGGER.info("Hub cottage built successfully.");
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
                world.setBlockState(new BlockPos(x, YARD_Y - DIRT_DEPTH, z), bedrock);
                // 3 layers of dirt
                for (int d = 1; d <= DIRT_DEPTH; d++) {
                    world.setBlockState(new BlockPos(x, YARD_Y - DIRT_DEPTH + d, z), dirt);
                }
                // Grass on top
                world.setBlockState(new BlockPos(x, YARD_Y + 1, z), grass);
                // Clear air above (10 blocks high for trees, buildings, etc.)
                for (int y = YARD_Y + 2; y <= YARD_Y + 12; y++) {
                    world.setBlockState(new BlockPos(x, y, z), air);
                }
            }
        }

        // Place a fence around the yard perimeter
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();
        for (int x = YARD_MIN_X; x <= YARD_MAX_X; x++) {
            world.setBlockState(new BlockPos(x, YARD_Y + 2, YARD_MIN_Z), fence);
            world.setBlockState(new BlockPos(x, YARD_Y + 2, YARD_MAX_Z), fence);
        }
        for (int z = YARD_MIN_Z; z <= YARD_MAX_Z; z++) {
            world.setBlockState(new BlockPos(YARD_MIN_X, YARD_Y + 2, z), fence);
            world.setBlockState(new BlockPos(YARD_MAX_X, YARD_Y + 2, z), fence);
        }

        // Gate openings (south side, centered)
        world.setBlockState(new BlockPos(-1, YARD_Y + 2, YARD_MAX_Z), air);
        world.setBlockState(new BlockPos(0, YARD_Y + 2, YARD_MAX_Z), air);
        world.setBlockState(new BlockPos(1, YARD_Y + 2, YARD_MAX_Z), air);

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
            world.setBlockState(new BlockPos(x, baseY + y, z), log);
        }
        // Leaf canopy (3x3x2 on top)
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                world.setBlockState(new BlockPos(x + lx, baseY + 3, z + lz), leaves);
                world.setBlockState(new BlockPos(x + lx, baseY + 4, z + lz), leaves);
            }
        }
        world.setBlockState(new BlockPos(x, baseY + 5, z), leaves);
    }

    private static void clearArea(ServerWorld world) {
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = -8; x <= 10; x++) {
            for (int z = -6; z <= 7; z++) {
                for (int y = 62; y <= 73; y++) {
                    world.setBlockState(new BlockPos(x, y, z), air);
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
                    world.setBlockState(new BlockPos(x, y, z), stone);
                }
            }
            // Bump-out foundation
            for (int x = BUMP_MIN_X - 1; x <= BUMP_MAX_X + 1; x++) {
                for (int z = BUMP_MIN_Z - 1; z <= BUMP_MAX_Z + 1; z++) {
                    BlockState stone = rng.nextInt(4) == 0
                        ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                        : Blocks.COBBLESTONE.getDefaultState();
                    world.setBlockState(new BlockPos(x, y, z), stone);
                }
            }
            // Porch foundation
            for (int x = PORCH_MIN_X - 1; x <= PORCH_MAX_X + 1; x++) {
                for (int z = PORCH_MIN_Z; z <= PORCH_MAX_Z + 1; z++) {
                    BlockState stone = rng.nextInt(3) == 0
                        ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                        : Blocks.COBBLESTONE.getDefaultState();
                    world.setBlockState(new BlockPos(x, y, z), stone);
                }
            }
        }
    }

    private static void buildFloor(ServerWorld world) {
        BlockState oakPlanks = Blocks.OAK_PLANKS.getDefaultState();
        // Main room floor
        for (int x = MAIN_MIN_X; x <= MAIN_MAX_X; x++) {
            for (int z = MAIN_MIN_Z; z <= MAIN_MAX_Z; z++) {
                world.setBlockState(new BlockPos(x, FLOOR_Y, z), oakPlanks);
            }
        }
        // Bump-out floor
        for (int x = BUMP_MIN_X; x <= BUMP_MAX_X; x++) {
            for (int z = BUMP_MIN_Z; z <= BUMP_MAX_Z; z++) {
                world.setBlockState(new BlockPos(x, FLOOR_Y, z), oakPlanks);
            }
        }
        // Clear interior air above floor
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = MAIN_MIN_X + 1; x < MAIN_MAX_X; x++) {
            for (int z = MAIN_MIN_Z + 1; z < MAIN_MAX_Z; z++) {
                for (int y = INTERIOR_Y; y <= INTERIOR_Y + 2; y++) {
                    world.setBlockState(new BlockPos(x, y, z), air);
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
                world.setBlockState(new BlockPos(c[0], y, c[1]), darkOakLog);
            }
        }

        // North wall (Z = MAIN_MIN_Z), runs along X
        for (int x = MAIN_MIN_X + 1; x < MAIN_MAX_X; x++) {
            world.setBlockState(new BlockPos(x, INTERIOR_Y, MAIN_MIN_Z), wainscotX);
            world.setBlockState(new BlockPos(x, INTERIOR_Y + 1, MAIN_MIN_Z), oakPlanks);
            world.setBlockState(new BlockPos(x, INTERIOR_Y + 2, MAIN_MIN_Z), oakPlanks);
            world.setBlockState(new BlockPos(x, CEILING_Y, MAIN_MIN_Z), beamX);
        }

        // South wall (Z = MAIN_MAX_Z), runs along X
        for (int x = MAIN_MIN_X + 1; x < MAIN_MAX_X; x++) {
            world.setBlockState(new BlockPos(x, INTERIOR_Y, MAIN_MAX_Z), wainscotX);
            world.setBlockState(new BlockPos(x, INTERIOR_Y + 1, MAIN_MAX_Z), oakPlanks);
            world.setBlockState(new BlockPos(x, INTERIOR_Y + 2, MAIN_MAX_Z), oakPlanks);
            world.setBlockState(new BlockPos(x, CEILING_Y, MAIN_MAX_Z), beamX);
        }

        // West wall (X = MAIN_MIN_X), runs along Z
        for (int z = MAIN_MIN_Z + 1; z < MAIN_MAX_Z; z++) {
            world.setBlockState(new BlockPos(MAIN_MIN_X, INTERIOR_Y, z), wainscotZ);
            world.setBlockState(new BlockPos(MAIN_MIN_X, INTERIOR_Y + 1, z), oakPlanks);
            world.setBlockState(new BlockPos(MAIN_MIN_X, INTERIOR_Y + 2, z), oakPlanks);
            world.setBlockState(new BlockPos(MAIN_MIN_X, CEILING_Y, z), beamZ);
        }

        // East wall (X = MAIN_MAX_X), runs along Z — but leave opening for bump-out
        for (int z = MAIN_MIN_Z + 1; z < MAIN_MAX_Z; z++) {
            if (z >= BUMP_MIN_Z && z <= BUMP_MAX_Z) continue; // opening to bump-out
            world.setBlockState(new BlockPos(MAIN_MAX_X, INTERIOR_Y, z), wainscotZ);
            world.setBlockState(new BlockPos(MAIN_MAX_X, INTERIOR_Y + 1, z), oakPlanks);
            world.setBlockState(new BlockPos(MAIN_MAX_X, INTERIOR_Y + 2, z), oakPlanks);
            world.setBlockState(new BlockPos(MAIN_MAX_X, CEILING_Y, z), beamZ);
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
                world.setBlockState(new BlockPos(c[0], y, c[1]), darkOakLog);
            }
        }
        // Transition posts where bump meets main wall
        for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
            world.setBlockState(new BlockPos(MAIN_MAX_X, y, BUMP_MIN_Z), darkOakLog);
            world.setBlockState(new BlockPos(MAIN_MAX_X, y, BUMP_MAX_Z), darkOakLog);
        }

        // East wall of bump-out (X = BUMP_MAX_X)
        for (int z = BUMP_MIN_Z + 1; z < BUMP_MAX_Z; z++) {
            world.setBlockState(new BlockPos(BUMP_MAX_X, INTERIOR_Y, z), wainscotZ);
            world.setBlockState(new BlockPos(BUMP_MAX_X, INTERIOR_Y + 1, z), oakPlanks);
            world.setBlockState(new BlockPos(BUMP_MAX_X, INTERIOR_Y + 2, z), oakPlanks);
            world.setBlockState(new BlockPos(BUMP_MAX_X, CEILING_Y, z), beamZ);
        }

        // North wall of bump-out (Z = BUMP_MIN_Z)
        for (int x = BUMP_MIN_X; x < BUMP_MAX_X; x++) {
            world.setBlockState(new BlockPos(x, INTERIOR_Y, BUMP_MIN_Z), wainscotX);
            world.setBlockState(new BlockPos(x, INTERIOR_Y + 1, BUMP_MIN_Z), oakPlanks);
            world.setBlockState(new BlockPos(x, INTERIOR_Y + 2, BUMP_MIN_Z), oakPlanks);
            world.setBlockState(new BlockPos(x, CEILING_Y, BUMP_MIN_Z), beamX);
        }

        // South wall of bump-out (Z = BUMP_MAX_Z)
        for (int x = BUMP_MIN_X; x < BUMP_MAX_X; x++) {
            world.setBlockState(new BlockPos(x, INTERIOR_Y, BUMP_MAX_Z), wainscotX);
            world.setBlockState(new BlockPos(x, INTERIOR_Y + 1, BUMP_MAX_Z), oakPlanks);
            world.setBlockState(new BlockPos(x, INTERIOR_Y + 2, BUMP_MAX_Z), oakPlanks);
            world.setBlockState(new BlockPos(x, CEILING_Y, BUMP_MAX_Z), beamX);
        }

        // Clear bump-out interior air
        for (int x = BUMP_MIN_X; x < BUMP_MAX_X; x++) {
            for (int z = BUMP_MIN_Z + 1; z < BUMP_MAX_Z; z++) {
                for (int y = INTERIOR_Y; y <= INTERIOR_Y + 2; y++) {
                    world.setBlockState(new BlockPos(x, y, z), air);
                }
            }
        }
        // Clear opening between main room and bump-out
        for (int z = BUMP_MIN_Z + 1; z < BUMP_MAX_Z; z++) {
            for (int y = INTERIOR_Y; y <= INTERIOR_Y + 2; y++) {
                world.setBlockState(new BlockPos(MAIN_MAX_X, y, z), air);
            }
        }

        // Bump-out ceiling
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.getDefaultState();
        for (int x = BUMP_MIN_X; x <= BUMP_MAX_X; x++) {
            for (int z = BUMP_MIN_Z; z <= BUMP_MAX_Z; z++) {
                world.setBlockState(new BlockPos(x, CEILING_Y, z), sprucePlanks);
            }
        }
    }

    private static void buildWindows(ServerWorld world) {
        BlockState glassPane = Blocks.GLASS_PANE.getDefaultState();

        // North wall windows (Z = MAIN_MIN_Z) — two 2-wide windows at Y=66-67
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            // Left window
            world.setBlockState(new BlockPos(-3, y, MAIN_MIN_Z), glassPane);
            world.setBlockState(new BlockPos(-2, y, MAIN_MIN_Z), glassPane);
            // Right window
            world.setBlockState(new BlockPos(2, y, MAIN_MIN_Z), glassPane);
            world.setBlockState(new BlockPos(3, y, MAIN_MIN_Z), glassPane);
        }

        // South wall windows (Z = MAIN_MAX_Z) — two windows, leaving center for door
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            // Left window
            world.setBlockState(new BlockPos(-4, y, MAIN_MAX_Z), glassPane);
            world.setBlockState(new BlockPos(-3, y, MAIN_MAX_Z), glassPane);
            // Right window
            world.setBlockState(new BlockPos(3, y, MAIN_MAX_Z), glassPane);
            world.setBlockState(new BlockPos(4, y, MAIN_MAX_Z), glassPane);
        }

        // West wall window (X = MAIN_MIN_X) — one 2-tall window
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(new BlockPos(MAIN_MIN_X, y, -1), glassPane);
            world.setBlockState(new BlockPos(MAIN_MIN_X, y, 0), glassPane);
        }

        // Bump-out east window (X = BUMP_MAX_X) — one 2-tall window
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(new BlockPos(BUMP_MAX_X, y, 0), glassPane);
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
            world.setBlockState(new BlockPos(-4, y, MAIN_MIN_Z - 1), shutterN);
            world.setBlockState(new BlockPos(-1, y, MAIN_MIN_Z - 1), shutterN);
            world.setBlockState(new BlockPos(1, y, MAIN_MIN_Z - 1), shutterN);
            world.setBlockState(new BlockPos(4, y, MAIN_MIN_Z - 1), shutterN);
        }

        // South wall shutters (placed on Z = MAIN_MAX_Z + 1)
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(new BlockPos(-5, y, MAIN_MAX_Z + 1), shutterS);
            world.setBlockState(new BlockPos(-2, y, MAIN_MAX_Z + 1), shutterS);
            world.setBlockState(new BlockPos(2, y, MAIN_MAX_Z + 1), shutterS);
            world.setBlockState(new BlockPos(5, y, MAIN_MAX_Z + 1), shutterS);
        }

        // West wall shutters (placed on X = MAIN_MIN_X - 1)
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(new BlockPos(MAIN_MIN_X - 1, y, -2), shutterW);
            world.setBlockState(new BlockPos(MAIN_MIN_X - 1, y, 1), shutterW);
        }

        // Bump-out east shutters (placed on X = BUMP_MAX_X + 1)
        for (int y = INTERIOR_Y + 1; y <= INTERIOR_Y + 2; y++) {
            world.setBlockState(new BlockPos(BUMP_MAX_X + 1, y, -1), shutterE);
            world.setBlockState(new BlockPos(BUMP_MAX_X + 1, y, 1), shutterE);
        }
    }

    private static void buildDoor(ServerWorld world) {
        BlockState doorLower = Blocks.OAK_DOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = Blocks.OAK_DOOR.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);

        world.setBlockState(new BlockPos(0, INTERIOR_Y, MAIN_MAX_Z), doorLower);
        world.setBlockState(new BlockPos(0, INTERIOR_Y + 1, MAIN_MAX_Z), doorUpper);
    }

    private static void buildCeiling(ServerWorld world) {
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.getDefaultState();
        // Main room ceiling
        for (int x = MAIN_MIN_X; x <= MAIN_MAX_X; x++) {
            for (int z = MAIN_MIN_Z; z <= MAIN_MAX_Z; z++) {
                world.setBlockState(new BlockPos(x, CEILING_Y, z), sprucePlanks);
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
            world.setBlockState(new BlockPos(x, 68, MAIN_MIN_Z - 1), stairsN);
            world.setBlockState(new BlockPos(x, 68, MAIN_MAX_Z + 1), stairsS);
            // Y=69
            world.setBlockState(new BlockPos(x, 69, MAIN_MIN_Z), stairsN);
            world.setBlockState(new BlockPos(x, 69, MAIN_MAX_Z), stairsS);
            // Y=70
            world.setBlockState(new BlockPos(x, 70, MAIN_MIN_Z + 1), stairsN);
            world.setBlockState(new BlockPos(x, 70, MAIN_MAX_Z - 1), stairsS);
            // Y=71 — ridge cap (slabs)
            world.setBlockState(new BlockPos(x, 71, MAIN_MIN_Z + 2), slab);
            world.setBlockState(new BlockPos(x, 71, MAIN_MAX_Z - 2), slab);
        }

        // Fill under roof with air (attic space)
        BlockState air = Blocks.AIR.getDefaultState();
        for (int y = 69; y <= 71; y++) {
            for (int x = MAIN_MIN_X; x <= MAIN_MAX_X; x++) {
                for (int z = MAIN_MIN_Z; z <= MAIN_MAX_Z; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
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
            world.setBlockState(new BlockPos(x, 69, z), fill);
        }
        // Y=70: Z from MAIN_MIN_Z+1 to MAIN_MAX_Z-1
        for (int z = MAIN_MIN_Z + 1; z <= MAIN_MAX_Z - 1; z++) {
            world.setBlockState(new BlockPos(x, 70, z), fill);
        }
        // Y=71: Z from MAIN_MIN_Z+2 to MAIN_MAX_Z-2 (just the center)
        for (int z = MAIN_MIN_Z + 2; z <= MAIN_MAX_Z - 2; z++) {
            world.setBlockState(new BlockPos(x, 71, z), fill);
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
                world.setBlockState(new BlockPos(x, FLOOR_Y, z), oakSlab);
            }
        }

        // Porch support posts
        world.setBlockState(new BlockPos(PORCH_MIN_X, INTERIOR_Y, PORCH_MAX_Z), post);
        world.setBlockState(new BlockPos(PORCH_MAX_X, INTERIOR_Y, PORCH_MAX_Z), post);
        world.setBlockState(new BlockPos(PORCH_MIN_X, INTERIOR_Y + 1, PORCH_MAX_Z), post);
        world.setBlockState(new BlockPos(PORCH_MAX_X, INTERIOR_Y + 1, PORCH_MAX_Z), post);
        world.setBlockState(new BlockPos(PORCH_MIN_X, INTERIOR_Y + 2, PORCH_MAX_Z), post);
        world.setBlockState(new BlockPos(PORCH_MAX_X, INTERIOR_Y + 2, PORCH_MAX_Z), post);

        // Fence railing along porch edge (Z = PORCH_MAX_Z), skip middle for entry
        for (int x = PORCH_MIN_X + 1; x < PORCH_MAX_X; x++) {
            if (Math.abs(x) <= 1) continue; // entry gap
            world.setBlockState(new BlockPos(x, INTERIOR_Y, PORCH_MAX_Z), fence);
        }
        // Side railings
        world.setBlockState(new BlockPos(PORCH_MIN_X, INTERIOR_Y, PORCH_MIN_Z), fence);
        world.setBlockState(new BlockPos(PORCH_MAX_X, INTERIOR_Y, PORCH_MIN_Z), fence);

        // Steps down from porch (cobble step at Z = PORCH_MAX_Z + 1)
        BlockState cobbleSlab = Blocks.COBBLESTONE_SLAB.getDefaultState()
            .with(Properties.SLAB_TYPE, SlabType.TOP);
        for (int x = -1; x <= 1; x++) {
            world.setBlockState(new BlockPos(x, FLOOR_Y, PORCH_MAX_Z + 1), cobbleSlab);
        }

        // Path from steps (a few cobblestone blocks)
        for (int x = -1; x <= 1; x++) {
            world.setBlockState(new BlockPos(x, FLOOR_Y - 1, PORCH_MAX_Z + 2), cobble);
            world.setBlockState(new BlockPos(x, FLOOR_Y - 1, PORCH_MAX_Z + 3), cobble);
        }
    }

    private static void buildChimney(ServerWorld world) {
        BlockState bricks = Blocks.BRICKS.getDefaultState();
        BlockState wall = Blocks.BRICK_WALL.getDefaultState();

        // Chimney on the bump-out roof, rising from Y=69 to Y=72
        int cx = BUMP_MAX_X, cz = 0;
        for (int y = CEILING_Y; y <= 72; y++) {
            world.setBlockState(new BlockPos(cx, y, cz), bricks);
        }
        // Chimney cap
        world.setBlockState(new BlockPos(cx, 73, cz), wall);
    }

    private static void placeFurniture(ServerWorld world) {
        // Level select block — centered on north wall
        world.setBlockState(new BlockPos(0, INTERIOR_Y, MAIN_MIN_Z + 1),
            ModBlocks.LEVEL_SELECT_BLOCK.getDefaultState());

        // Furnace — against NW interior wall, facing into room
        world.setBlockState(new BlockPos(MAIN_MIN_X + 1, INTERIOR_Y, MAIN_MIN_Z + 1),
            Blocks.FURNACE.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.EAST));

        // Crafting table — between furnace and chest on west wall
        world.setBlockState(new BlockPos(MAIN_MIN_X + 1, INTERIOR_Y, 0),
            Blocks.CRAFTING_TABLE.getDefaultState());

        // Chest — against SW interior wall
        world.setBlockState(new BlockPos(MAIN_MIN_X + 1, INTERIOR_Y, MAIN_MAX_Z - 1),
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
            world.setBlockState(new BlockPos(pos[0], CEILING_Y, pos[1]), chain);
            world.setBlockState(new BlockPos(pos[0], CEILING_Y - 1, pos[1]), lantern);
        }

        // Soul lanterns on porch posts
        BlockState soulLantern = Blocks.SOUL_LANTERN.getDefaultState()
            .with(Properties.HANGING, false);
        world.setBlockState(new BlockPos(PORCH_MIN_X, INTERIOR_Y + 3, PORCH_MAX_Z), soulLantern);
        world.setBlockState(new BlockPos(PORCH_MAX_X, INTERIOR_Y + 3, PORCH_MAX_Z), soulLantern);
    }

    private static void setSpawn(ServerWorld world) {
        BlockPos spawnPos = new BlockPos(0, INTERIOR_Y, 0);
        world.setSpawnPos(spawnPos, 0f);
    }

    /**
     * Check if a position is part of the hub shell (protected from breaking).
     * Players can modify the interior but not the structure itself.
     */
    public static boolean isHubShell(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

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
