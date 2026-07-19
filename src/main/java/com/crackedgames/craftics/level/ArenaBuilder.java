package com.crackedgames.craftics.level;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds combat arenas from .schem or .nbt structures with marker block detection.
 *
 * Rectangular arenas (markers at floor level - they ARE the inside corner tiles):
 *   DIAMOND_BLOCK  = camera corner
 *   EMERALD_BLOCK  = opposite corner
 *   Playable area is the rectangle bounded by those two tiles; the biome-themed
 *   concrete border ring sits one tile further out.
 *
 * Polygon (non-rectangular) arenas:
 *   3+ ARENA_CORNER_BLOCK markers at the vertices outline the shape; the playable
 *   floor is that outline eroded one tile inward, so the markers form the border
 *   ring just outside the floor. An optional DIAMOND_BLOCK placed at one vertex is
 *   the camera-corner vertex - it joins the outline AND aims the camera from there
 *   toward the centroid (without one, the first sorted corner is used).
 *
 * Optional spawn markers (inside the playable area):
 *   GOLD_BLOCK / IRON_BLOCK / COPPER_BLOCK / COAL_BLOCK = P1..P4
 *   If GOLD_BLOCK is missing, P1 spawn is auto-derived from arena geometry.
 */
public class ArenaBuilder {

    // FORCE_STATE skips per-block client updates - client gets full chunk data after build
    static final int SET_FLAGS = Block.FORCE_STATE;

    private static float pendingCameraYaw = -1;
    private static BlockPos structureOrigin = null;
    private static GridPos structurePlayerStart = null;
    private static int structureGridW = -1, structureGridH = -1;
    /** Polygon-mask shape for non-rectangular arenas, or null when the
     *  scanned structure used the legacy DIAMOND/EMERALD rectangle pair. */
    private static boolean[][] structureInsideMask = null;
    /** Outer polygon (playable mask + its border ring), or null. Used to keep
     *  bbox-wide passes (the clear-above sweep) off the terrain outside the
     *  drawn outline. */
    private static boolean[][] structureOuterMask = null;
    private static int structureHeight = -1;

    /** Get and clear the pending camera yaw from the last structure load */
    public static float consumePendingCameraYaw() {
        float yaw = pendingCameraYaw;
        pendingCameraYaw = -1;
        return yaw;
    }

    /** Isometric camera yaw (degrees) looking from (fromX,fromZ) toward (toX,toZ).
     *  Shared by the rectangular (DIAMOND corner) and polygon (DIAMOND vertex /
     *  first-corner) camera-side derivations. */
    private static float yawFromTo(double fromX, double fromZ, double toX, double toZ) {
        double dx = fromX - toX;
        double dz = fromZ - toZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + 180f;
        if (yaw < 0f) yaw += 360f;
        return yaw;
    }

    public static GridArena build(ServerWorld world, LevelDefinition levelDef) {
        if (refuseSyntheticLevel(levelDef)) return null;
        int level = levelDef.getLevelNumber();
        BlockPos origin = GridArena.arenaOriginForLevel(level);
        return buildAt(world, levelDef, origin);
    }

    public static GridArena build(ServerWorld world, LevelDefinition levelDef, java.util.UUID worldOwner) {
        if (refuseSyntheticLevel(levelDef)) return null;
        com.crackedgames.craftics.world.CrafticsSavedData data =
            com.crackedgames.craftics.world.CrafticsSavedData.get(world);
        BlockPos origin = data.getArenaOrigin(worldOwner, levelDef.getLevelNumber());
        if (origin == null) {
            origin = GridArena.arenaOriginForLevel(levelDef.getLevelNumber());
        }
        return buildAt(world, levelDef, origin);
    }

    /**
     * Synthetic event levels (level number >= {@link LevelDefinition#SYNTHETIC_LEVEL_BASE})
     * only carry that number as a logging/metadata id - routing it through the
     * numbered-arena origin formulas turns it into a nonsense world coordinate
     * (potentially inside or beyond real arena rows). Such defs must be placed
     * via {@link #buildAt} at their dedicated override origin; if a caller lands
     * here anyway, refuse the build so the failure is visible instead of a
     * misplaced arena.
     */
    private static boolean refuseSyntheticLevel(LevelDefinition levelDef) {
        if (levelDef.getLevelNumber() < LevelDefinition.SYNTHETIC_LEVEL_BASE) return false;
        CrafticsMod.LOGGER.error(
            "ArenaBuilder.build: refusing to place synthetic event level {} ('{}') via the "
            + "numbered-arena origin formula; it must be built at its override origin via buildAt",
            levelDef.getLevelNumber(), levelDef.getName());
        return true;
    }

    /**
     * Create a GridArena from an already-placed (pre-generated) arena.
     * Scans existing world blocks for obstacles/water/voids - no blocks are placed.
     * Chunks are force-loaded and resent to the client.
     */
    private static final int TRAP_FLAGS =
        Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    /** How tall the perimeter barrier wall stands above the arena floor. Matches the
     *  ceiling the arena clears to, so flying mobs (ghast/phantom/blaze) can't cross. */
    static final int PERIMETER_WALL_HEIGHT = 18;

    /**
     * Seal the ring of tiles ONE OUT from the playable grid with an invisible barrier
     * wall, so mobs from the surrounding world can't wander in. Only walkable gaps are
     * sealed: for each border tile, if the block at floor+1 is air or a non-solid
     * pass-through (grass, a flower, snow layer, ...) a barrier column is raised there;
     * if a solid block already stands (a natural stone wall, a hill, a fence), that tile
     * is left untouched so the existing scenery keeps blocking mobs and stays smooth.
     *
     * <p>Idempotent - a barrier already standing is simply re-set - so it is safe to run
     * on every arena entry, including cached-arena revisits.
     *
     * @param floorX,floorY,floorZ  the playable grid's origin (its 0,0 floor block)
     * @param gridW,gridH           the playable grid size
     */
    static void sealArenaPerimeter(ServerWorld world, int floorX, int floorY, int floorZ,
                                   int gridW, int gridH) {
        int minX = floorX - 1, maxX = floorX + gridW;
        int minZ = floorZ - 1, maxZ = floorZ + gridH;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Border ring only: skip the interior (the playable tiles).
                if (x > minX && x < maxX && z > minZ && z < maxZ) continue;
                sealBorderColumn(world, x, floorY, z);
            }
        }
    }

    /** Raise a barrier column on one border tile, unless a solid block already walls it. */
    private static void sealBorderColumn(ServerWorld world, int x, int floorY, int z) {
        BlockPos wallStart = new BlockPos(x, floorY + 1, z);
        BlockState atWall = world.getBlockState(wallStart);
        // A solid full block at head level is already a wall - leave the scenery be.
        if (atWall.isSolidBlock(world, wallStart) && !atWall.isOf(Blocks.BARRIER)) return;
        for (int dy = 1; dy <= PERIMETER_WALL_HEIGHT; dy++) {
            BlockPos p = new BlockPos(x, floorY + dy, z);
            BlockState s = world.getBlockState(p);
            // Only fill air / walkable pass-throughs; never overwrite a real block that
            // isn't ours, so a tree trunk or overhang on the edge stays intact.
            if (s.isAir() || s.isReplaceable() || s.isOf(Blocks.BARRIER)
                    || s.isOf(Blocks.SHORT_GRASS) || s.isOf(Blocks.TALL_GRASS)
                    || s.isOf(Blocks.FERN) || s.isOf(Blocks.LARGE_FERN)
                    || s.isOf(Blocks.SNOW)) {
                world.setBlockState(p, Blocks.BARRIER.getDefaultState(), TRAP_FLAGS);
            }
        }
    }

    /**
     * Remove bow-trap blocks (Everbloom rose bushes, Bubbleveil bubble columns) left in an
     * arena footprint by a fight that didn't clean up, so they don't get scanned in as
     * permanent terrain. Rose bushes become air; a bubble column (water over soul sand)
     * has both its water and its soul sand replaced with the arena's own floor material,
     * sampled from a clean neighbouring tile.
     */
    private static void scrubTrapLeftovers(ServerWorld world, int floorX, int floorY, int floorZ,
                                           int gridW, int gridH) {
        Block floorFill = sampleArenaFloor(world, floorX, floorY, floorZ, gridW, gridH);
        for (int x = 0; x < gridW; x++) {
            for (int z = 0; z < gridH; z++) {
                // Rose bush trap lives in the overlay column (floorY+1 / floorY+2).
                for (int dy = 1; dy <= 2; dy++) {
                    BlockPos p = new BlockPos(floorX + x, floorY + dy, floorZ + z);
                    if (world.getBlockState(p).isOf(Blocks.ROSE_BUSH)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), TRAP_FLAGS);
                    }
                }
                // Bubble column trap: WATER (or the BUBBLE_COLUMN it becomes) at floor over
                // SOUL_SAND one below. Restore both to the arena's floor material.
                BlockPos floorPos = new BlockPos(floorX + x, floorY, floorZ + z);
                BlockPos underPos = new BlockPos(floorX + x, floorY - 1, floorZ + z);
                Block fb = world.getBlockState(floorPos).getBlock();
                if ((fb == Blocks.WATER || fb == Blocks.BUBBLE_COLUMN)
                        && world.getBlockState(underPos).isOf(Blocks.SOUL_SAND)) {
                    world.setBlockState(floorPos, floorFill.getDefaultState(), TRAP_FLAGS);
                    world.setBlockState(underPos, floorFill.getDefaultState(), TRAP_FLAGS);
                }
            }
        }
    }

    /** A solid floor block from a tile with no trap on it, to patch a scrubbed column with. */
    private static Block sampleArenaFloor(ServerWorld world, int floorX, int floorY, int floorZ,
                                          int gridW, int gridH) {
        for (int x = 0; x < gridW; x++) {
            for (int z = 0; z < gridH; z++) {
                net.minecraft.block.BlockState s =
                    world.getBlockState(new BlockPos(floorX + x, floorY, floorZ + z));
                Block b = s.getBlock();
                if (s.isSolidBlock(world, new BlockPos(floorX + x, floorY, floorZ + z))
                        && b != Blocks.SOUL_SAND) {
                    return b;
                }
            }
        }
        return Blocks.STONE;
    }

    public static GridArena scanExisting(ServerWorld world, BlockPos gridOrigin,
                                          int gridW, int gridH, GridPos playerStart, int level,
                                          boolean[][] insideMask) {
        int floorX = gridOrigin.getX(), floorY = gridOrigin.getY(), floorZ = gridOrigin.getZ();

        // Load and resend chunks to client (not force-loaded - CombatManager handles that)
        int minCX = (floorX - 2) >> 4;
        int maxCX = (floorX + gridW + 2) >> 4;
        int minCZ = (floorZ - 2) >> 4;
        int maxCZ = (floorZ + gridH + 2) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                var chunk = world.getChunk(cx, cz);
                if (chunk != null) {
                    for (var p : world.getPlayers()) {
                        ((net.minecraft.server.network.ServerPlayerEntity) p)
                            .networkHandler.chunkDataSender.add(chunk);
                    }
                }
            }
        }

        // Heal leftover bow-trap blocks before the scan reads them. A rose bush (Everbloom
        // field) or a bubble column (Bubbleveil) left behind by a fight that didn't clean
        // up would otherwise be read as permanent arena terrain and baked in forever. No
        // natural arena floor uses these, so removing them here is unambiguous. This also
        // repairs saves already polluted by the earlier restore-order bug.
        scrubTrapLeftovers(world, floorX, floorY, floorZ, gridW, gridH);

        // Scan existing blocks to build tile grid
        GridTile[][] tiles = new GridTile[gridW][gridH];
        for (int x = 0; x < gridW; x++) {
            for (int z = 0; z < gridH; z++) {
                BlockPos floorPos = new BlockPos(floorX + x, floorY, floorZ + z);
                BlockPos abovePos = new BlockPos(floorX + x, floorY + 1, floorZ + z);
                BlockPos headPos = new BlockPos(floorX + x, floorY + 2, floorZ + z);
                net.minecraft.block.BlockState floorState = world.getBlockState(floorPos);
                net.minecraft.block.BlockState aboveState = world.getBlockState(abovePos);
                net.minecraft.block.BlockState headState = world.getBlockState(headPos);

                Block floorBlock = floorState.getBlock();

                // Water at floor level - check depth
                if (!floorState.getFluidState().isEmpty() && floorBlock == Blocks.WATER) {
                    BlockPos belowWater = new BlockPos(floorX + x, floorY - 1, floorZ + z);
                    boolean isDeep = !world.getBlockState(belowWater).getFluidState().isEmpty();
                    tiles[x][z] = new GridTile(isDeep
                        ? com.crackedgames.craftics.core.TileType.DEEP_WATER
                        : com.crackedgames.craftics.core.TileType.WATER, Blocks.WATER);
                    continue;
                }

                // Lava at floor level = LAVA tile. Cap the depth so the
                // player can't fall through a multi-block lava column to an
                // instant death - fill any lava directly below with stone,
                // turning a hidden pit into a fair single-tile hazard.
                if (floorBlock == Blocks.LAVA || floorBlock == Blocks.MAGMA_BLOCK) {
                    capLavaDepth(world, floorX + x, floorY, floorZ + z);
                    tiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.LAVA, Blocks.LAVA);
                    continue;
                }

                // Powder snow at floor level
                if (floorBlock == Blocks.POWDER_SNOW) {
                    tiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.POWDER_SNOW, Blocks.POWDER_SNOW);
                    continue;
                }

                // Air at floor level - check depth to distinguish shallow pit from void
                if (floorState.isAir() || floorBlock == Blocks.VOID_AIR) {
                    BlockPos belowPos = new BlockPos(floorX + x, floorY - 1, floorZ + z);
                    net.minecraft.block.BlockState belowState = world.getBlockState(belowPos);
                    if (!belowState.isAir() && belowState.getFluidState().isEmpty()) {
                        // Solid block 1 below = shallow pit (LOW_GROUND)
                        tiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.LOW_GROUND, belowState.getBlock());
                    } else {
                        tiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.VOID, Blocks.AIR);
                    }
                    continue;
                }

                // Fences, walls, panes, iron bars, fence gates, and cactus all
                // have non-full collision shapes so isSolidBlock returns false,
                // yet they hard-block movement. isArenaObstacle catches them all
                // (see WallBlocks#isArenaObstacle) so pathfinding routes around them.
                boolean hasObstacleBlock =
                    com.crackedgames.craftics.combat.WallBlocks.isArenaObstacle(aboveState, world, abovePos);
                boolean hasHeadBlock =
                    com.crackedgames.craftics.combat.WallBlocks.isArenaObstacle(headState, world, headPos);

                if (hasObstacleBlock) {
                    tiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.OBSTACLE,
                        aboveState.getBlock(), hasHeadBlock);
                } else if (hasHeadBlock) {
                    tiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.OBSTACLE,
                        headState.getBlock(), true);
                } else {
                    tiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.NORMAL, floorBlock);
                }
            }
        }

        // Snap player start to nearest walkable tile, restricted to the polygon
        // mask when one was persisted at pre-gen (null = rectangular arena).
        GridPos finalPlayerStart = findNearestWalkableTile(tiles, playerStart, insideMask);

        // Wall off the surrounding world so outside mobs can't wander into a revisited arena.
        sealArenaPerimeter(world, floorX, floorY, floorZ, gridW, gridH);

        CrafticsMod.LOGGER.info("Scanned pre-built arena. origin={}, size={}x{}, playerStart={}, polygon={}",
            gridOrigin, gridW, gridH, finalPlayerStart, insideMask != null);
        return new GridArena(gridW, gridH, tiles, gridOrigin, level, finalPlayerStart, insideMask);
    }

    public static GridArena buildAt(ServerWorld world, LevelDefinition levelDef, BlockPos origin) {
        int level = levelDef.getLevelNumber();
        int w = levelDef.getWidth();
        int h = levelDef.getHeight();
        GridTile[][] tiles = levelDef.buildTiles();

        CrafticsMod.LOGGER.info("Building arena for Level {} '{}' at {} ({}x{})",
            level, levelDef.getName(), origin, w, h);

        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        Random rng = new Random(System.nanoTime() ^ (level * 31L + ox + oz * 17L));

        // Resolve biome for schematic lookup + environment theming
        String biomeId = "plains";
        boolean isBoss = false;
        int biomeLevelIndex = 0;
        com.crackedgames.craftics.api.EnvironmentDef env =
            com.crackedgames.craftics.api.registry.EnvironmentRegistry.get("plains");
        if (levelDef instanceof GeneratedLevelDefinition gld) {
            BiomeTemplate bt = gld.getBiomeTemplate();
            if (bt != null) {
                biomeId = bt.biomeId;
                isBoss = bt.isBossLevel(gld.getLevelNumber());
                biomeLevelIndex = bt.getBiomeLevelIndex(gld.getLevelNumber());
                if (bt.environmentId != null) {
                    env = com.crackedgames.craftics.api.registry.EnvironmentRegistry.get(bt.environmentId);
                }
            }
        }

        String arenaBiomeOverride = levelDef.getArenaBiomeId();
        if (arenaBiomeOverride != null && !arenaBiomeOverride.isBlank()) {
            biomeId = arenaBiomeOverride;
            isBoss = false;
        }

        // Trial chamber events need their own schematic folder. The Ominous
        // Trial Chamber gets a separate folder so it can use the dedicated
        // "trial_ominous" schematic instead of rolling from the regular trial
        // chamber pool - different geometry / vibe for the warden fight.
        String levelName = levelDef.getName();
        if (levelName != null && levelName.toLowerCase(java.util.Locale.ROOT).contains("trial chamber")) {
            biomeId = levelName.toLowerCase(java.util.Locale.ROOT).contains("ominous")
                ? "trial_chamber_ominous"
                : "trial_chamber";
            isBoss = false;
            // Trial chambers don't extend GeneratedLevelDefinition so the
            // biomeLevelIndex above stayed at its default 0 - that means the
            // `chosen = candidates.get(biomeLevelIndex % candidates.size())`
            // pick at the bottom of tryLoadStructure always landed on 1.schem.
            // Roll a fresh random index here so all of 1/2/3.schem (or any
            // future additions) get picked from across runs.
            biomeLevelIndex = rng.nextInt(1000);
        }

        // Reset per-build state
        structureOrigin = null;
        structurePlayerStart = null;
        structureGridW = -1;
        structureGridH = -1;
        structureHeight = -1;
        structureInsideMask = null;
        structureOuterMask = null;

        CrafticsMod.LOGGER.info("ArenaBuilder: resolved biomeId='{}' isBoss={} env={} levelDef={} (instanceof GLD: {})",
            biomeId, isBoss, env.id(), levelDef.getClass().getSimpleName(),
            levelDef instanceof GeneratedLevelDefinition);

        // Try structure preset, fall back to procedural
        boolean structureLoaded = tryLoadStructure(world, ox, oy, oz, w, h, tiles, biomeId, isBoss, biomeLevelIndex, rng);

        if (!structureLoaded) {
            buildProceduralFallback(world, ox, oy, oz, w, h, tiles, rng);
        }

        // Prefer structure-detected origin/spawn/size over level definition defaults
        BlockPos finalOrigin = structureOrigin != null ? structureOrigin : origin;
        GridPos requestedPlayerStart = structurePlayerStart != null ? structurePlayerStart : levelDef.getPlayerStart();
        int finalW = structureGridW > 0 ? structureGridW : w;
        int finalH = structureGridH > 0 ? structureGridH : h;

        // Resize tile array if structure grid differs from level definition
        GridTile[][] finalTiles = tiles;
        if (finalW != w || finalH != h) {
            finalTiles = new GridTile[finalW][finalH];
            for (int x = 0; x < finalW; x++) {
                for (int z = 0; z < finalH; z++) {
                    if (x < w && z < h) {
                        finalTiles[x][z] = tiles[x][z];
                    } else {
                        finalTiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.NORMAL, tiles[0][0].getBlockType());
                    }
                }
            }
        }

        int floorX = finalOrigin.getX(), floorY = finalOrigin.getY(), floorZ = finalOrigin.getZ();

        // Clear stray blocks above the arena (Y+2 and higher).
        // Y+1 (chest-level obstacles, decorations) is intentionally preserved.
        // Cobwebs at any Y level are also preserved - many schematics hang
        // cobwebs from the ceiling at floor+2, and wiping them would silently
        // strip the trap (the player would see no web yet still get caught
        // by the floor+1 overlay, or vice versa - visually confusing either
        // way). The cobweb scan below picks them up regardless of height.
        int clearCeiling = floorY + Math.max(15, structureHeight > 0 ? structureHeight + 3 : 15);
        for (int x = 0; x < finalW; x++) {
            for (int z = 0; z < finalH; z++) {
                // Polygon arenas: only sweep above the outline's own columns -
                // the bounding box reaches over terrain outside the drawn shape,
                // and that terrain is the author's to keep.
                if (structureOuterMask != null
                    && x < structureOuterMask.length && z < structureOuterMask[0].length
                    && !structureOuterMask[x][z]) {
                    continue;
                }
                for (int y = floorY + 2; y <= clearCeiling; y++) {
                    BlockPos bp = new BlockPos(floorX + x, y, floorZ + z);
                    net.minecraft.block.BlockState bs = world.getBlockState(bp);
                    if (!bs.isAir() && !bs.isOf(Blocks.COBWEB)) {
                        world.setBlockState(bp, Blocks.AIR.getDefaultState(), SET_FLAGS);
                    }
                }
            }
        }

        // Biome-themed random obstacles on the arena floor. Skipped for
        // trial chambers - they're dev-designed schematics where any extra
        // obstacles would clash with the hand-built layout and clutter the
        // visible footprint. Skipped for polygon (corner-marker) arenas for the
        // same reason, plus a practical one: the placers pick tiles across the
        // whole bounding box with no idea of the mask, which scattered random
        // boulders and hazards outside the drawn outline.
        if (!biomeId.startsWith("trial_chamber") && structureInsideMask == null) {
            placeBiomeObstacles(world, floorX, floorY, floorZ, finalW, finalH, env, rng);
        }

        //? if >=1.21.5 {
        /*// Spring to Life decorations: river gets a firefly bush on a grass border tile
        if ("river".equals(env.id()) && structureInsideMask == null) {
            placeRiverFireflyBush(world, floorX, floorY, floorZ, finalW, finalH, rng);
        }
        *///?}

        // Biome-themed light posts around the border. Skipped for trial
        // chambers - the dev's schematic already provides any lighting it
        // wants, and the procedural posts were the choppy "fences just
        // outside the arena" you could see in the screenshot.
        if (!biomeId.startsWith("trial_chamber")) {
            placeLighting(world, floorX, floorY, floorZ, finalW, finalH, env, structureInsideMask);
        }

        // FORCE_STATE can leave stale light - recheck arena bounds
        int relightTop = floorY + Math.max(12, structureHeight > 0 ? structureHeight + 3 : 12);
        relightArena(world, floorX, floorY - 3, floorZ, finalW, finalH, relightTop);

        // Chunk bounds for biome painting and client resend (not force-loaded - CombatManager handles that)
        int minCX = (finalOrigin.getX() - 2) >> 4;
        int maxCX = (finalOrigin.getX() + finalW + 2) >> 4;
        int minCZ = (finalOrigin.getZ() - 2) >> 4;
        int maxCZ = (finalOrigin.getZ() + finalH + 2) >> 4;

        // Paint MC biome on arena chunks so fog/grass/sky match the theme
        setBiomeForArena(world, finalOrigin, finalW, finalH, biomeId);

        // Resend chunks so client gets all blocks at once
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                var chunk = world.getChunk(cx, cz);
                if (chunk != null) {
                    for (var sectionPlayer : world.getPlayers()) {
                        ((net.minecraft.server.network.ServerPlayerEntity) sectionPlayer)
                            .networkHandler.chunkDataSender.add(chunk);
                    }
                }
            }
        }

        // Auto-detect obstacles/voids/cobwebs from placed schematic terrain
        List<GridPos> cobwebPositions = new ArrayList<>();
        for (int x = 0; x < finalW; x++) {
            for (int z = 0; z < finalH; z++) {
                GridTile tile = finalTiles[x][z];
                if (tile == null) continue;
                // Out-of-polygon tiles are out of bounds (GridArena returns null
                // for them) - don't classify them from the natural terrain out
                // there, and don't let capLavaDepth mutate blocks outside the
                // drawn shape.
                if (structureInsideMask != null
                    && x < structureInsideMask.length && z < structureInsideMask[0].length
                    && !structureInsideMask[x][z]) {
                    continue;
                }
                BlockPos floorPos = new BlockPos(floorX + x, floorY, floorZ + z);
                BlockPos abovePos = new BlockPos(floorX + x, floorY + 1, floorZ + z);
                BlockPos headPos = new BlockPos(floorX + x, floorY + 2, floorZ + z);
                net.minecraft.block.BlockState aboveState = world.getBlockState(abovePos);
                net.minecraft.block.BlockState headState = world.getBlockState(headPos);

                // Cobwebs at Y+1 OR Y+2: walkable but trap - register as web
                // overlay. Schematics frequently hang webs from the ceiling
                // (Y+2) instead of placing them at body level (Y+1), so we
                // check both. The overlay is keyed by (x,z) so the trap
                // catches the player regardless of which Y the web sits at.
                boolean webAtBody = aboveState.isOf(Blocks.COBWEB);
                boolean webAtHead = headState.isOf(Blocks.COBWEB);
                if (tile.isWalkable() && (webAtBody || webAtHead)) {
                    cobwebPositions.add(new GridPos(x, z));
                    // Skip normal obstacle detection for this tile - cobwebs aren't solid
                    continue;
                }

                // Tall grass / large fern at Y+1: walkable stealth tile. The upper half
                // sits at Y+2 (headPos) - non-solid so it won't be flagged as obstacle.
                if (tile.isWalkable() && aboveState.isOf(Blocks.TALL_GRASS)) {
                    finalTiles[x][z] = new GridTile(
                        com.crackedgames.craftics.core.TileType.TALL_GRASS, Blocks.TALL_GRASS);
                    continue;
                }
                if (tile.isWalkable() && aboveState.isOf(Blocks.LARGE_FERN)) {
                    finalTiles[x][z] = new GridTile(
                        com.crackedgames.craftics.core.TileType.TALL_FERN, Blocks.LARGE_FERN);
                    continue;
                }

                // Stair at floor+1 → STAIR tile (half-step, walkable). Caught
                // before the obstacle check below so a stair block doesn't get
                // misclassified as a wall. A second pass after this loop will
                // upgrade neighboring full-block tiles to ELEVATED if they sit
                // next to a stair, making the stair → upper-floor ramp work.
                if (tile.isWalkable() && aboveState.getBlock() instanceof net.minecraft.block.StairsBlock) {
                    finalTiles[x][z] = new GridTile(
                        com.crackedgames.craftics.core.TileType.STAIR, aboveState.getBlock());
                    continue;
                }

                // Slabs also act as half-step ramps. A BOTTOM slab at floor+1
                // gives the same +0.5 step as a stair; a TOP slab sits at
                // +1.5..+2.0 so walking on it is a full Y+1 step (ELEVATED).
                // DOUBLE slabs are a full block - fall through to the obstacle
                // / ELEVATED-promotion path.
                if (tile.isWalkable() && aboveState.getBlock() instanceof net.minecraft.block.SlabBlock) {
                    net.minecraft.block.enums.SlabType slabType =
                        aboveState.get(net.minecraft.block.SlabBlock.TYPE);
                    if (slabType == net.minecraft.block.enums.SlabType.BOTTOM) {
                        finalTiles[x][z] = new GridTile(
                            com.crackedgames.craftics.core.TileType.STAIR, aboveState.getBlock());
                        continue;
                    } else if (slabType == net.minecraft.block.enums.SlabType.TOP) {
                        finalTiles[x][z] = new GridTile(
                            com.crackedgames.craftics.core.TileType.ELEVATED, aboveState.getBlock());
                        continue;
                    }
                }

                // Fences, walls, panes, iron bars, fence gates, and cactus all
                // have non-full collision shapes so isSolidBlock returns false,
                // yet they hard-block movement. isArenaObstacle catches them all
                // (see WallBlocks#isArenaObstacle) so pathfinding routes around them.
                boolean hasObstacleBlock =
                    com.crackedgames.craftics.combat.WallBlocks.isArenaObstacle(aboveState, world, abovePos);
                boolean hasHeadBlock =
                    com.crackedgames.craftics.combat.WallBlocks.isArenaObstacle(headState, world, headPos);

                // Solid block above floor = obstacle; block at head level too = permanent (can't mine)
                if (tile.isWalkable() && hasObstacleBlock) {
                    finalTiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.OBSTACLE,
                        aboveState.getBlock(), hasHeadBlock);
                }

                // Head-level block with no chest-level block = suffocation, mark permanent
                if (finalTiles[x][z].isWalkable() && !hasObstacleBlock && hasHeadBlock) {
                    finalTiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.OBSTACLE,
                        headState.getBlock(), true);
                }

                // Water at floor level - check depth
                net.minecraft.block.BlockState floorState = world.getBlockState(floorPos);
                if (tile.isWalkable() && !floorState.getFluidState().isEmpty()
                    && floorState.getBlock() == Blocks.WATER) {
                    BlockPos belowWater = new BlockPos(floorX + x, floorY - 1, floorZ + z);
                    boolean isDeep = !world.getBlockState(belowWater).getFluidState().isEmpty();
                    finalTiles[x][z] = new GridTile(isDeep
                        ? com.crackedgames.craftics.core.TileType.DEEP_WATER
                        : com.crackedgames.craftics.core.TileType.WATER, Blocks.WATER);
                }

                // Powder snow at floor level
                if (finalTiles[x][z].isWalkable() && floorState.getBlock() == Blocks.POWDER_SNOW) {
                    finalTiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.POWDER_SNOW,
                        Blocks.POWDER_SNOW);
                }

                // Lava or magma at floor level = LAVA tile. Cap depth so the
                // tile is a fair single-block hazard (see capLavaDepth).
                if (finalTiles[x][z].isWalkable()
                    && (floorState.getBlock() == Blocks.LAVA || floorState.getBlock() == Blocks.MAGMA_BLOCK)) {
                    capLavaDepth(world, floorX + x, floorY, floorZ + z);
                    finalTiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.LAVA,
                        Blocks.LAVA);
                }

                // Air at floor level - check depth for shallow pit vs void
                if (finalTiles[x][z].isWalkable()) {
                    Block floorBlock = floorState.getBlock();
                    if (floorState.isAir() || floorBlock == Blocks.VOID_AIR) {
                        BlockPos belowPos = new BlockPos(floorX + x, floorY - 1, floorZ + z);
                        net.minecraft.block.BlockState belowState = world.getBlockState(belowPos);
                        if (!belowState.isAir() && belowState.getFluidState().isEmpty()) {
                            finalTiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.LOW_GROUND,
                                belowState.getBlock());
                        } else {
                            finalTiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.VOID,
                                Blocks.AIR);
                        }
                    }
                }
            }
        }

        // Second pass: promote OBSTACLE → ELEVATED. A stair provides a Y+0.5
        // ramp; the full block at floor+1 that the stair butts up against is
        // the upper-floor landing (Y+1). Then ELEVATED propagates outward
        // through 4-connected OBSTACLE neighbors so an entire raised platform
        // becomes walkable, not just the single tile touching the stair.
        // Walls with no stair access stay as OBSTACLE.
        int[][] stairAdjDirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int x = 0; x < finalW; x++) {
                for (int z = 0; z < finalH; z++) {
                    GridTile tile = finalTiles[x][z];
                    if (tile == null || tile.getType() != com.crackedgames.craftics.core.TileType.OBSTACLE) continue;
                    // Only promote if not a head-level (permanent) obstacle -
                    // those are walls the player can't reasonably stand on top of.
                    if (tile.isPermanent()) continue;
                    boolean adjacentToLanding = false;
                    for (int[] d : stairAdjDirs) {
                        int nx = x + d[0], nz = z + d[1];
                        if (nx < 0 || nx >= finalW || nz < 0 || nz >= finalH) continue;
                        GridTile neighbor = finalTiles[nx][nz];
                        if (neighbor == null) continue;
                        com.crackedgames.craftics.core.TileType nt = neighbor.getType();
                        if (nt == com.crackedgames.craftics.core.TileType.STAIR
                                || nt == com.crackedgames.craftics.core.TileType.ELEVATED) {
                            adjacentToLanding = true;
                            break;
                        }
                    }
                    if (adjacentToLanding) {
                        finalTiles[x][z] = new GridTile(
                            com.crackedgames.craftics.core.TileType.ELEVATED, tile.getBlockType());
                        changed = true;
                    }
                }
            }
        }

        // Snap player start to nearest walkable tile after obstacle scan
        GridPos finalPlayerStart = findNearestWalkableTile(finalTiles, requestedPlayerStart,
            structureInsideMask);

        CrafticsMod.LOGGER.info("Arena built. origin={}, size={}x{}, playerStart={}, polygon={}",
            finalOrigin, finalW, finalH, finalPlayerStart, structureInsideMask != null);
        // Hand the polygon mask (if any) to GridArena. Null = legacy rectangle.
        GridArena arena = new GridArena(finalW, finalH, finalTiles, finalOrigin, level,
            finalPlayerStart, structureInsideMask);

        // Wall off the surrounding world so outside mobs can't wander into the arena. One
        // ring out from the playable grid, walkable gaps only - existing scenery walls are
        // left intact. Covers every buildAt sub-path (rectangle, polygon, procedural).
        sealArenaPerimeter(world, finalOrigin.getX(), finalOrigin.getY(), finalOrigin.getZ(),
            finalW, finalH);

        // Register static cobwebs as permanent web overlays - schematic + jungle
        // decoration cobwebs only go away when a player walks through them, so
        // they must skip the turn-decrement that timed spider/broodmother webs use.
        for (GridPos webPos : cobwebPositions) {
            arena.setWebOverlay(webPos, GridArena.PERMANENT_WEB);
        }

        return arena;
    }

    // Tries .schem on disk first, then bundled .schem in JAR, then .nbt via StructureTemplateManager
    private static boolean tryLoadStructure(ServerWorld world, int ox, int oy, int oz,
                                              int w, int h, GridTile[][] tiles,
                                              String biomeId, boolean isBoss,
                                              int biomeLevelIndex, Random rng) {
        // 1. Disk .schem files (filesystem overrides take priority)
        List<java.nio.file.Path> diskSchemCandidates = new ArrayList<>();
        try {
            var server = world.getServer();
            java.nio.file.Path datapacksDir = server.getSavePath(net.minecraft.util.WorldSavePath.DATAPACKS);
            searchSchemFiles(datapacksDir, biomeId, isBoss, diskSchemCandidates);

            java.nio.file.Path currentDir = java.nio.file.Path.of("").toAbsolutePath();
            List<java.nio.file.Path> searchRoots = new ArrayList<>();
            searchRoots.add(currentDir.resolve("craftics_arenas"));
            searchRoots.add(currentDir.resolve("run/config/worldedit/schematics"));
            searchRoots.add(currentDir.resolve("config/worldedit/schematics"));
            if (currentDir.getParent() != null) {
                searchRoots.add(currentDir.getParent().resolve("craftics_arenas"));
                searchRoots.add(currentDir.getParent().resolve("run/config/worldedit/schematics"));
                searchRoots.add(currentDir.getParent().resolve("config/worldedit/schematics"));
                // Stonecutter layout: run/ is under versions/<ver>/run/, repo root is 3 levels up
                if (currentDir.getParent().getParent() != null) {
                    searchRoots.add(currentDir.getParent().getParent().resolve("craftics_arenas"));
                    if (currentDir.getParent().getParent().getParent() != null) {
                        searchRoots.add(currentDir.getParent().getParent().getParent().resolve("craftics_arenas"));
                    }
                }
            }

            for (java.nio.file.Path root : searchRoots) {
                CrafticsMod.LOGGER.debug("Searching for .schem arenas in: {}", root);
                searchSchemFiles(root, biomeId, isBoss, diskSchemCandidates);
            }
        } catch (Exception e) {
            CrafticsMod.LOGGER.debug("Error while searching for .schem arenas: {}", e.getMessage());
        }

        // Disk overrides win - distribute by biome level index to avoid repeats.
        // Trial chambers are also "preserve schematic ground" like bosses: the
        // schematics are hand-built per-room and we don't want the level def's
        // procedural overlay (random copper obstacles, tuff floor replacement)
        // painting over the dev's design.
        if (!diskSchemCandidates.isEmpty()) {
            CrafticsMod.LOGGER.info("Found {} disk .schem candidate(s) for biome '{}': {}",
                diskSchemCandidates.size(), biomeId, diskSchemCandidates);
            java.nio.file.Path chosenSchem = diskSchemCandidates.get(biomeLevelIndex % diskSchemCandidates.size());
            boolean preserveGround = isBoss || biomeId.startsWith("trial_chamber");
            return loadAndPlaceSchem(world, chosenSchem, ox, oy, oz, w, h, tiles, biomeId, preserveGround);
        }

        // 2. Bundled .schem in JAR: data/craftics/arenas/<biome>/<name>.schem
        List<Identifier> bundledCandidates = new ArrayList<>();
        net.minecraft.resource.ResourceManager resourceManager = world.getServer().getResourceManager();

        // Sub-biome ids like "forest/pale_garden" map to a single bundled FILE
        // (arenas/forest/pale_garden.schem), not a directory. The numbered/boss probes
        // below treat the id as a directory (arenas/forest/pale_garden/<i>.schem) and
        // would always miss, dropping the dedicated arena to the procedural fallback in
        // packaged installs. Resolve the literal file here, mirroring the disk-path
        // handling in searchSchemFiles.
        if (biomeId.contains("/")) {
            Identifier subId = Identifier.of("craftics", "arenas/" + biomeId + ".schem");
            if (resourceManager.getResource(subId).isPresent()) {
                boolean preserveGround = isBoss || biomeId.startsWith("trial_chamber");
                CrafticsMod.LOGGER.info("Loading bundled sub-biome arena: {} (preserveGround={})",
                    subId, preserveGround);
                return loadAndPlaceBundledSchem(world, resourceManager, subId, ox, oy, oz, w, h, tiles, biomeId, preserveGround);
            }
        }

        if (isBoss) {
            Identifier bossId = Identifier.of("craftics", "arenas/" + biomeId + "/boss.schem");
            if (resourceManager.getResource(bossId).isPresent()) {
                bundledCandidates.add(bossId);
            }
        }
        if (bundledCandidates.isEmpty()) {
            for (int i = 1; i <= 10; i++) {
                Identifier id = Identifier.of("craftics", "arenas/" + biomeId + "/" + i + ".schem");
                if (resourceManager.getResource(id).isPresent()) {
                    bundledCandidates.add(id);
                } else {
                    break;
                }
            }
        }

        if (!bundledCandidates.isEmpty()) {
            // Distribute by biome level index so each level in a biome uses a different variant.
            // Trial chambers preserve schematic ground (same as bosses) so the
            // dev-designed layout isn't overwritten by the procedural tile overlay.
            Identifier chosen = bundledCandidates.get(biomeLevelIndex % bundledCandidates.size());
            boolean preserveGround = isBoss || biomeId.startsWith("trial_chamber");
            CrafticsMod.LOGGER.info("Loading bundled arena: {} ({} candidates, biomeLevelIndex={}, preserveGround={})",
                chosen, bundledCandidates.size(), biomeLevelIndex, preserveGround);
            return loadAndPlaceBundledSchem(world, resourceManager, chosen, ox, oy, oz, w, h, tiles, biomeId, preserveGround);
        }

        // 3. .nbt structures via StructureTemplateManager
        var manager = world.getStructureTemplateManager();
        List<Identifier> nbtCandidates = new ArrayList<>();
        String prefix = isBoss ? "boss_" : "";

        for (int i = 1; i <= 10; i++) {
            Identifier id = Identifier.of("craftics", "arenas/" + biomeId + "/" + prefix + i);
            var template = manager.getTemplate(id);
            if (template.isPresent()) {
                nbtCandidates.add(id);
            } else {
                break;
            }
        }

        if (nbtCandidates.isEmpty() && isBoss) {
            for (int i = 1; i <= 10; i++) {
                Identifier id = Identifier.of("craftics", "arenas/" + biomeId + "/" + i);
                var template = manager.getTemplate(id);
                if (template.isPresent()) {
                    nbtCandidates.add(id);
                } else {
                    break;
                }
            }
        }

        if (nbtCandidates.isEmpty()) {
            CrafticsMod.LOGGER.info("No structure presets found for biome '{}' (boss={}), using procedural",
                biomeId, isBoss);
            return false;
        }

        List<Identifier> candidates = nbtCandidates;
        Identifier chosen = candidates.get(biomeLevelIndex % candidates.size());
        StructureTemplate template = manager.getTemplate(chosen).orElse(null);
        if (template == null) return false;

        CrafticsMod.LOGGER.info("Loading arena structure: {} ({}x{}x{})",
            chosen, template.getSize().getX(), template.getSize().getY(), template.getSize().getZ());

        var size = template.getSize();
        int clearPad = 3;
        for (int x = -clearPad; x < size.getX() + clearPad; x++) {
            for (int z = -clearPad; z < size.getZ() + clearPad; z++) {
                for (int y = -3; y <= size.getY() + 3; y++) {
                    BlockPos bp = new BlockPos(ox + x - size.getX() / 2 + w / 2, oy + y, oz + z - size.getZ() / 2 + h / 2);
                    if (!world.getBlockState(bp).isAir()) {
                        world.setBlockState(bp, Blocks.AIR.getDefaultState(), SET_FLAGS);
                    }
                }
            }
        }

        // Center structure over the arena
        int placeX = ox + w / 2 - size.getX() / 2;
        int placeY = oy;
        int placeZ = oz + h / 2 - size.getZ() / 2;
        BlockPos placePos = new BlockPos(placeX, placeY, placeZ);

        StructurePlacementData placementData = new StructurePlacementData();
        template.place(world, placePos, placePos, placementData,
            net.minecraft.util.math.random.Random.create(rng.nextLong()), SET_FLAGS);

        return processPlacedStructure(world, placeX, placeY, placeZ,
            size.getX(), size.getY(), size.getZ(), ox, oy, oz, w, h, tiles, chosen.toString(), biomeId, isBoss);
    }

    // Shared marker processing - diamond+emerald corners define the inner playable grid
        private static boolean processPlacedStructure(ServerWorld world,
            int placeX, int placeY, int placeZ, int sizeX, int sizeY, int sizeZ,
            int ox, int oy, int oz, int w, int h, GridTile[][] tiles, String sourceName,
                String biomeId,
            boolean preserveSchematicGround) {

        BlockPos diamondPos = null;
        BlockPos emeraldPos = null;
        // Collect spawn marker candidates, filter to grid bounds later
        List<BlockPos> goldCandidates = new ArrayList<>();
        List<BlockPos> ironCandidates = new ArrayList<>();
        List<BlockPos> copperCandidates = new ArrayList<>();
        List<BlockPos> coalCandidates = new ArrayList<>();
        // Polygon corner markers: 3+ ArenaCornerBlocks define a non-rectangular
        // arena outline. The mod's ARENA_CORNER_BLOCK is the only marker
        // checked here - vanilla blocks stay rectangle-only so existing
        // schematics aren't affected.
        List<BlockPos> polygonCorners = new ArrayList<>();

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockPos worldPos = new BlockPos(placeX + x, placeY + y, placeZ + z);
                    BlockState state = world.getBlockState(worldPos);
                    if (state.isOf(Blocks.DIAMOND_BLOCK)) diamondPos = worldPos;
                    else if (state.isOf(Blocks.EMERALD_BLOCK)) emeraldPos = worldPos;
                    else if (state.isOf(Blocks.GOLD_BLOCK)) goldCandidates.add(worldPos);
                    else if (state.isOf(Blocks.IRON_BLOCK)) ironCandidates.add(worldPos);
                    else if (state.isOf(Blocks.COPPER_BLOCK)) copperCandidates.add(worldPos);
                    else if (state.isOf(Blocks.COAL_BLOCK)) coalCandidates.add(worldPos);
                    else if (state.isOf(com.crackedgames.craftics.block.ModBlocks.ARENA_CORNER_BLOCK)) {
                        polygonCorners.add(worldPos);
                    }
                }
            }
        }

        boolean hasGold = !goldCandidates.isEmpty();
        boolean hasIron = !ironCandidates.isEmpty();
        boolean hasCopper = !copperCandidates.isEmpty();
        boolean hasCoal = !coalCandidates.isEmpty();
        CrafticsMod.LOGGER.info("Structure {} marker scan: diamond={}, emerald={}, corners={}, spawns=[{},{},{},{}]",
            sourceName, diamondPos != null, emeraldPos != null, polygonCorners.size(),
            hasGold, hasIron, hasCopper, hasCoal);

        // POLYGON PATH: 3+ ArenaCornerBlocks present → derive bounding box from
        // those corners (ignore DIAMOND/EMERALD), build a polygon mask, fall
        // through to the existing overlay logic so spawn-marker scanning and
        // border placement reuse the same code as the rectangle path. The
        // polygon mask is stored in structureInsideMask and handed to GridArena
        // at construction.
        // Polygon path: 3+ corner markers, OR 2+ corners plus a DIAMOND that
        // completes the outline as the camera-corner vertex. (Rectangular arenas use
        // DIAMOND+EMERALD with zero corner blocks, so they never enter here.)
        if (polygonCorners.size() >= 3 || (polygonCorners.size() >= 2 && diamondPos != null)) {
            return processPolygonStructure(world, placeX, placeY, placeZ, sizeX, sizeY, sizeZ,
                tiles, sourceName, biomeId, preserveSchematicGround,
                polygonCorners, diamondPos, goldCandidates, ironCandidates, copperCandidates, coalCandidates);
        }

        // RECTANGLE-BY-CORNERS: exactly two arena_corner markers with no
        // DIAMOND act as the DIAMOND+EMERALD pair - opposite inside corner
        // tiles of a rectangular arena. The first corner in scan order
        // (lowest X wins) takes the DIAMOND role, so it is also the camera
        // corner. A stray EMERALD without a DIAMOND never formed a valid
        // rectangle, so the corner pair overrides it here. 2 corners PLUS a
        // DIAMOND stay a polygon via the gate above.
        if (polygonCorners.size() == 2 && diamondPos == null) {
            diamondPos = polygonCorners.get(0);
            emeraldPos = polygonCorners.get(1);
            CrafticsMod.LOGGER.info("Structure {}: 2 arena_corner markers, no DIAMOND - treating corners as the rectangle pair.", sourceName);
        }

        if (diamondPos == null || emeraldPos == null) {
            CrafticsMod.LOGGER.warn("Structure {} missing DIAMOND/EMERALD corner markers. Falling back to default overlay.", sourceName);
            int scannedY = scanSchematicFloorY(world, placeX, placeY, placeZ, sizeX, sizeY, sizeZ, ox, oz, w, h);
            overlayArenaTiles(world, ox, scannedY, oz, w, h, tiles);
            return true;
        }

        // Tolerate small Y mismatches between the two corner markers - e.g. one
        // accidentally placed on a recessed step or sunken corner. Use the
        // HIGHER of the two as the arena floor: the floor is whatever the
        // player actually stands on, and any stray marker that slipped a
        // block down into the foundation should not drag the playfield below
        // the intended surface.
        int diamondY = diamondPos.getY();
        int emeraldY = emeraldPos.getY();
        int yDiff = Math.abs(diamondY - emeraldY);
        if (yDiff > 3) {
            CrafticsMod.LOGGER.warn("Structure {} has corner markers more than 3 blocks apart on Y: diamond={}, emerald={}. Falling back to default overlay.",
                sourceName, diamondY, emeraldY);
            int scannedY = scanSchematicFloorY(world, placeX, placeY, placeZ, sizeX, sizeY, sizeZ, ox, oz, w, h);
            overlayArenaTiles(world, ox, scannedY, oz, w, h, tiles);
            return true;
        }
        if (yDiff > 0) {
            CrafticsMod.LOGGER.info("Structure {} has corner markers {} block(s) apart on Y (diamond={}, emerald={}); using higher Y as arena floor.",
                sourceName, yDiff, diamondY, emeraldY);
        }

        int arenaFloorY = Math.max(diamondY, emeraldY);

        // DIAMOND/EMERALD now mark the INSIDE corners of the playable area -
        // i.e. their positions are tiles the player can stand on. Previously
        // they sat one tile OUTSIDE the playable grid (border row) which
        // chopped 2 tiles off each axis and produced the "boundaries cut
        // short too harshly" symptom. The border concrete ring below still
        // sits one tile further out so the markers double as the corner
        // tiles you actually fight in.
        int gridMinX = Math.min(diamondPos.getX(), emeraldPos.getX());
        int gridMinZ = Math.min(diamondPos.getZ(), emeraldPos.getZ());
        int gridMaxX = Math.max(diamondPos.getX(), emeraldPos.getX());
        int gridMaxZ = Math.max(diamondPos.getZ(), emeraldPos.getZ());
        int borderMinX = gridMinX - 1;
        int borderMinZ = gridMinZ - 1;
        int borderMaxX = gridMaxX + 1;
        int borderMaxZ = gridMaxZ + 1;
        int gridW = gridMaxX - gridMinX + 1;
        int gridH = gridMaxZ - gridMinZ + 1;
        structureHeight = sizeY;

        if (gridW <= 0 || gridH <= 0) {
            CrafticsMod.LOGGER.warn("Structure {} produced invalid inner arena size {}x{}. Falling back to default overlay.", sourceName, gridW, gridH);
            overlayArenaTiles(world, ox, oy, oz, w, h, tiles);
            return true;
        }

        // Only accept spawn markers at arena floor Y within grid bounds
        @SuppressWarnings("unchecked")
        List<BlockPos>[] candidateLists = new List[]{goldCandidates, ironCandidates, copperCandidates, coalCandidates};
        BlockPos[] playerSpawns = new BlockPos[4];
        for (int i = 0; i < 4; i++) {
            for (BlockPos bp : candidateLists[i]) {
                if (bp.getY() == arenaFloorY
                    && bp.getX() >= gridMinX && bp.getX() <= gridMaxX
                    && bp.getZ() >= gridMinZ && bp.getZ() <= gridMaxZ) {
                    playerSpawns[i] = bp;
                    break;
                }
            }
        }

        double centerX = (gridMinX + gridMaxX) / 2.0;
        double centerZ = (gridMinZ + gridMaxZ) / 2.0;
        pendingCameraYaw = yawFromTo(diamondPos.getX(), diamondPos.getZ(), centerX, centerZ);

        Block floorBlock = tiles[0][0].getBlockType();
        Block borderConcrete = getBorderConcreteForBiome(biomeId, tiles);

        // Replace corner markers with surrounding block so they blend in
        Block diamondReplacement = getMostCommonTouchingBlock(world, diamondPos, borderConcrete);
        Block emeraldReplacement = getMostCommonTouchingBlock(world, emeraldPos, borderConcrete);
        world.setBlockState(diamondPos, diamondReplacement.getDefaultState(), SET_FLAGS);
        world.setBlockState(emeraldPos, emeraldReplacement.getDefaultState(), SET_FLAGS);

        if (!preserveSchematicGround) {
            // Border ring in biome-themed concrete
            for (int x = borderMinX; x <= borderMaxX; x++) {
                BlockPos north = new BlockPos(x, arenaFloorY, borderMinZ);
                BlockPos south = new BlockPos(x, arenaFloorY, borderMaxZ);
                if (!isAirLike(world.getBlockState(north))) set(world, x, arenaFloorY, borderMinZ, borderConcrete);
                if (!isAirLike(world.getBlockState(south))) set(world, x, arenaFloorY, borderMaxZ, borderConcrete);
            }
            for (int z = borderMinZ; z <= borderMaxZ; z++) {
                BlockPos west = new BlockPos(borderMinX, arenaFloorY, z);
                BlockPos east = new BlockPos(borderMaxX, arenaFloorY, z);
                if (!isAirLike(world.getBlockState(west))) set(world, borderMinX, arenaFloorY, z, borderConcrete);
                if (!isAirLike(world.getBlockState(east))) set(world, borderMaxX, arenaFloorY, z, borderConcrete);
            }

            // Overlay tiles onto inner grid (skip existing liquids)
            for (int x = 0; x < gridW; x++) {
                for (int z = 0; z < gridH; z++) {
                    int worldX = gridMinX + x;
                    int worldZ = gridMinZ + z;
                    BlockPos floorPos = new BlockPos(worldX, arenaFloorY, worldZ);

                    GridTile tile = (x < tiles.length && z < tiles[0].length)
                        ? tiles[x][z]
                        : new GridTile(com.crackedgames.craftics.core.TileType.NORMAL, floorBlock);

                    if (!world.getBlockState(floorPos).getFluidState().isEmpty()) {
                        continue;
                    }

                    // Air at floor Y: only preserve it when the block below is
                    // safe (air or a walkable solid). If the schematic has a
                    // hazard (lava / liquid) directly under an air gap in the
                    // playable area, treat it as an accidental hole and paint
                    // the floor block there - otherwise the post-scan would
                    // classify this tile as VOID and the player would fall
                    // through into whatever horrors live beneath the arena.
                    if (world.getBlockState(floorPos).isAir()) {
                        BlockPos belowPos = new BlockPos(worldX, arenaFloorY - 1, worldZ);
                        BlockState belowState = world.getBlockState(belowPos);
                        boolean belowIsHazard = !belowState.getFluidState().isEmpty()
                            || belowState.isOf(Blocks.LAVA)
                            || belowState.isOf(Blocks.MAGMA_BLOCK);
                        if (!belowIsHazard) {
                            continue; // genuine cliff/pit - leave it alone
                        }
                        // Patch the hole so the tile scan doesn't see it as VOID
                        set(world, worldX, arenaFloorY - 1, worldZ, Blocks.STONE);
                    }

                    world.setBlockState(floorPos,
                        tile.getBlockType().getDefaultState(), SET_FLAGS);

                    if (!tile.isWalkable() && !tile.isWater()) {
                        set(world, worldX, arenaFloorY + 1, worldZ, tile.getBlockType());
                    } else {
                        BlockPos above = new BlockPos(worldX, arenaFloorY + 1, worldZ);
                        BlockState aboveState = world.getBlockState(above);
                        if (!aboveState.isAir() && !(aboveState.getBlock() instanceof net.minecraft.block.CarpetBlock)) {
                            world.setBlockState(above, Blocks.AIR.getDefaultState(), SET_FLAGS);
                        }
                    }
                }
            }
        }

        structureOrigin = new BlockPos(gridMinX, arenaFloorY, gridMinZ);
        structureGridW = gridW;
        structureGridH = gridH;

        String[] spawnNames = {"P1 (gold)", "P2 (iron)", "P3 (copper)", "P4 (coal)"};
        for (int i = 0; i < 4; i++) {
            if (playerSpawns[i] != null) {
                world.setBlockState(playerSpawns[i], floorBlock.getDefaultState(), SET_FLAGS);
                int gridX = playerSpawns[i].getX() - gridMinX;
                int gridZ = playerSpawns[i].getZ() - gridMinZ;
                CrafticsMod.LOGGER.info("  {} spawn: grid({}, {})", spawnNames[i], gridX, gridZ);
                if (i == 0) structurePlayerStart = new GridPos(gridX, gridZ);
            }
        }

        // Auto P1 spawn if no gold marker - center, one tile in from camera side
        if (structurePlayerStart == null) {
            int spawnX = Math.max(0, Math.min(gridW - 1, gridW / 2));
            int spawnZ = diamondPos.getZ() < centerZ ? 1 : gridH - 2;
            spawnZ = Math.max(0, Math.min(gridH - 1, spawnZ));
            structurePlayerStart = new GridPos(spawnX, spawnZ);
            CrafticsMod.LOGGER.info("  Auto P1 spawn: grid({}, {})", spawnX, spawnZ);
        }

        if (!preserveSchematicGround) {
            for (int x = gridMinX; x <= gridMaxX; x++) {
                for (int z = gridMinZ; z <= gridMaxZ; z++) {
                    int gridX = x - gridMinX;
                    int gridZ = z - gridMinZ;
                    GridTile tile = (gridX < tiles.length && gridZ < tiles[0].length)
                        ? tiles[gridX][gridZ]
                        : new GridTile(com.crackedgames.craftics.core.TileType.NORMAL, floorBlock);
                    BlockPos floorPos = new BlockPos(x, arenaFloorY, z);
                    if (!world.getBlockState(floorPos).getFluidState().isEmpty()) {
                        continue;
                    }
                    if (isAirLike(world.getBlockState(floorPos))) {
                        continue;
                    }
                    set(world, x, arenaFloorY - 1, z, Blocks.STONE);
                }
            }
        }

        return true;
    }

    /**
     * Polygon-arena builder. Triggered when {@link #processPlacedStructure} sees
     * 3+ {@code ArenaCornerBlock} markers. Sorts the corners by angle around
     * their centroid to produce a sensible polygon outline (handles convex
     * shapes perfectly and most concave shapes that are drawn corner-by-corner
     * in order), then builds a per-tile in/out mask via ray-casting and hands
     * it to {@link GridArena} via {@link #structureInsideMask}. Tile overlay,
     * border placement, and spawn-marker scanning all respect the polygon -
     * tiles outside the mask are left untouched so the surrounding terrain
     * shows through whatever shape the dev outlined.
     */
    private static boolean processPolygonStructure(ServerWorld world,
            int placeX, int placeY, int placeZ, int sizeX, int sizeY, int sizeZ,
            GridTile[][] tiles, String sourceName, String biomeId,
            boolean preserveSchematicGround,
            List<BlockPos> corners, BlockPos diamondPos,
            List<BlockPos> goldCandidates, List<BlockPos> ironCandidates,
            List<BlockPos> copperCandidates, List<BlockPos> coalCandidates) {

        // A DIAMOND_BLOCK among the corner markers is the camera-corner vertex: it
        // joins the polygon outline AND aims the camera (see yaw derivation below).
        // Folding it into the corner list makes it a real vertex and gets it cleaned
        // up with the other markers, instead of being left as a stray diamond block.
        if (diamondPos != null) corners.add(diamondPos);

        // Polygon floor = the surface the player stands on. A corner marker may
        // be deliberately buried under the floor block so it doesn't show in the
        // arena - climb from each marker through any solid blocks stacked
        // directly on it (capped at 2: that's a hidden marker, a taller column
        // is a wall and shouldn't lift the floor) and use THAT as the corner's
        // floor level. Exposed markers keep their own Y, matching the
        // diamond/emerald rule. The arena floor is the most common corner
        // surface (ties prefer the higher), so one odd corner - buried deeper,
        // dropped on a step, tucked under a wall - can't shift the whole
        // playfield the way the old raw max-Y did.
        Map<Integer, Integer> surfaceVotes = new HashMap<>();
        for (BlockPos c : corners) {
            int surfaceY = c.getY();
            for (int climb = 0; climb < 2; climb++) {
                BlockPos above = new BlockPos(c.getX(), surfaceY + 1, c.getZ());
                BlockState aboveState = world.getBlockState(above);
                if (aboveState.isAir() || !aboveState.isSolidBlock(world, above)) break;
                surfaceY++;
            }
            surfaceVotes.merge(surfaceY, 1, Integer::sum);
        }
        int arenaFloorY = corners.get(0).getY();
        int bestVotes = -1;
        for (Map.Entry<Integer, Integer> vote : surfaceVotes.entrySet()) {
            if (vote.getValue() > bestVotes
                || (vote.getValue() == bestVotes && vote.getKey() > arenaFloorY)) {
                bestVotes = vote.getValue();
                arenaFloorY = vote.getKey();
            }
        }

        // Bounding box from all corners. The corner markers ARE the outer ring; the
        // playable floor is the polygon interior eroded one tile inward (below), so
        // the grid spans the full corner bbox with no inset.
        int borderMinX = Integer.MAX_VALUE, borderMaxX = Integer.MIN_VALUE;
        int borderMinZ = Integer.MAX_VALUE, borderMaxZ = Integer.MIN_VALUE;
        for (BlockPos c : corners) {
            borderMinX = Math.min(borderMinX, c.getX());
            borderMaxX = Math.max(borderMaxX, c.getX());
            borderMinZ = Math.min(borderMinZ, c.getZ());
            borderMaxZ = Math.max(borderMaxZ, c.getZ());
        }
        // The grid spans the FULL marker bbox; the playable floor is the polygon
        // interior eroded one tile inward (see mask build below), so the corner
        // markers on the outer ring always sit one tile outside the floor - convex
        // tips and concave armpits alike. (Not a bbox inset, which only trimmed the
        // outer ring and left concave-vertex markers sitting on floor tiles.)
        int gridMinX = borderMinX;
        int gridMinZ = borderMinZ;
        int gridMaxX = borderMaxX;
        int gridMaxZ = borderMaxZ;
        int gridW = gridMaxX - gridMinX + 1;
        int gridH = gridMaxZ - gridMinZ + 1;
        structureHeight = sizeY;

        if (gridW <= 0 || gridH <= 0) {
            CrafticsMod.LOGGER.warn("Polygon arena {} produced invalid size {}x{}. Falling back to default overlay.",
                sourceName, gridW, gridH);
            overlayArenaTiles(world, borderMinX, arenaFloorY, borderMinZ, gridW, gridH, tiles);
            return true;
        }

        // Order the corners into one closed outline. Rectilinear outlines (the
        // L / T / plus / U shapes authors actually draw) are reconstructed
        // EXACTLY from their edge structure; everything else falls back to a
        // centroid-angle sort, which is correct for convex rings (diamond,
        // octagon, hexagon). The angle sort alone self-intersected on concave
        // shapes - an L's concave vertex sits AT the centroid, where the angle
        // is undefined - which made the ray-cast mask mark regions outside the
        // drawn shape as playable: mobs, floor, and hover showing up "outside
        // the arena".
        List<BlockPos> sorted = orderOutline(corners);

        // Build per-tile in/out mask via ray-casting point-in-polygon. Tiles are
        // sampled at their integer block coordinate (gridMinX + tx, gridMinZ + tz)
        // and the outline is grown a hair OUTWARD from its centroid, so an
        // axis-aligned edge never falls exactly on a sample point.
        //
        // The old test sampled tile CENTERS at +0.5 against vertices also at +0.5,
        // which put every axis-aligned edge right on the sample row/column; the
        // half-open even-odd rule then resolved those boundary hits asymmetrically
        // and lopsided otherwise-symmetric shapes - one plus/cross/diamond inner
        // corner filled while its mirror was not. Integer sampling keeps the mask
        // symmetric about a centered polygon; the outward grow removes the on-edge
        // degeneracy without shifting the shape.
        int polyN = sorted.size();
        double polyCx = 0, polyCz = 0;
        for (BlockPos c : sorted) { polyCx += c.getX(); polyCz += c.getZ(); }
        polyCx /= polyN;
        polyCz /= polyN;
        final double GROW = 0.001;
        double[] vx = new double[polyN];
        double[] vz = new double[polyN];
        for (int i = 0; i < polyN; i++) {
            double x = sorted.get(i).getX();
            double z = sorted.get(i).getZ();
            vx[i] = x + (x - polyCx) * GROW;
            vz[i] = z + (z - polyCz) * GROW;
        }
        boolean[][] outer = new boolean[gridW][gridH];
        for (int tx = 0; tx < gridW; tx++) {
            for (int tz = 0; tz < gridH; tz++) {
                double px = gridMinX + tx;
                double pz = gridMinZ + tz;
                boolean inside = false;
                for (int i = 0, j = polyN - 1; i < polyN; j = i++) {
                    boolean intersects = ((vz[i] > pz) != (vz[j] > pz))
                        && (px < (vx[j] - vx[i]) * (pz - vz[i]) / (vz[j] - vz[i]) + vx[i]);
                    if (intersects) inside = !inside;
                }
                outer[tx][tz] = inside;
            }
        }
        // Erode one tile inward (4-neighbour): a tile is playable floor only if it
        // and all four orthogonal neighbours are inside the outer polygon (grid-edge
        // neighbours count as outside). This recedes the floor uniformly so every
        // corner marker on the outer ring sits one tile outside the playable area.
        boolean[][] mask = new boolean[gridW][gridH];
        int insideCount = 0;
        for (int tx = 0; tx < gridW; tx++) {
            for (int tz = 0; tz < gridH; tz++) {
                if (!outer[tx][tz]) continue;
                boolean keep = tx > 0 && tx < gridW - 1 && tz > 0 && tz < gridH - 1
                    && outer[tx - 1][tz] && outer[tx + 1][tz]
                    && outer[tx][tz - 1] && outer[tx][tz + 1];
                mask[tx][tz] = keep;
                if (keep) insideCount++;
            }
        }
        // Markers are the border ring, never playable floor. Clear any mask tile that
        // coincides with a corner marker - this enforces "markers sit outside the
        // floor" and also corrects the half-open rasterization asymmetry where a
        // single concave-armpit vertex could otherwise survive the 4-neighbour
        // erosion and lopside an otherwise-symmetric shape (e.g. plus / cross).
        for (BlockPos c : corners) {
            int ctx = c.getX() - gridMinX;
            int ctz = c.getZ() - gridMinZ;
            if (ctx >= 0 && ctx < gridW && ctz >= 0 && ctz < gridH && mask[ctx][ctz]) {
                mask[ctx][ctz] = false;
                insideCount--;
            }
        }
        if (insideCount == 0) {
            // Degrade to a plain rectangle over the marker bounding box with the
            // ground preserved - the old fallback repainted the level
            // definition's FULL rectangle (flattening terrain and laying a stone
            // underlayer far outside the drawn shape) and left the marker
            // blocks in the world because replacement hadn't run yet.
            CrafticsMod.LOGGER.warn("Polygon arena {} mask is empty (no tile inside the polygon). "
                + "Using the marker bounding box as a plain rectangle, ground preserved.",
                sourceName);
            for (BlockPos c : corners) {
                Block replacement = getMostCommonTouchingBlock(world, c,
                    getBorderConcreteForBiome(biomeId, tiles));
                world.setBlockState(c, replacement.getDefaultState(), SET_FLAGS);
            }
            BlockPos camFallback = diamondPos != null ? diamondPos : corners.get(0);
            pendingCameraYaw = yawFromTo(camFallback.getX() + 0.5, camFallback.getZ() + 0.5,
                gridMinX + gridW / 2.0, gridMinZ + gridH / 2.0);
            structureOrigin = new BlockPos(gridMinX, arenaFloorY, gridMinZ);
            structureGridW = gridW;
            structureGridH = gridH;
            structureInsideMask = null;
            structureOuterMask = null;
            return true;
        }

        // Camera yaw: face the polygon centroid from the camera anchor. A
        // DIAMOND_BLOCK marks the chosen camera-corner vertex; without one, fall back
        // to the first sorted corner (dev can rotate by re-ordering corners).
        BlockPos camAnchor = diamondPos != null ? diamondPos : sorted.get(0);
        pendingCameraYaw = yawFromTo(camAnchor.getX() + 0.5, camAnchor.getZ() + 0.5,
            gridMinX + gridW / 2.0, gridMinZ + gridH / 2.0);

        Block floorBlock = tiles[0][0].getBlockType();
        Block borderConcrete = getBorderConcreteForBiome(biomeId, tiles);

        // Replace corner markers with surrounding block so they blend in
        for (BlockPos c : corners) {
            Block replacement = getMostCommonTouchingBlock(world, c, borderConcrete);
            world.setBlockState(c, replacement.getDefaultState(), SET_FLAGS);
        }

        if (!preserveSchematicGround) {
            // Tile overlay: only paint floor on tiles inside the polygon mask.
            // Outside-the-polygon tiles are left untouched so the world's
            // natural terrain (or schematic decoration) shows through. Inside
            // the mask the level definition's tile pattern paints every tile -
            // that pattern IS the readable grid; arenas that want their ground
            // kept verbatim use preserveSchematicGround.
            for (int x = 0; x < gridW; x++) {
                for (int z = 0; z < gridH; z++) {
                    if (!mask[x][z]) continue;
                    int worldX = gridMinX + x;
                    int worldZ = gridMinZ + z;
                    BlockPos floorPos = new BlockPos(worldX, arenaFloorY, worldZ);

                    GridTile tile = (x < tiles.length && z < tiles[0].length)
                        ? tiles[x][z]
                        : new GridTile(com.crackedgames.craftics.core.TileType.NORMAL, floorBlock);

                    if (!world.getBlockState(floorPos).getFluidState().isEmpty()) continue;
                    if (world.getBlockState(floorPos).isAir()) {
                        BlockPos belowPos = new BlockPos(worldX, arenaFloorY - 1, worldZ);
                        BlockState belowState = world.getBlockState(belowPos);
                        boolean belowIsHazard = !belowState.getFluidState().isEmpty()
                            || belowState.isOf(Blocks.LAVA)
                            || belowState.isOf(Blocks.MAGMA_BLOCK);
                        if (!belowIsHazard) continue;
                        set(world, worldX, arenaFloorY - 1, worldZ, Blocks.STONE);
                    }
                    world.setBlockState(floorPos, tile.getBlockType().getDefaultState(), SET_FLAGS);
                    if (!tile.isWalkable() && !tile.isWater()) {
                        set(world, worldX, arenaFloorY + 1, worldZ, tile.getBlockType());
                    } else {
                        BlockPos above = new BlockPos(worldX, arenaFloorY + 1, worldZ);
                        BlockState aboveState = world.getBlockState(above);
                        if (!aboveState.isAir() && !(aboveState.getBlock() instanceof net.minecraft.block.CarpetBlock)) {
                            world.setBlockState(above, Blocks.AIR.getDefaultState(), SET_FLAGS);
                        }
                    }
                }
            }

            // Flat themed border: the 1-tile ring just inside the outline (tiles in
            // the polygon but eroded out of the playable mask - this is exactly where
            // the corner markers sit) is painted with the biome's border concrete at
            // FLOOR level, every tile, so the boundary reads as one continuous band
            // (gap-only patching left a speckled, inconsistent edge). Any block
            // directly above is cleared so the edge stays flush - at the corrected
            // floor height this only flattens the ring itself, not the author's
            // surrounding terrain.
            for (int x = 0; x < gridW; x++) {
                for (int z = 0; z < gridH; z++) {
                    if (!outer[x][z] || mask[x][z]) continue;
                    int worldX = gridMinX + x;
                    int worldZ = gridMinZ + z;
                    BlockPos borderPos = new BlockPos(worldX, arenaFloorY, worldZ);
                    if (world.getBlockState(borderPos).getFluidState().isEmpty()) {
                        world.setBlockState(borderPos, borderConcrete.getDefaultState(), SET_FLAGS);
                    }
                    BlockPos abovePos = new BlockPos(worldX, arenaFloorY + 1, worldZ);
                    if (!world.getBlockState(abovePos).isAir()) {
                        world.setBlockState(abovePos, Blocks.AIR.getDefaultState(), SET_FLAGS);
                    }
                }
            }
        }

        structureOrigin = new BlockPos(gridMinX, arenaFloorY, gridMinZ);
        structureGridW = gridW;
        structureGridH = gridH;
        structureInsideMask = mask;
        structureOuterMask = outer;

        // Spawn markers: honor those inside the polygon mask within one block
        // of the arena floor - like the corners, a spawn marker may be buried
        // just under the floor so it doesn't show. Out-of-mask markers are
        // ignored.
        @SuppressWarnings("unchecked")
        List<BlockPos>[] candidateLists = new List[]{goldCandidates, ironCandidates, copperCandidates, coalCandidates};
        BlockPos[] playerSpawns = new BlockPos[4];
        for (int i = 0; i < 4; i++) {
            for (BlockPos bp : candidateLists[i]) {
                if (Math.abs(bp.getY() - arenaFloorY) > 1) continue;
                int tx = bp.getX() - gridMinX;
                int tz = bp.getZ() - gridMinZ;
                if (tx < 0 || tx >= gridW || tz < 0 || tz >= gridH) continue;
                if (!mask[tx][tz]) continue;
                playerSpawns[i] = bp;
                break;
            }
        }
        String[] spawnNames = {"P1 (gold)", "P2 (iron)", "P3 (copper)", "P4 (coal)"};
        for (int i = 0; i < 4; i++) {
            if (playerSpawns[i] != null) {
                if (playerSpawns[i].getY() == arenaFloorY) {
                    // Exposed marker doubles as a floor tile - blend it out.
                    world.setBlockState(playerSpawns[i], floorBlock.getDefaultState(), SET_FLAGS);
                } else {
                    // Buried marker - swap for whatever surrounds it underground.
                    Block rep = getMostCommonTouchingBlock(world, playerSpawns[i], Blocks.STONE);
                    world.setBlockState(playerSpawns[i], rep.getDefaultState(), SET_FLAGS);
                }
                int gridX = playerSpawns[i].getX() - gridMinX;
                int gridZ = playerSpawns[i].getZ() - gridMinZ;
                CrafticsMod.LOGGER.info("  {} spawn (polygon): grid({}, {})", spawnNames[i], gridX, gridZ);
                if (i == 0) structurePlayerStart = new GridPos(gridX, gridZ);
            }
        }
        if (structurePlayerStart == null) {
            // Auto P1: closest inside-mask tile to the polygon centroid.
            int cxTile = gridW / 2, czTile = gridH / 2;
            int bestDist = Integer.MAX_VALUE;
            GridPos best = null;
            for (int x = 0; x < gridW; x++) {
                for (int z = 0; z < gridH; z++) {
                    if (!mask[x][z]) continue;
                    int d = Math.abs(x - cxTile) + Math.abs(z - czTile);
                    if (d < bestDist) {
                        bestDist = d;
                        best = new GridPos(x, z);
                    }
                }
            }
            structurePlayerStart = best != null ? best : new GridPos(0, 0);
            CrafticsMod.LOGGER.info("  Auto P1 spawn (polygon): grid({}, {})",
                structurePlayerStart.x(), structurePlayerStart.z());
        }

        CrafticsMod.LOGGER.info("Polygon arena {} built: {} corners, bbox {}x{}, {} tiles inside",
            sourceName, polyN, gridW, gridH, insideCount);
        return true;
    }

    /**
     * Order polygon corner markers into a single closed outline (XZ only -
     * corner Y is ignored).
     *
     * <p>Rectilinear vertex sets - every edge axis-aligned, which is what
     * arena authors actually draw (L, T, plus, U) - are reconstructed exactly
     * by {@link #tryRectilinearOutline}. Anything that doesn't form a single
     * simple rectilinear ring (diamonds, octagons, hexagons - diagonal edges)
     * falls back to a centroid-angle sort, which is correct for convex rings.
     * Nearest-neighbor chaining was tried here and rejected: from a plus
     * shape's concave vertex the closest unvisited corner is across the
     * interior, not along the arm, so the chain cut through the shape.
     */
    private static List<BlockPos> orderOutline(List<BlockPos> corners) {
        // Dedupe by column - two markers stacked in the same X/Z column would
        // break both ordering strategies.
        java.util.LinkedHashMap<Long, BlockPos> unique = new java.util.LinkedHashMap<>();
        for (BlockPos c : corners) {
            unique.putIfAbsent(((long) c.getX() << 32) ^ (c.getZ() & 0xFFFFFFFFL), c);
        }
        List<BlockPos> verts = new ArrayList<>(unique.values());

        List<BlockPos> rectilinear = tryRectilinearOutline(verts);
        if (rectilinear != null) return rectilinear;

        double cx = 0, cz = 0;
        for (BlockPos c : verts) { cx += c.getX(); cz += c.getZ(); }
        final double centerX = cx / verts.size(), centerZ = cz / verts.size();
        List<BlockPos> sorted = new ArrayList<>(verts);
        sorted.sort((a, b) -> {
            double angA = Math.atan2(a.getZ() - centerZ, a.getX() - centerX);
            double angB = Math.atan2(b.getZ() - centerZ, b.getX() - centerX);
            return Double.compare(angA, angB);
        });
        return sorted;
    }

    /**
     * Exact outline reconstruction for rectilinear polygons. In a simple
     * rectilinear ring every vertex joins exactly one vertical and one
     * horizontal edge, and those edges are recovered uniquely by pairing
     * consecutive vertices within each X column and each Z row. Returns the
     * ring in walk order, or {@code null} when the vertex set isn't a single
     * simple rectilinear ring (odd column/row counts, false pairings that
     * close early, multiple loops) - callers then fall back to the angle sort.
     */
    private static List<BlockPos> tryRectilinearOutline(List<BlockPos> verts) {
        int n = verts.size();
        if (n < 4 || (n & 1) != 0) return null; // rectilinear rings have even vertex counts

        Map<Integer, List<Integer>> byX = new HashMap<>();
        Map<Integer, List<Integer>> byZ = new HashMap<>();
        for (int i = 0; i < n; i++) {
            byX.computeIfAbsent(verts.get(i).getX(), k -> new ArrayList<>()).add(i);
            byZ.computeIfAbsent(verts.get(i).getZ(), k -> new ArrayList<>()).add(i);
        }

        int[] vPartner = new int[n];
        int[] hPartner = new int[n];
        java.util.Arrays.fill(vPartner, -1);
        java.util.Arrays.fill(hPartner, -1);
        for (List<Integer> column : byX.values()) {
            if ((column.size() & 1) != 0) return null;
            column.sort(java.util.Comparator.comparingInt(i -> verts.get(i).getZ()));
            for (int k = 0; k + 1 < column.size(); k += 2) {
                vPartner[column.get(k)] = column.get(k + 1);
                vPartner[column.get(k + 1)] = column.get(k);
            }
        }
        for (List<Integer> row : byZ.values()) {
            if ((row.size() & 1) != 0) return null;
            row.sort(java.util.Comparator.comparingInt(i -> verts.get(i).getX()));
            for (int k = 0; k + 1 < row.size(); k += 2) {
                hPartner[row.get(k)] = row.get(k + 1);
                hPartner[row.get(k + 1)] = row.get(k);
            }
        }

        // Walk the ring, alternating vertical and horizontal edges. A valid
        // simple ring visits every vertex exactly once and closes at the start;
        // false pairings (e.g. an octagon's columns pair up but join vertices
        // that aren't really adjacent) close early and are rejected.
        List<BlockPos> ring = new ArrayList<>(n);
        boolean[] seen = new boolean[n];
        int at = 0;
        boolean vertical = true;
        for (int step = 0; step < n; step++) {
            if (seen[at]) return null;
            seen[at] = true;
            ring.add(verts.get(at));
            at = vertical ? vPartner[at] : hPartner[at];
            if (at < 0) return null;
            vertical = !vertical;
        }
        return at == 0 ? ring : null;
    }

    /**
     * When corner markers are missing or invalid, scan the placed schematic to
     * find the Y level that looks like a floor - the first layer above
     * {@code placeY} where most columns in the arena footprint have a solid
     * block with open space above it. Falls back to {@code oy} if nothing
     * suitable is found so the caller's default behavior is unchanged.
     */
    private static int scanSchematicFloorY(ServerWorld world,
                                            int placeX, int placeY, int placeZ,
                                            int sizeX, int sizeY, int sizeZ,
                                            int ox, int oz, int w, int h) {
        int overlapMinX = Math.max(placeX, ox);
        int overlapMaxX = Math.min(placeX + sizeX - 1, ox + w - 1);
        int overlapMinZ = Math.max(placeZ, oz);
        int overlapMaxZ = Math.min(placeZ + sizeZ - 1, oz + h - 1);
        if (overlapMaxX < overlapMinX || overlapMaxZ < overlapMinZ) return placeY;

        int columnCount = (overlapMaxX - overlapMinX + 1) * (overlapMaxZ - overlapMinZ + 1);
        if (columnCount <= 0) return placeY;

        int bestY = placeY;
        int bestScore = -1;
        int maxY = placeY + sizeY - 2;
        for (int y = placeY; y <= maxY; y++) {
            int score = 0;
            for (int x = overlapMinX; x <= overlapMaxX; x++) {
                for (int z = overlapMinZ; z <= overlapMaxZ; z++) {
                    BlockState floor = world.getBlockState(new BlockPos(x, y, z));
                    BlockState above = world.getBlockState(new BlockPos(x, y + 1, z));
                    // "Floor-ish" = solid block with air / non-solid above
                    if (!floor.isAir() && floor.isSolidBlock(world, new BlockPos(x, y, z))
                            && (above.isAir() || !above.isSolidBlock(world, new BlockPos(x, y + 1, z)))) {
                        score++;
                    }
                }
            }
            // Require at least half the columns to agree before accepting this Y,
            // and prefer the LOWEST qualifying layer (gameplay floor, not a roof).
            if (score * 2 >= columnCount && score > bestScore) {
                bestScore = score;
                bestY = y + 1; // tiles paint at the level above the solid block
                break;
            }
        }
        return bestY;
    }

    // Fallback tile overlay when marker blocks are missing
    private static void overlayArenaTiles(ServerWorld world, int ox, int oy, int oz,
                                            int w, int h, GridTile[][] tiles) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                GridTile tile = tiles[x][z];
                Block tileBlock = tile.getBlockType();
                boolean airLikeTile = tileBlock == Blocks.AIR || tileBlock == Blocks.CAVE_AIR || tileBlock == Blocks.VOID_AIR;
                if (!airLikeTile) {
                    set(world, ox + x, oy - 1, oz + z, Blocks.STONE);
                }
                if (airLikeTile) {
                    world.setBlockState(new BlockPos(ox + x, oy, oz + z), Blocks.AIR.getDefaultState(), SET_FLAGS);
                    world.setBlockState(new BlockPos(ox + x, oy + 1, oz + z), Blocks.AIR.getDefaultState(), SET_FLAGS);
                } else if (!tile.isWalkable() && !tile.isWater()) {
                    // Obstacle: solid at oy, block at oy+1
                    world.setBlockState(new BlockPos(ox + x, oy, oz + z),
                        Blocks.STONE.getDefaultState(), SET_FLAGS);
                    set(world, ox + x, oy + 1, oz + z, tile.getBlockType());
                } else {
                    world.setBlockState(new BlockPos(ox + x, oy, oz + z),
                        tile.getBlockType().getDefaultState(), SET_FLAGS);
                    world.setBlockState(new BlockPos(ox + x, oy + 1, oz + z), Blocks.AIR.getDefaultState(), SET_FLAGS);
                }
            }
        }
    }

    // Procedural flat arena when no structure file is found
    private static void buildProceduralFallback(ServerWorld world, int ox, int oy, int oz,
                                                  int w, int h, GridTile[][] tiles, Random rng) {
        int pad = 2;

        // Only clear if blocks exist (e.g. previous arena at same location)
        boolean needsClear = !world.getBlockState(new BlockPos(ox, oy, oz)).isAir();
        if (needsClear) {
            for (int x = -pad; x < w + pad; x++) {
                for (int z = -pad; z < h + pad; z++) {
                    for (int y = -3; y <= 6; y++) {
                        BlockPos bp = new BlockPos(ox + x, oy + y, oz + z);
                        if (!world.getBlockState(bp).isAir()) {
                            world.setBlockState(bp, Blocks.AIR.getDefaultState(), SET_FLAGS);
                        }
                    }
                }
            }
        }

        // Bedrock + stone base under the arena
        for (int x = -1; x < w + 1; x++) {
            for (int z = -1; z < h + 1; z++) {
                if (x >= 0 && x < w && z >= 0 && z < h) {
                    Block tileBlock = tiles[x][z].getBlockType();
                    if (tileBlock == Blocks.AIR || tileBlock == Blocks.CAVE_AIR || tileBlock == Blocks.VOID_AIR) {
                        continue;
                    }
                }
                set(world, ox + x, oy - 2, oz + z, Blocks.BEDROCK);
                set(world, ox + x, oy - 1, oz + z, Blocks.STONE);
            }
        }

        // Grass border ring
        for (int x = -pad; x < w + pad; x++) {
            for (int z = -pad; z < h + pad; z++) {
                if (x >= 0 && x < w && z >= 0 && z < h) continue;
                set(world, ox + x, oy, oz + z, Blocks.GRASS_BLOCK);
            }
        }

        overlayArenaTiles(world, ox, oy, oz, w, h, tiles);
    }

    /**
     * Search a directory for .schem files matching the biome.
     * Boss levels use boss.schem or <biome>_boss.schem.
     * Normal levels use numbered files: 1.schem, <biome>_1.schem, etc.
     */
    private static void searchSchemFiles(java.nio.file.Path searchDir, String biomeId,
                                          boolean isBoss, List<java.nio.file.Path> results) {
        if (!java.nio.file.Files.isDirectory(searchDir)) return;
        try {
            // Sub-biome IDs like "forest/pale_garden" → look for forest/pale_garden.schem
            if (biomeId.contains("/")) {
                java.nio.file.Path subBiomeSchem = searchDir.resolve(biomeId + ".schem");
                if (java.nio.file.Files.exists(subBiomeSchem) && !results.contains(subBiomeSchem)) {
                    results.add(subBiomeSchem);
                }
                return; // sub-biome: specific file only, no numbered variants
            }

            // WorldEdit-style flat naming: <biome>.schem, <biome>_2.schem, <biome>_boss.schem
            java.util.List<String> aliases = new java.util.ArrayList<>();
            aliases.add(biomeId);
            if (biomeId.endsWith("s") && biomeId.length() > 1) {
                aliases.add(biomeId.substring(0, biomeId.length() - 1));
            }
            if ("trial_chamber".equals(biomeId) || "trial_chambers".equals(biomeId)) {
                aliases.add("trial_chamber");
                aliases.add("trial_chambers");
            }

            for (String alias : aliases) {
                java.nio.file.Path direct = searchDir.resolve(alias + ".schem");
                if (java.nio.file.Files.exists(direct) && !results.contains(direct)) {
                    results.add(direct);
                }

                if (isBoss) {
                    java.nio.file.Path bossFlat = searchDir.resolve(alias + "_boss.schem");
                    if (java.nio.file.Files.exists(bossFlat) && !results.contains(bossFlat)) {
                        results.add(bossFlat);
                    }
                } else {
                    for (int i = 1; i <= 10; i++) {
                        java.nio.file.Path numberedFlat = searchDir.resolve(alias + "_" + i + ".schem");
                        if (java.nio.file.Files.exists(numberedFlat) && !results.contains(numberedFlat)) {
                            results.add(numberedFlat);
                        }
                    }
                }
            }

            java.nio.file.Path biomeDir = null;

            java.nio.file.Path direct = searchDir.resolve(biomeId);
            if (java.nio.file.Files.isDirectory(direct)) biomeDir = direct;

            if (biomeDir == null) {
                try (var datapacks = java.nio.file.Files.list(searchDir)) {
                    for (var dp : datapacks.toList()) {
                        java.nio.file.Path nested = dp.resolve("data/craftics/arenas/" + biomeId);
                        if (java.nio.file.Files.isDirectory(nested)) {
                            biomeDir = nested;
                            break;
                        }
                    }
                }
            }

            // Plain numbered files (1.schem) in the root are skipped - no biome association.
            // Use biome-prefixed (desert_1.schem) or subdirectory (desert/1.schem) instead.

            if (biomeDir == null) return;

            if (isBoss) {
                java.nio.file.Path bossSchem = biomeDir.resolve("boss.schem");
                if (java.nio.file.Files.exists(bossSchem) && !results.contains(bossSchem)) {
                    results.add(bossSchem);
                }
                return;
            }

            for (int i = 1; i <= 10; i++) {
                java.nio.file.Path schemFile = biomeDir.resolve(i + ".schem");
                if (java.nio.file.Files.exists(schemFile)) {
                    if (!results.contains(schemFile)) {
                        results.add(schemFile);
                    }
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            CrafticsMod.LOGGER.debug("Error searching for .schem files in {}: {}", searchDir, e.getMessage());
        }
    }

    // Load a disk .schem and place it with marker detection
    private static boolean loadAndPlaceSchem(ServerWorld world, java.nio.file.Path schemPath,
                                              int ox, int oy, int oz, int w, int h, GridTile[][] tiles,
                                              String biomeId,
                                              boolean preserveSchematicGround) {
        SchemLoader.SchemData schem = SchemLoader.load(schemPath);
        if (schem == null) return false;

        CrafticsMod.LOGGER.info("Placing .schem arena: {} ({}x{}x{})",
            schemPath.getFileName(), schem.width(), schem.height(), schem.length());

        int clearPad = 3;
        int placeX = ox + w / 2 - schem.width() / 2;
        int placeY = oy;
        int placeZ = oz + h / 2 - schem.length() / 2;

        for (int x = -clearPad; x < schem.width() + clearPad; x++) {
            for (int z = -clearPad; z < schem.length() + clearPad; z++) {
                for (int y = -3; y <= schem.height() + 3; y++) {
                    BlockPos bp = new BlockPos(placeX + x, placeY + y, placeZ + z);
                    if (!world.getBlockState(bp).isAir()) {
                        world.setBlockState(bp, Blocks.AIR.getDefaultState(), SET_FLAGS);
                    }
                }
            }
        }

        // Skip the edge taper for preserve-ground schematics (bosses + trial
        // chambers) - the dev hand-built the arena and the stepped slope
        // carver would leave 1-block-thick walls 2-3 tiles in from the sloped
        // edge wherever their schematic's walls fit within the dist*2 height
        // envelope. The clearPad=3 air sweep already handles surrounding
        // terrain blending without modifying the schematic interior.
        // place() needs to know up front: the cull would otherwise hollow out
        // the very columns the taper is about to slice open.
        schem.placeTapered(world, placeX, placeY, placeZ, !preserveSchematicGround);
        if (!preserveSchematicGround) {
            taperSchemEdges(world, placeX, placeY, placeZ, schem.width(), schem.height(), schem.length());
        }

        return processPlacedStructure(world, placeX, placeY, placeZ,
            schem.width(), schem.height(), schem.length(), ox, oy, oz, w, h, tiles,
            schemPath.getFileName().toString(), biomeId, preserveSchematicGround);
    }

    // Load a bundled .schem from mod resources (JAR)
    private static boolean loadAndPlaceBundledSchem(ServerWorld world,
                                                     net.minecraft.resource.ResourceManager resourceManager,
                                                     Identifier resourceId,
                                                     int ox, int oy, int oz, int w, int h, GridTile[][] tiles,
                                                     String biomeId, boolean preserveSchematicGround) {
        try {
            var resource = resourceManager.getResource(resourceId);
            if (resource.isEmpty()) return false;

            SchemLoader.SchemData schem;
            try (java.io.InputStream in = resource.get().getInputStream()) {
                schem = SchemLoader.load(in, resourceId.toString());
            }
            if (schem == null) return false;

            CrafticsMod.LOGGER.info("Placing bundled .schem arena: {} ({}x{}x{})",
                resourceId, schem.width(), schem.height(), schem.length());

            int clearPad = 3;
            int placeX = ox + w / 2 - schem.width() / 2;
            int placeY = oy;
            int placeZ = oz + h / 2 - schem.length() / 2;

            for (int x = -clearPad; x < schem.width() + clearPad; x++) {
                for (int z = -clearPad; z < schem.length() + clearPad; z++) {
                    for (int y = -3; y <= schem.height() + 3; y++) {
                        BlockPos bp = new BlockPos(placeX + x, placeY + y, placeZ + z);
                        if (!world.getBlockState(bp).isAir()) {
                            world.setBlockState(bp, Blocks.AIR.getDefaultState(), SET_FLAGS);
                        }
                    }
                }
            }

            // Same preserveGround taper skip as the disk path - see loadAndPlaceSchem.
            schem.placeTapered(world, placeX, placeY, placeZ, !preserveSchematicGround);
            if (!preserveSchematicGround) {
                taperSchemEdges(world, placeX, placeY, placeZ, schem.width(), schem.height(), schem.length());
            }

            return processPlacedStructure(world, placeX, placeY, placeZ,
                schem.width(), schem.height(), schem.length(), ox, oy, oz, w, h, tiles,
                resourceId.getPath(), biomeId, preserveSchematicGround);
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("Failed to load bundled arena: {}", resourceId, e);
            return false;
        }
    }

    /** How far in from a schematic edge the taper's stepped slope reaches. */
    static final int TAPER_FADE = 4;

    /**
     * Highest schematic-local Y the taper keeps in the column {@code dist} tiles
     * in from the nearest edge, or {@code sizeY - 1} (keep the whole column)
     * outside the fade zone. The slope rises 2 blocks per tile inward, so the
     * cut edge reads as a stepped bank rather than a sliced-off wall.
     *
     * <p>Pure so {@code SchemTaperKeepTest} can pin the geometry down without a
     * Minecraft bootstrap, and so the cull can mirror it exactly.
     */
    static int taperMaxKeepLocalY(int dist, int sizeY) {
        if (dist >= TAPER_FADE) return sizeY - 1;
        return Math.min(dist * 2, sizeY - 1);
    }

    /** Distance from (x,z) to the nearest edge of a sizeX-by-sizeZ footprint. */
    static int taperEdgeDist(int x, int z, int sizeX, int sizeZ) {
        return Math.min(Math.min(x, sizeX - 1 - x), Math.min(z, sizeZ - 1 - z));
    }

    // Taper schematic edges so terrain doesn't look sliced, and seal the cut so
    // the arena's hollow underside isn't exposed.
    private static void taperSchemEdges(ServerWorld world, int px, int py, int pz,
                                         int sizeX, int sizeY, int sizeZ) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int dist = taperEdgeDist(x, z, sizeX, sizeZ);
                if (dist >= TAPER_FADE) continue;

                // Fade zone: keep fewer blocks as you approach the edge
                int maxKeepY = py + taperMaxKeepLocalY(dist, sizeY);
                for (int y = maxKeepY + 1; y < py + sizeY; y++) {
                    BlockPos bp = new BlockPos(px + x, y, pz + z);
                    if (!world.getBlockState(bp).isAir()) {
                        world.setBlockState(bp, Blocks.AIR.getDefaultState(), SET_FLAGS);
                    }
                }

                // The cut face itself is sealed by SchemData.place's
                // taper-aware cull (see TAPER_FADE there): the columns this
                // slope exposes are placed solid instead of hollow, so there is
                // no cavity left to backfill here. Filling from this side is
                // not possible anyway - the world cannot tell a cull cavity
                // from a gap the schematic author left open (a river bank's
                // undercut), and stoning those shut walls up the arena's edge.
            }
        }
    }

    // Barrier box around schematic - floor + walls, no ceiling
    private static void buildBarrierContainmentShell(ServerWorld world,
                                                      int placeX, int placeY, int placeZ,
                                                      int sizeX, int sizeY, int sizeZ) {
        int minX = placeX - 1;
        int maxX = placeX + sizeX;
        int minZ = placeZ - 1;
        int maxZ = placeZ + sizeZ;
        int floorY = placeY - 1;
        int topY = placeY + sizeY + 1;

        // Floor catches liquids
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockState(new BlockPos(x, floorY, z), Blocks.BARRIER.getDefaultState(), SET_FLAGS);
            }
        }

        // Side walls only
        for (int y = floorY; y <= topY; y++) {
            for (int x = minX; x <= maxX; x++) {
                world.setBlockState(new BlockPos(x, y, minZ), Blocks.BARRIER.getDefaultState(), SET_FLAGS);
                world.setBlockState(new BlockPos(x, y, maxZ), Blocks.BARRIER.getDefaultState(), SET_FLAGS);
            }
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockState(new BlockPos(minX, y, z), Blocks.BARRIER.getDefaultState(), SET_FLAGS);
                world.setBlockState(new BlockPos(maxX, y, z), Blocks.BARRIER.getDefaultState(), SET_FLAGS);
            }
        }
    }

    // Light posts themed per biome. Rectangular arenas ring the bbox; polygon
    // arenas hug the actual outline (spaced) so the lights follow the shape rather
    // than a misleading square. Posts sit on the local terrain surface so they
    // don't embed when the surrounding ground rises above the arena floor.
    private static void placeLighting(ServerWorld world, int ox, int oy, int oz,
                                       int w, int h, com.crackedgames.craftics.api.EnvironmentDef env,
                                       boolean[][] insideMask) {
        Block postBlock = env.postBlock();
        Block lightBlock = env.lightBlock();

        if (insideMask != null) {
            // Polygon: a post on each border tile (just outside the playable mask,
            // adjacent to it), spaced ~every 3rd so the outline reads clearly.
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < h; z++) {
                    if (insideMask[x][z]) continue;
                    boolean adjacent = (x > 0 && insideMask[x - 1][z])
                        || (x < w - 1 && insideMask[x + 1][z])
                        || (z > 0 && insideMask[x][z - 1])
                        || (z < h - 1 && insideMask[x][z + 1]);
                    if (!adjacent) continue;
                    int px = ox + x, pz = oz + z;
                    if (((px + pz) % 3) != 0) continue;
                    placeLightPost(world, px, pz, oy, postBlock, lightBlock);
                }
            }
            return;
        }

        List<int[]> posts = new ArrayList<>();
        posts.add(new int[]{-1, -1});
        posts.add(new int[]{-1, h});
        posts.add(new int[]{w, -1});
        posts.add(new int[]{w, h});

        // Along edges every 3 tiles
        for (int x = 2; x < w - 1; x += 3) {
            posts.add(new int[]{x, -1});
            posts.add(new int[]{x, h});
        }

        for (int z = 2; z < h - 1; z += 3) {
            posts.add(new int[]{-1, z});
            posts.add(new int[]{w, z});
        }

        // Base + post + light - only place next to solid arena terrain
        for (int[] p : posts) {
            int px = ox + p[0], pz = oz + p[1];
            // Check the nearest arena tile(s) adjacent to this post.
            // If all neighbouring arena tiles are air/void, skip - it's a cliff edge.
            if (!hasAdjacentSolidTile(world, px, oy, pz, ox, oz, w, h)) continue;
            placeLightPost(world, px, pz, oy, postBlock, lightBlock);
        }
    }

    /** Place a base + post + light column sitting on the local terrain surface
     *  (highest solid block near the arena floor) so it doesn't embed when the
     *  surrounding ground is higher than the arena floor. */
    private static void placeLightPost(ServerWorld world, int px, int pz, int oy,
                                        Block postBlock, Block lightBlock) {
        int sy = oy;
        for (int y = oy + 5; y >= oy - 2; y--) {
            BlockState s = world.getBlockState(new BlockPos(px, y, pz));
            if (!isAirLike(s) && s.getFluidState().isEmpty()) { sy = y; break; }
        }
        setIf(world, px, sy, pz, Blocks.STONE);   // ensure solid base
        set(world, px, sy + 1, pz, postBlock);
        set(world, px, sy + 2, pz, lightBlock);
    }

    /** Check if a border position has at least one adjacent solid arena tile at floor level. */
    private static boolean hasAdjacentSolidTile(ServerWorld world, int px, int oy, int pz,
                                                  int ox, int oz, int w, int h) {
        int[][] neighbors = {{px - 1, pz}, {px + 1, pz}, {px, pz - 1}, {px, pz + 1}};
        for (int[] n : neighbors) {
            int nx = n[0], nz = n[1];
            // Only check tiles inside the arena grid
            if (nx < ox || nx >= ox + w || nz < oz || nz >= oz + h) continue;
            BlockState state = world.getBlockState(new BlockPos(nx, oy, nz));
            if (!isAirLike(state) && state.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /**
     * Place biome-themed random obstacles on the arena floor.
     * Only placed on solid, non-liquid, non-air tiles with clearance above.
     * Avoids a 2-tile margin from each edge so obstacles don't crowd the border.
     */
    private static void placeBiomeObstacles(ServerWorld world, int ox, int oy, int oz,
                                              int w, int h, com.crackedgames.craftics.api.EnvironmentDef env,
                                              Random rng) {
        switch (env.decorStyle()) {
            case "forest" -> placeFallenLogs(world, ox, oy, oz, w, h, rng);
            case "jungle" -> placeSimpleObstacles(world, ox, oy, oz, w, h, Blocks.COBWEB, 0, 5, rng);
            case "mountain" -> placePitObstacles(world, ox, oy, oz, w, h, 3, 6, rng);
            case "desert" -> {
                placeSimpleObstacles(world, ox, oy, oz, w, h, Blocks.CACTUS, 1, 3, rng);
                //? if >=1.21.5 {
                /*placeCactusFlowerToppers(world, ox, oy, oz, w, h, rng);
                *///?}
            }
            case "snowy" -> placePowderSnowPatch(world, ox, oy, oz, w, h, rng);
            case "cave", "deep_dark" -> placePitObstacles(world, ox, oy, oz, w, h, 0, 7, rng);
            case "nether" -> placeFloorHazards(world, ox, oy, oz, w, h, Blocks.LAVA, 2, 5, rng);
            case "crimson_forest" -> placeFallenNetherLogs(world, ox, oy, oz, w, h, Blocks.CRIMSON_STEM, rng);
            case "warped_forest" -> placeFallenNetherLogs(world, ox, oy, oz, w, h, Blocks.WARPED_STEM, rng);
            case "plains" -> placePlainsFoliage(world, ox, oy, oz, w, h, rng);
            default -> {} // no flavor obstacles for custom environments
        }
    }

    /**
     * Scatter patches of tall grass and large ferns across a plains arena. These
     * are 2-block-tall plants; the occupant tile becomes {@code TALL_GRASS} /
     * {@code TALL_FERN} during the post-placement scan and provides stealth.
     */
    private static void placePlainsFoliage(ServerWorld world, int ox, int oy, int oz,
                                            int w, int h, Random rng) {
        int grassCount = 2 + rng.nextInt(2); // 2-3 tall grass patches
        int fernCount = 1 + rng.nextInt(2);  // 1-2 large fern patches

        java.util.Set<Long> used = new java.util.HashSet<>();

        for (int i = 0; i < grassCount; i++) {
            tryPlaceDoublePlant(world, ox, oy, oz, w, h, Blocks.TALL_GRASS, used, rng);
        }
        for (int i = 0; i < fernCount; i++) {
            tryPlaceDoublePlant(world, ox, oy, oz, w, h, Blocks.LARGE_FERN, used, rng);
        }
    }

    /**
     * Place a 2-block-tall plant (tall grass / large fern) at a random walkable
     * floor tile with 2-block vertical clearance. LOWER half goes at Y+1, UPPER
     * at Y+2. Silently skips if no valid spot is found within 20 attempts.
     */
    private static void tryPlaceDoublePlant(ServerWorld world, int ox, int oy, int oz,
                                             int w, int h, Block plant,
                                             java.util.Set<Long> used, Random rng) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int tx = 2 + rng.nextInt(Math.max(1, w - 4));
            int tz = 2 + rng.nextInt(Math.max(1, h - 4));
            if (used.contains(packPos(tx, tz))) continue;

            BlockPos floorPos = new BlockPos(ox + tx, oy, oz + tz);
            BlockPos abovePos = new BlockPos(ox + tx, oy + 1, oz + tz);
            BlockPos headPos  = new BlockPos(ox + tx, oy + 2, oz + tz);

            if (!world.getBlockState(floorPos).isSolidBlock(world, floorPos)) continue;
            if (!isAirLike(world.getBlockState(abovePos))) continue;
            if (!isAirLike(world.getBlockState(headPos))) continue;

            BlockState lower = plant.getDefaultState()
                .with(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF,
                      net.minecraft.block.enums.DoubleBlockHalf.LOWER);
            BlockState upper = plant.getDefaultState()
                .with(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF,
                      net.minecraft.block.enums.DoubleBlockHalf.UPPER);
            world.setBlockState(abovePos, lower, 2);
            world.setBlockState(headPos, upper, 2);

            used.add(packPos(tx, tz));
            return;
        }
    }

    /** Place 1-2 fallen sideways dark oak logs (2-4 blocks long) on the arena floor. */
    private static void placeFallenLogs(ServerWorld world, int ox, int oy, int oz,
                                          int w, int h, Random rng) {
        int count = 1 + rng.nextInt(2); // 1-2 logs

        // Horizontal log states: axis=x runs east-west, axis=z runs north-south
        BlockState logX = Blocks.DARK_OAK_LOG.getDefaultState()
            .with(net.minecraft.state.property.Properties.AXIS, net.minecraft.util.math.Direction.Axis.X);
        BlockState logZ = Blocks.DARK_OAK_LOG.getDefaultState()
            .with(net.minecraft.state.property.Properties.AXIS, net.minecraft.util.math.Direction.Axis.Z);

        java.util.Set<Long> used = new java.util.HashSet<>();

        for (int i = 0; i < count; i++) {
            int length = 2 + rng.nextInt(3); // 2-4
            boolean alongX = rng.nextBoolean();
            BlockState logState = alongX ? logX : logZ;

            // Try up to 20 times to find a valid placement
            for (int attempt = 0; attempt < 20; attempt++) {
                int sx = 2 + rng.nextInt(w - 4);
                int sz = 2 + rng.nextInt(h - 4);

                // Check that the full log run fits and every tile is solid ground with clearance
                boolean valid = true;
                List<int[]> positions = new ArrayList<>();
                for (int seg = 0; seg < length; seg++) {
                    int tx = alongX ? sx + seg : sx;
                    int tz = alongX ? sz : sz + seg;
                    if (tx < 2 || tx >= w - 2 || tz < 2 || tz >= h - 2) { valid = false; break; }
                    if (used.contains(packPos(tx, tz))) { valid = false; break; }

                    BlockPos floorPos = new BlockPos(ox + tx, oy, oz + tz);
                    BlockPos abovePos = new BlockPos(ox + tx, oy + 1, oz + tz);
                    BlockState floorState = world.getBlockState(floorPos);
                    if (isAirLike(floorState) || !floorState.getFluidState().isEmpty()) { valid = false; break; }
                    if (!world.getBlockState(abovePos).isAir()) { valid = false; break; }
                    positions.add(new int[]{tx, tz});
                }
                if (!valid || positions.size() != length) continue;

                // Place the log
                for (int[] p : positions) {
                    world.setBlockState(new BlockPos(ox + p[0], oy + 1, oz + p[1]), logState, SET_FLAGS);
                    used.add(packPos(p[0], p[1]));
                }
                break;
            }
        }
    }

    /** Place 1-2 fallen sideways nether stems (2-4 blocks long) on the arena floor. */
    private static void placeFallenNetherLogs(ServerWorld world, int ox, int oy, int oz,
                                                int w, int h, Block logBlock, Random rng) {
        int count = 1 + rng.nextInt(2); // 1-2 logs

        BlockState logX = logBlock.getDefaultState()
            .with(net.minecraft.state.property.Properties.AXIS, net.minecraft.util.math.Direction.Axis.X);
        BlockState logZ = logBlock.getDefaultState()
            .with(net.minecraft.state.property.Properties.AXIS, net.minecraft.util.math.Direction.Axis.Z);

        java.util.Set<Long> used = new java.util.HashSet<>();

        for (int i = 0; i < count; i++) {
            int length = 2 + rng.nextInt(3); // 2-4
            boolean alongX = rng.nextBoolean();
            BlockState logState = alongX ? logX : logZ;

            for (int attempt = 0; attempt < 20; attempt++) {
                int sx = 2 + rng.nextInt(w - 4);
                int sz = 2 + rng.nextInt(h - 4);

                boolean valid = true;
                List<int[]> positions = new ArrayList<>();
                for (int seg = 0; seg < length; seg++) {
                    int tx = alongX ? sx + seg : sx;
                    int tz = alongX ? sz : sz + seg;
                    if (tx < 2 || tx >= w - 2 || tz < 2 || tz >= h - 2) { valid = false; break; }
                    if (used.contains(packPos(tx, tz))) { valid = false; break; }

                    BlockPos floorPos = new BlockPos(ox + tx, oy, oz + tz);
                    BlockPos abovePos = new BlockPos(ox + tx, oy + 1, oz + tz);
                    BlockState floorState = world.getBlockState(floorPos);
                    if (isAirLike(floorState) || !floorState.getFluidState().isEmpty()) { valid = false; break; }
                    if (!world.getBlockState(abovePos).isAir()) { valid = false; break; }
                    positions.add(new int[]{tx, tz});
                }
                if (!valid || positions.size() != length) continue;

                for (int[] p : positions) {
                    world.setBlockState(new BlockPos(ox + p[0], oy + 1, oz + p[1]), logState, SET_FLAGS);
                    used.add(packPos(p[0], p[1]));
                }
                break;
            }
        }
    }

    /** Place simple block obstacles (logs, cobwebs, etc.) at Y+1 on valid solid tiles. */
    /** Place hazard blocks at floor level (Y=oy), replacing the existing floor block. */
    private static void placeFloorHazards(ServerWorld world, int ox, int oy, int oz,
                                            int w, int h, Block block, int min, int max, Random rng) {
        int count = min + rng.nextInt(max - min + 1);
        if (count <= 0) return;

        List<int[]> candidates = new ArrayList<>();
        for (int x = 2; x < w - 2; x++) {
            for (int z = 2; z < h - 2; z++) {
                candidates.add(new int[]{x, z});
            }
        }
        java.util.Collections.shuffle(candidates, rng);

        int placed = 0;
        for (int[] c : candidates) {
            if (placed >= count) break;
            BlockPos floorPos = new BlockPos(ox + c[0], oy, oz + c[1]);
            BlockState floorState = world.getBlockState(floorPos);

            // Must be solid ground - no air, no liquid, no existing hazard
            if (isAirLike(floorState) || !floorState.getFluidState().isEmpty()) continue;
            if (floorState.getBlock() == Blocks.POWDER_SNOW || floorState.getBlock() == Blocks.LAVA) continue;

            world.setBlockState(floorPos, block.getDefaultState(), SET_FLAGS);
            placed++;
        }
    }

    private static void placeSimpleObstacles(ServerWorld world, int ox, int oy, int oz,
                                               int w, int h, Block block, int min, int max, Random rng) {
        int count = min + rng.nextInt(max - min + 1);
        if (count <= 0) return;

        List<int[]> candidates = new ArrayList<>();
        for (int x = 2; x < w - 2; x++) {
            for (int z = 2; z < h - 2; z++) {
                candidates.add(new int[]{x, z});
            }
        }
        java.util.Collections.shuffle(candidates, rng);

        int placed = 0;
        for (int[] c : candidates) {
            if (placed >= count) break;
            BlockPos floorPos = new BlockPos(ox + c[0], oy, oz + c[1]);
            BlockPos abovePos = new BlockPos(ox + c[0], oy + 1, oz + c[1]);
            BlockState floorState = world.getBlockState(floorPos);

            // Must be solid ground - no air, no liquid
            if (isAirLike(floorState) || !floorState.getFluidState().isEmpty()) continue;
            // Must have clearance above
            if (!world.getBlockState(abovePos).isAir()) continue;

            world.setBlockState(abovePos, block.getDefaultState(), SET_FLAGS);
            placed++;
        }
    }

    //? if >=1.21.5 {
    /*// Scan the arena floor for cactus blocks placed by placeSimpleObstacles and,
    // for each one, roll a 50/50 to stack a cactus flower on top. 1.21.5+ only.
    private static void placeCactusFlowerToppers(ServerWorld world, int ox, int oy, int oz,
                                                   int w, int h, Random rng) {
        for (int x = 2; x < w - 2; x++) {
            for (int z = 2; z < h - 2; z++) {
                BlockPos cactusPos = new BlockPos(ox + x, oy + 1, oz + z);
                if (!world.getBlockState(cactusPos).isOf(Blocks.CACTUS)) continue;
                if (rng.nextFloat() >= 0.5f) continue;
                BlockPos flowerPos = cactusPos.up();
                if (!world.getBlockState(flowerPos).isAir()) continue;
                world.setBlockState(flowerPos, Blocks.CACTUS_FLOWER.getDefaultState(), SET_FLAGS);
            }
        }
    }

    // Place at least one firefly bush on a grass block adjacent to (but outside) the
    // river arena grid. Scans the border ring for a grass block with air above.
    private static void placeRiverFireflyBush(ServerWorld world, int ox, int oy, int oz,
                                                 int w, int h, Random rng) {
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        int pad = 3;
        for (int x = -pad; x < w + pad; x++) {
            for (int z = -pad; z < h + pad; z++) {
                if (x >= 0 && x < w && z >= 0 && z < h) continue;
                BlockPos floorPos = new BlockPos(ox + x, oy, oz + z);
                if (!world.getBlockState(floorPos).isOf(Blocks.GRASS_BLOCK)) continue;
                if (!world.getBlockState(floorPos.up()).isAir()) continue;
                candidates.add(floorPos.up());
            }
        }
        if (candidates.isEmpty()) return;
        BlockPos pick = candidates.get(rng.nextInt(candidates.size()));
        world.setBlockState(pick, Blocks.FIREFLY_BUSH.getDefaultState(), SET_FLAGS);
    }
    *///?}

    /**
     * Place connected pit obstacles for mountain biomes.
     * Each pit: floor replaced with air, Y-1 replaced with air, Y-2 = black concrete.
     * Pits grow outward from a seed tile so they form a natural-looking cluster.
     */
    private static void placePitObstacles(ServerWorld world, int ox, int oy, int oz,
                                            int w, int h, int min, int max, Random rng) {
        int pitCount = min + rng.nextInt(max - min + 1);
        if (pitCount <= 0) return;

        // Candidate window: a 1-tile edge margin (z starts at 2 so the front player-spawn
        // row stays clear), matching placePowderSnowPatch. The old hardcoded 3-tile margin
        // collapsed the whole allowed range to the central 3x3 on the mountain's ~9x9 grids,
        // so every pit landed dead center. A 1-tile margin lets pits reach the whole board.
        final int minX = 1, maxX = w - 1;   // exclusive upper bound
        final int minZ = 2, maxZ = h - 1;
        if (minX >= maxX || minZ >= maxZ) return;

        // Split the pit budget across 2-3 independent clumps so voids scatter across the
        // board as separate clusters instead of one centered blob. Never ask for more
        // clumps than we can seed (>= 2 tiles each), and clamp to the tile budget.
        int clumps = Math.max(1, Math.min(1 + rng.nextInt(3), Math.max(1, pitCount / 2)));

        java.util.Set<Long> pitTiles = new java.util.LinkedHashSet<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int remaining = pitCount;

        for (int c = 0; c < clumps && remaining > 0; c++) {
            // Distribute the remaining budget evenly; the last clump takes whatever is left.
            int clumpSize = (c == clumps - 1) ? remaining : Math.max(1, remaining / (clumps - c));

            // Pick a fresh random seed on solid ground that isn't already carved.
            List<int[]> seedCandidates = new ArrayList<>();
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (!pitTiles.contains(packPos(x, z))) seedCandidates.add(new int[]{x, z});
                }
            }
            if (seedCandidates.isEmpty()) break;
            java.util.Collections.shuffle(seedCandidates, rng);

            int[] seed = null;
            for (int[] cand : seedCandidates) {
                BlockPos fp = new BlockPos(ox + cand[0], oy, oz + cand[1]);
                BlockState fs = world.getBlockState(fp);
                if (!isAirLike(fs) && fs.getFluidState().isEmpty()) { seed = cand; break; }
            }
            if (seed == null) break;

            // Grow this clump outward from its seed using BFS.
            java.util.Set<Long> clump = new java.util.LinkedHashSet<>();
            java.util.Queue<int[]> frontier = new java.util.LinkedList<>();
            clump.add(packPos(seed[0], seed[1]));
            frontier.add(seed);

            while (clump.size() < clumpSize && !frontier.isEmpty()) {
                int[] current = frontier.poll();
                // Shuffle directions for organic growth
                List<int[]> shuffledDirs = new ArrayList<>(java.util.Arrays.asList(dirs));
                java.util.Collections.shuffle(shuffledDirs, rng);

                for (int[] d : shuffledDirs) {
                    if (clump.size() >= clumpSize) break;
                    int nx = current[0] + d[0], nz = current[1] + d[1];
                    if (nx < minX || nx >= maxX || nz < minZ || nz >= maxZ) continue;
                    long key = packPos(nx, nz);
                    if (clump.contains(key) || pitTiles.contains(key)) continue;

                    BlockPos fp = new BlockPos(ox + nx, oy, oz + nz);
                    BlockState fs = world.getBlockState(fp);
                    if (isAirLike(fs) || !fs.getFluidState().isEmpty()) continue;

                    clump.add(key);
                    frontier.add(new int[]{nx, nz});
                }
            }

            pitTiles.addAll(clump);
            remaining -= clump.size();
        }
        if (pitTiles.isEmpty()) return;

        // Carve each pit tile: floor=air, Y-1=air, Y-2=black concrete
        for (long packed : pitTiles) {
            int px = unpackX(packed), pz = unpackZ(packed);
            world.setBlockState(new BlockPos(ox + px, oy, oz + pz),
                Blocks.AIR.getDefaultState(), SET_FLAGS);
            world.setBlockState(new BlockPos(ox + px, oy - 1, oz + pz),
                Blocks.AIR.getDefaultState(), SET_FLAGS);
            world.setBlockState(new BlockPos(ox + px, oy - 2, oz + pz),
                Blocks.BLACK_CONCRETE.getDefaultState(), SET_FLAGS);
        }
    }

    /**
     * Place a connected cluster of powder snow tiles for snowy biomes.
     * Same BFS growth as pit obstacles but replaces floor blocks with powder snow.
     */
    private static void placePowderSnowPatch(ServerWorld world, int ox, int oy, int oz,
                                               int w, int h, Random rng) {
        int count = 2 + rng.nextInt(11); // 2-12

        // Use a 1-block margin so the patch can fill more of the arena
        List<int[]> seedCandidates = new ArrayList<>();
        for (int x = 1; x < w - 1; x++) {
            for (int z = 2; z < h - 1; z++) { // z=2 to avoid player spawn row
                seedCandidates.add(new int[]{x, z});
            }
        }
        if (seedCandidates.isEmpty()) return;
        java.util.Collections.shuffle(seedCandidates, rng);

        int[] seed = null;
        for (int[] c : seedCandidates) {
            BlockPos fp = new BlockPos(ox + c[0], oy, oz + c[1]);
            BlockState fs = world.getBlockState(fp);
            if (!isAirLike(fs) && fs.getFluidState().isEmpty()
                && fs.getBlock() != Blocks.POWDER_SNOW && fs.getBlock() != Blocks.LAVA) {
                seed = c;
                break;
            }
        }
        if (seed == null) return;

        java.util.Set<Long> snowTiles = new java.util.LinkedHashSet<>();
        java.util.Queue<int[]> frontier = new java.util.LinkedList<>();
        snowTiles.add(packPos(seed[0], seed[1]));
        frontier.add(seed);

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        while (snowTiles.size() < count && !frontier.isEmpty()) {
            int[] current = frontier.poll();
            List<int[]> shuffledDirs = new ArrayList<>(java.util.Arrays.asList(dirs));
            java.util.Collections.shuffle(shuffledDirs, rng);

            for (int[] d : shuffledDirs) {
                if (snowTiles.size() >= count) break;
                int nx = current[0] + d[0], nz = current[1] + d[1];
                if (nx < 1 || nx >= w - 1 || nz < 2 || nz >= h - 1) continue;
                if (snowTiles.contains(packPos(nx, nz))) continue;

                BlockPos fp = new BlockPos(ox + nx, oy, oz + nz);
                BlockState fs = world.getBlockState(fp);
                if (isAirLike(fs) || !fs.getFluidState().isEmpty()) continue;
                if (fs.getBlock() == Blocks.POWDER_SNOW || fs.getBlock() == Blocks.LAVA) continue;

                snowTiles.add(packPos(nx, nz));
                frontier.add(new int[]{nx, nz});
            }
        }

        for (long packed : snowTiles) {
            int px = unpackX(packed), pz = unpackZ(packed);
            BlockPos below = new BlockPos(ox + px, oy - 1, oz + pz);
            BlockState belowState = world.getBlockState(below);
            if (fallsThroughSupport(belowState)) {
                world.setBlockState(below, Blocks.STONE.getDefaultState(), SET_FLAGS);
            }
            world.setBlockState(new BlockPos(ox + px, oy, oz + pz),
                Blocks.POWDER_SNOW.getDefaultState(), SET_FLAGS);
        }
    }

    private static long packPos(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    private static int unpackX(long packed) { return (int) (packed >> 32); }
    private static int unpackZ(long packed) { return (int) packed; }

    // Border concrete color per biome - falls back to most common tile block
    private static Block getBorderConcreteForBiome(String biomeId, GridTile[][] tiles) {
        return switch (biomeId) {
            case "plains" -> Blocks.LIME_CONCRETE;
            case "forest", "jungle", "river" -> Blocks.GREEN_CONCRETE;
            case "mountain" -> Blocks.LIME_CONCRETE;
            case "snowy" -> Blocks.WHITE_CONCRETE;
            default -> concreteForCommonTileBlock(getMostCommonTileBlock(tiles));
        };
    }

    private static Block getMostCommonTileBlock(GridTile[][] tiles) {
        Map<Block, Integer> counts = new HashMap<>();

        for (int x = 0; x < tiles.length; x++) {
            for (int z = 0; z < tiles[x].length; z++) {
                Block block = tiles[x][z].getBlockType();
                if (block == Blocks.AIR || block == Blocks.WATER || block == Blocks.LAVA) {
                    continue;
                }
                counts.put(block, counts.getOrDefault(block, 0) + 1);
            }
        }

        Block best = Blocks.GRASS_BLOCK;
        int bestCount = -1;
        for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private static Block concreteForCommonTileBlock(Block block) {
        if (block == Blocks.SAND || block == Blocks.SANDSTONE || block == Blocks.END_STONE) {
            return Blocks.YELLOW_CONCRETE;
        }
        if (block == Blocks.RED_SAND || block == Blocks.RED_SANDSTONE || block == Blocks.NETHERRACK
            || block == Blocks.CRIMSON_NYLIUM || block == Blocks.NETHER_BRICKS) {
            return Blocks.RED_CONCRETE;
        }
        if (block == Blocks.BASALT || block == Blocks.BLACKSTONE || block == Blocks.DEEPSLATE
            || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.STONE || block == Blocks.COBBLESTONE
            || block == Blocks.TUFF || block == Blocks.ANDESITE) {
            return Blocks.GRAY_CONCRETE;
        }
        if (block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL || block == Blocks.BROWN_MUSHROOM_BLOCK) {
            return Blocks.BROWN_CONCRETE;
        }
        if (block == Blocks.PURPUR_BLOCK || block == Blocks.PURPUR_PILLAR || block == Blocks.CHORUS_PLANT) {
            return Blocks.PURPLE_CONCRETE;
        }
        if (block == Blocks.WARPED_NYLIUM || block == Blocks.WARPED_WART_BLOCK || block == Blocks.PRISMARINE) {
            return Blocks.CYAN_CONCRETE;
        }
        if (block == Blocks.SNOW_BLOCK || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) {
            return Blocks.WHITE_CONCRETE;
        }
        if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.PODZOL
            || block == Blocks.MOSS_BLOCK) {
            return Blocks.GREEN_CONCRETE;
        }
        return Blocks.LIGHT_GRAY_CONCRETE;
    }

    static void set(ServerWorld world, int x, int y, int z, Block block) {
        world.setBlockState(new BlockPos(x, y, z), block.getDefaultState(), SET_FLAGS);
    }

    static void setIf(ServerWorld world, int x, int y, int z, Block block) {
        BlockPos pos = new BlockPos(x, y, z);
        if (world.getBlockState(pos).isAir()) {
            world.setBlockState(pos, block.getDefaultState(), SET_FLAGS);
        }
    }

    private static boolean isAirLike(BlockState state) {
        Block block = state.getBlock();
        return state.isAir() || block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    /** Support check for floor hazards that must not sit over air-like blocks. */
    private static boolean fallsThroughSupport(BlockState state) {
        return state.isAir() || state.isReplaceable() || state.isLiquid()
            || state.isIn(net.minecraft.registry.tag.BlockTags.FIRE);
    }

    private static GridPos findNearestWalkableTile(GridTile[][] tiles, GridPos requested,
                                                    boolean[][] insideMask) {
        int w = tiles.length;
        int h = w > 0 ? tiles[0].length : 0;
        if (w <= 0 || h <= 0) return new GridPos(0, 0);

        int rx = Math.max(0, Math.min(w - 1, requested.x()));
        int rz = Math.max(0, Math.min(h - 1, requested.z()));
        GridTile requestedTile = tiles[rx][rz];
        boolean requestedInMask = insideMask == null
            || (rx < insideMask.length && rz < insideMask[0].length && insideMask[rx][rz]);
        // A tile being safe to STAND on is not enough - it must also be possible to LEAVE.
        // A one-block pillar ringed by lava or void passes isSafeForSpawn and used to strand
        // the player with no legal move on turn one.
        if (requestedTile != null && requestedTile.isSafeForSpawn() && requestedInMask
                && hasEscapeRoute(tiles, insideMask, rx, rz)) {
            return new GridPos(rx, rz);
        }

        // First pass: the nearest safe tile the player can actually walk off of.
        GridPos best = nearestSafeTile(tiles, insideMask, rx, rz, true);
        // Only if the whole arena is stranded tiles do we accept one without an exit -
        // better to stand somewhere than to fail the level build outright.
        if (best == null) best = nearestSafeTile(tiles, insideMask, rx, rz, false);

        return best != null ? best : new GridPos(rx, rz);
    }

    /** Nearest spawn-safe tile to (rx,rz), optionally requiring a walkable neighbour. */
    private static GridPos nearestSafeTile(GridTile[][] tiles, boolean[][] insideMask,
                                           int rx, int rz, boolean requireEscape) {
        int w = tiles.length;
        int h = w > 0 ? tiles[0].length : 0;
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                GridTile tile = tiles[x][z];
                if (tile == null || !tile.isSafeForSpawn()) continue;
                // Polygon arenas: the player must start inside the drawn shape.
                if (insideMask != null
                    && (x >= insideMask.length || z >= insideMask[0].length || !insideMask[x][z])) {
                    continue;
                }
                if (requireEscape && !hasEscapeRoute(tiles, insideMask, x, z)) continue;
                int dist = Math.abs(x - rx) + Math.abs(z - rz);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new GridPos(x, z);
                }
            }
        }
        return best;
    }

    /**
     * True if at least one of the eight tiles around (x,z) can be walked onto, so a player
     * standing here has somewhere to go. Hazard tiles (lava, fire) don't count as an exit -
     * being able only to step into lava is not an escape.
     */
    private static boolean hasEscapeRoute(GridTile[][] tiles, boolean[][] insideMask, int x, int z) {
        int w = tiles.length;
        int h = w > 0 ? tiles[0].length : 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int nx = x + dx, nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                if (insideMask != null
                    && (nx >= insideMask.length || nz >= insideMask[0].length || !insideMask[nx][nz])) {
                    continue;
                }
                GridTile n = tiles[nx][nz];
                if (n != null && n.isSafeForSpawn()) return true;
            }
        }
        return false;
    }

    private static Block getMostCommonTouchingBlock(ServerWorld world, BlockPos pos, Block fallback) {
        Map<Block, Integer> counts = new HashMap<>();
        for (Direction dir : Direction.values()) {
            Block neighbor = world.getBlockState(pos.offset(dir)).getBlock();
            if (neighbor == Blocks.AIR || neighbor == Blocks.CAVE_AIR || neighbor == Blocks.VOID_AIR) continue;
            if (neighbor == Blocks.DIAMOND_BLOCK || neighbor == Blocks.EMERALD_BLOCK
                || neighbor == Blocks.GOLD_BLOCK || neighbor == Blocks.IRON_BLOCK
                || neighbor == Blocks.COPPER_BLOCK || neighbor == Blocks.COAL_BLOCK) {
                continue;
            }
            // Never count a neighboring corner marker - when an outline places
            // markers side by side, the not-yet-replaced neighbor would win the
            // vote and the "replacement" left a corner block in the world.
            if (neighbor == com.crackedgames.craftics.block.ModBlocks.ARENA_CORNER_BLOCK) continue;
            counts.merge(neighbor, 1, Integer::sum);
        }

        Block best = fallback;
        int bestCount = -1;
        for (Map.Entry<Block, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    private static void relightArena(ServerWorld world, int ox, int minY, int oz, int w, int h, int maxY) {
        int pad = 3;
        int fromY = Math.max(world.getBottomY(), minY);
        //? if <=1.21.1 {
        int toY = Math.min(world.getTopY() - 1, maxY);
        //?} else {
        /*int toY = Math.min(world.getTopYInclusive(), maxY);
        *///?}
        var lighting = world.getLightingProvider();

        for (int x = ox - pad; x < ox + w + pad; x++) {
            for (int z = oz - pad; z < oz + h + pad; z++) {
                for (int y = fromY; y <= toY; y++) {
                    lighting.checkBlock(new BlockPos(x, y, z));
                }
            }
        }
    }

    static boolean isOutside(int x, int z, int w, int h) {
        return x < 0 || x >= w || z < 0 || z >= h;
    }

    static int distFromRect(int x, int z, int x1, int z1, int x2, int z2) {
        return Math.max(
            Math.max(0, Math.max(x1 - x, x - x2)),
            Math.max(0, Math.max(z1 - z, z - z2))
        );
    }

    static int findSurfaceY(ServerWorld world, int wx, int oy, int wz) {
        for (int y = oy + 6; y >= oy - 2; y--) {
            if (!world.getBlockState(new BlockPos(wx, y, wz)).isAir()) return y;
        }
        return oy;
    }

    // Sets the MC biome on arena chunks so grass/fog/sky match the theme
    private static void setBiomeForArena(ServerWorld world, BlockPos origin, int w, int h, String crafticsBiomeId) {
        Identifier mcBiomeId = toMinecraftBiomeId(crafticsBiomeId);
        //? if <=1.21.1 {
        var biomeRegistry = world.getRegistryManager().get(RegistryKeys.BIOME);
        //?} else {
        /*var biomeRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        *///?}
        var biomeKey = RegistryKey.of(RegistryKeys.BIOME, mcBiomeId);
        //? if <=1.21.1 {
        java.util.Optional<? extends RegistryEntry<Biome>> optEntry = biomeRegistry.getEntry(biomeKey);
        //?} else {
        /*java.util.Optional<? extends RegistryEntry<Biome>> optEntry = biomeRegistry.streamEntries()
            .filter(e -> e.getKey().isPresent() && e.getKey().get().equals(biomeKey))
            .map(e -> (RegistryEntry<Biome>) e)
            .findFirst();
        *///?}
        if (optEntry.isEmpty()) {
            CrafticsMod.LOGGER.warn("BiomePainter: biome '{}' not found (craftics id: '{}')", mcBiomeId, crafticsBiomeId);
            return;
        }
        RegistryEntry<Biome> biomeEntry = optEntry.get();

        int ox = origin.getX(), oz = origin.getZ();
        int pad = 3;

        int minCX = (ox - pad) >> 4;
        int maxCX = (ox + w + pad) >> 4;
        int minCZ = (oz - pad) >> 4;
        int maxCZ = (oz + h + pad) >> 4;

        int count = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                var chunk = world.getChunk(cx, cz);
                chunk.populateBiomes((bx, by, bz, noise) -> biomeEntry, null);
                //? if <=1.21.1 {
                chunk.setNeedsSaving(true);
                //?} else {
                /*chunk.markNeedsSaving();
                *///?}
                count++;
            }
        }

        CrafticsMod.LOGGER.info("BiomePainter: painted {} chunks with biome '{}' for arena at {}",
            count, mcBiomeId, origin);
    }

    private static Identifier toMinecraftBiomeId(String crafticsBiomeId) {
        // Pale Garden sub-biome - resolves to vanilla 1.21.4+ biome or the
        // backport mod's biome on 1.21.1, depending on what's loaded.
        if ("forest/pale_garden".equals(crafticsBiomeId)) {
            return com.crackedgames.craftics.compat.palegardenbackport
                .PaleGardenBackportCompat.paleGardenBiomeId();
        }
        return Identifier.of("minecraft", switch (crafticsBiomeId) {
            case "plains"            -> "plains";
            case "forest"            -> "dark_forest";
            case "desert"            -> "desert";
            case "jungle"            -> "jungle";
            case "river"             -> "river";
            case "mountain"          -> "windswept_hills";
            case "snowy"             -> "snowy_plains";
            case "cave"              -> "dripstone_caves";
            case "deep_dark"         -> "deep_dark";
            case "nether_wastes"     -> "nether_wastes";
            case "soul_sand_valley"  -> "soul_sand_valley";
            case "crimson_forest"    -> "crimson_forest";
            case "warped_forest"     -> "warped_forest";
            case "basalt_deltas"     -> "basalt_deltas";
            case "outer_end_islands" -> "end_midlands";
            case "end_city"          -> "end_midlands";
            case "chorus_grove"      -> "end_midlands";
            case "dragons_nest"      -> "the_end";
            default                  -> "plains";
        });
    }

    /**
     * Cap lava depth at the tile so the player can't fall through a hidden
     * multi-block lava column into instant death. If the block directly
     * under the top lava tile is also lava, replace it with stone. The
     * top lava block stays visible so the hazard reads identically; only
     * the depth changes.
     */
    private static void capLavaDepth(net.minecraft.server.world.ServerWorld world,
                                     int wx, int floorY, int wz) {
        net.minecraft.util.math.BlockPos belowPos =
            new net.minecraft.util.math.BlockPos(wx, floorY - 1, wz);
        net.minecraft.block.BlockState below = world.getBlockState(belowPos);
        if (below.getBlock() == Blocks.LAVA
            || !below.getFluidState().isEmpty()
            || below.isAir()) {
            int flags = net.minecraft.block.Block.NOTIFY_LISTENERS
                | net.minecraft.block.Block.FORCE_STATE;
            world.setBlockState(belowPos, Blocks.STONE.getDefaultState(), flags);
        }
    }
}
