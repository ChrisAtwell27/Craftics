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
 * Marker blocks (must be on the same Y level):
 *   DIAMOND_BLOCK  = camera corner (outside the playable grid)
 *   EMERALD_BLOCK  = opposite corner
 *   Playable area is the rectangle one block inside those markers.
 *   Border ring gets replaced with biome-themed concrete at runtime.
 *
 * Optional spawn markers (inside the playable area):
 *   GOLD_BLOCK / IRON_BLOCK / COPPER_BLOCK / COAL_BLOCK = P1..P4
 *   If GOLD_BLOCK is missing, P1 spawn is auto-derived from arena geometry.
 */
public class ArenaBuilder {

    // FORCE_STATE skips per-block client updates — client gets full chunk data after build
    static final int SET_FLAGS = Block.FORCE_STATE;

    private static float pendingCameraYaw = -1;
    private static BlockPos structureOrigin = null;
    private static GridPos structurePlayerStart = null;
    private static int structureGridW = -1, structureGridH = -1;
    private static int structureHeight = -1;

    /** Get and clear the pending camera yaw from the last structure load */
    public static float consumePendingCameraYaw() {
        float yaw = pendingCameraYaw;
        pendingCameraYaw = -1;
        return yaw;
    }

    public static GridArena build(ServerWorld world, LevelDefinition levelDef) {
        int level = levelDef.getLevelNumber();
        BlockPos origin = GridArena.arenaOriginForLevel(level);
        return buildAt(world, levelDef, origin);
    }

    public static GridArena build(ServerWorld world, LevelDefinition levelDef, java.util.UUID worldOwner) {
        com.crackedgames.craftics.world.CrafticsSavedData data =
            com.crackedgames.craftics.world.CrafticsSavedData.get(world);
        BlockPos origin = data.getArenaOrigin(worldOwner, levelDef.getLevelNumber());
        if (origin == null) {
            origin = GridArena.arenaOriginForLevel(levelDef.getLevelNumber());
        }
        return buildAt(world, levelDef, origin);
    }

    private static GridArena buildAt(ServerWorld world, LevelDefinition levelDef, BlockPos origin) {
        int level = levelDef.getLevelNumber();
        int w = levelDef.getWidth();
        int h = levelDef.getHeight();
        GridTile[][] tiles = levelDef.buildTiles();

        CrafticsMod.LOGGER.info("Building arena for Level {} '{}' at {} ({}x{})",
            level, levelDef.getName(), origin, w, h);

        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        Random rng = new Random(level * 31L + ox);

        // Resolve biome for schematic lookup + environment theming
        String biomeId = "plains";
        boolean isBoss = false;
        EnvironmentStyle envStyle = EnvironmentStyle.PLAINS;
        if (levelDef instanceof GeneratedLevelDefinition gld) {
            BiomeTemplate bt = gld.getBiomeTemplate();
            if (bt != null) {
                biomeId = bt.biomeId;
                isBoss = bt.isBossLevel(gld.getLevelNumber());
                if (bt.environmentStyle != null) envStyle = bt.environmentStyle;
            }
        }

        String arenaBiomeOverride = levelDef.getArenaBiomeId();
        if (arenaBiomeOverride != null && !arenaBiomeOverride.isBlank()) {
            biomeId = arenaBiomeOverride;
            isBoss = false;
        }

        // Trial chamber events need their own schematic folder
        String levelName = levelDef.getName();
        if (levelName != null && levelName.toLowerCase(java.util.Locale.ROOT).contains("trial chamber")) {
            biomeId = "trial_chamber";
            isBoss = false;
        }

        // Reset per-build state
        structureOrigin = null;
        structurePlayerStart = null;
        structureGridW = -1;
        structureGridH = -1;
        structureHeight = -1;

        CrafticsMod.LOGGER.info("ArenaBuilder: resolved biomeId='{}' isBoss={} envStyle={} levelDef={} (instanceof GLD: {})",
            biomeId, isBoss, envStyle, levelDef.getClass().getSimpleName(),
            levelDef instanceof GeneratedLevelDefinition);

        // Try structure preset, fall back to procedural
        boolean structureLoaded = tryLoadStructure(world, ox, oy, oz, w, h, tiles, biomeId, isBoss, rng);

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

        // Biome-themed light posts around the border
        placeLighting(world, floorX, floorY, floorZ, finalW, finalH, envStyle);

        // FORCE_STATE can leave stale light — recheck arena bounds
        int relightTop = floorY + Math.max(12, structureHeight > 0 ? structureHeight + 3 : 12);
        relightArena(world, floorX, floorY - 3, floorZ, finalW, finalH, relightTop);

        // Force-load chunks so client has blocks before teleport
        int minCX = (finalOrigin.getX() - 2) >> 4;
        int maxCX = (finalOrigin.getX() + finalW + 2) >> 4;
        int minCZ = (finalOrigin.getZ() - 2) >> 4;
        int maxCZ = (finalOrigin.getZ() + finalH + 2) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.setChunkForced(cx, cz, true);
            }
        }

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

        // Auto-detect obstacles/voids from placed schematic terrain
        for (int x = 0; x < finalW; x++) {
            for (int z = 0; z < finalH; z++) {
                GridTile tile = finalTiles[x][z];
                if (tile == null) continue;
                BlockPos floorPos = new BlockPos(floorX + x, floorY, floorZ + z);
                BlockPos abovePos = new BlockPos(floorX + x, floorY + 1, floorZ + z);
                BlockPos headPos = new BlockPos(floorX + x, floorY + 2, floorZ + z);
                net.minecraft.block.BlockState aboveState = world.getBlockState(abovePos);
                net.minecraft.block.BlockState headState = world.getBlockState(headPos);

                boolean hasObstacleBlock = !aboveState.isAir()
                    && !(aboveState.getBlock() instanceof net.minecraft.block.CarpetBlock)
                    && aboveState.isSolidBlock(world, abovePos);
                boolean hasHeadBlock = !headState.isAir()
                    && !(headState.getBlock() instanceof net.minecraft.block.CarpetBlock)
                    && headState.isSolidBlock(world, headPos);

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

                // Water at floor level = WATER tile
                net.minecraft.block.BlockState floorState = world.getBlockState(floorPos);
                if (tile.isWalkable() && !floorState.getFluidState().isEmpty()
                    && floorState.getBlock() == Blocks.WATER) {
                    finalTiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.WATER,
                        Blocks.WATER);
                }

                // No floor = void (air, void_air, or lava at floor level)
                if (finalTiles[x][z].isWalkable()) {
                    Block floorBlock = floorState.getBlock();
                    boolean noFloor = floorState.isAir()
                        || floorBlock == Blocks.VOID_AIR
                        || floorBlock == Blocks.LAVA;
                    if (noFloor) {
                        finalTiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.VOID,
                            Blocks.AIR);
                    }
                }
            }
        }

        // Snap player start to nearest walkable tile after obstacle scan
        GridPos finalPlayerStart = findNearestWalkableTile(finalTiles, requestedPlayerStart);

        CrafticsMod.LOGGER.info("Arena built. origin={}, size={}x{}, playerStart={}", finalOrigin, finalW, finalH, finalPlayerStart);
        return new GridArena(finalW, finalH, finalTiles, finalOrigin, level, finalPlayerStart);
    }

    // Tries .schem on disk first, then bundled .schem in JAR, then .nbt via StructureTemplateManager
    private static boolean tryLoadStructure(ServerWorld world, int ox, int oy, int oz,
                                              int w, int h, GridTile[][] tiles,
                                              String biomeId, boolean isBoss, Random rng) {
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
            }

            for (java.nio.file.Path root : searchRoots) {
                CrafticsMod.LOGGER.debug("Searching for .schem arenas in: {}", root);
                searchSchemFiles(root, biomeId, isBoss, diskSchemCandidates);
            }
        } catch (Exception e) {
            CrafticsMod.LOGGER.debug("Error while searching for .schem arenas: {}", e.getMessage());
        }

        // Disk overrides win
        if (!diskSchemCandidates.isEmpty()) {
            CrafticsMod.LOGGER.info("Found {} disk .schem candidate(s) for biome '{}': {}",
                diskSchemCandidates.size(), biomeId, diskSchemCandidates);
            java.nio.file.Path chosenSchem = diskSchemCandidates.get(rng.nextInt(diskSchemCandidates.size()));
            return loadAndPlaceSchem(world, chosenSchem, ox, oy, oz, w, h, tiles, biomeId, isBoss);
        }

        // 2. Bundled .schem in JAR: data/craftics/arenas/<biome>/<name>.schem
        List<Identifier> bundledCandidates = new ArrayList<>();
        net.minecraft.resource.ResourceManager resourceManager = world.getServer().getResourceManager();

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
            Identifier chosen = bundledCandidates.get(rng.nextInt(bundledCandidates.size()));
            CrafticsMod.LOGGER.info("Loading bundled arena: {} ({} candidates)", chosen, bundledCandidates.size());
            return loadAndPlaceBundledSchem(world, resourceManager, chosen, ox, oy, oz, w, h, tiles, biomeId, isBoss);
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
        Identifier chosen = candidates.get(rng.nextInt(candidates.size()));
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

    // Shared marker processing — diamond+emerald corners define the inner playable grid
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
                }
            }
        }

        boolean hasGold = !goldCandidates.isEmpty();
        boolean hasIron = !ironCandidates.isEmpty();
        boolean hasCopper = !copperCandidates.isEmpty();
        boolean hasCoal = !coalCandidates.isEmpty();
        CrafticsMod.LOGGER.info("Structure {} marker scan: diamond={}, emerald={}, spawns=[{},{},{},{}]",
            sourceName, diamondPos != null, emeraldPos != null,
            hasGold, hasIron, hasCopper, hasCoal);

        if (diamondPos == null || emeraldPos == null) {
            CrafticsMod.LOGGER.warn("Structure {} missing DIAMOND/EMERALD corner markers. Falling back to default overlay.", sourceName);
            overlayArenaTiles(world, ox, oy, oz, w, h, tiles);
            return true;
        }

        if (diamondPos.getY() != emeraldPos.getY()) {
            CrafticsMod.LOGGER.warn("Structure {} has corner markers on different Y levels: diamond={}, emerald={}. Falling back to default overlay.",
                sourceName, diamondPos.getY(), emeraldPos.getY());
            overlayArenaTiles(world, ox, oy, oz, w, h, tiles);
            return true;
        }

        int arenaFloorY = diamondPos.getY();

        int borderMinX = Math.min(diamondPos.getX(), emeraldPos.getX());
        int borderMinZ = Math.min(diamondPos.getZ(), emeraldPos.getZ());
        int borderMaxX = Math.max(diamondPos.getX(), emeraldPos.getX());
        int borderMaxZ = Math.max(diamondPos.getZ(), emeraldPos.getZ());

        int gridMinX = borderMinX + 1;
        int gridMinZ = borderMinZ + 1;
        int gridMaxX = borderMaxX - 1;
        int gridMaxZ = borderMaxZ - 1;
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
        double dx = diamondPos.getX() - centerX;
        double dz = diamondPos.getZ() - centerZ;
        float cameraYaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + 180f;
        if (cameraYaw < 0f) cameraYaw += 360f;
        pendingCameraYaw = cameraYaw;

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

                    // Keep intentional schematic voids at floor level
                    if (world.getBlockState(floorPos).isAir()) {
                        continue;
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

        // Auto P1 spawn if no gold marker — center, one tile in from camera side
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

            // Plain numbered files (1.schem) in the root are skipped — no biome association.
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

        schem.place(world, placeX, placeY, placeZ);
        taperSchemEdges(world, placeX, placeY, placeZ, schem.width(), schem.height(), schem.length());

        return processPlacedStructure(world, placeX, placeY, placeZ,
            schem.width(), schem.height(), schem.length(), ox, oy, oz, w, h, tiles,
            schemPath.getFileName().toString(), biomeId, preserveSchematicGround);
    }

    // Load a bundled .schem from mod resources (JAR)
    private static boolean loadAndPlaceBundledSchem(ServerWorld world,
                                                     net.minecraft.resource.ResourceManager resourceManager,
                                                     Identifier resourceId,
                                                     int ox, int oy, int oz, int w, int h, GridTile[][] tiles,
                                                     String biomeId, boolean isBoss) {
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

            schem.place(world, placeX, placeY, placeZ);
            taperSchemEdges(world, placeX, placeY, placeZ, schem.width(), schem.height(), schem.length());

            return processPlacedStructure(world, placeX, placeY, placeZ,
                schem.width(), schem.height(), schem.length(), ox, oy, oz, w, h, tiles,
                resourceId.getPath(), biomeId, isBoss);
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("Failed to load bundled arena: {}", resourceId, e);
            return false;
        }
    }

    // Taper schematic edges so terrain doesn't look sliced, fill ground so nothing floats
    private static void taperSchemEdges(ServerWorld world, int px, int py, int pz,
                                         int sizeX, int sizeY, int sizeZ) {
        int fade = 4;

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int distX = Math.min(x, sizeX - 1 - x);
                int distZ = Math.min(z, sizeZ - 1 - z);
                int dist = Math.min(distX, distZ);

                if (dist < fade) {
                    // Fade zone: keep fewer blocks as you approach the edge
                    int maxKeepY = py + dist * 2;
                    for (int y = maxKeepY + 1; y < py + sizeY; y++) {
                        BlockPos bp = new BlockPos(px + x, y, pz + z);
                        if (!world.getBlockState(bp).isAir()) {
                            world.setBlockState(bp, Blocks.AIR.getDefaultState(), SET_FLAGS);
                        }
                    }
                }

                // Fill stone under any remaining non-air column so nothing floats
                boolean foundSolid = false;
                for (int y = py + sizeY - 1; y >= py; y--) {
                    if (!world.getBlockState(new BlockPos(px + x, y, pz + z)).isAir()) {
                        foundSolid = true;
                    }
                    if (foundSolid && y < py && world.getBlockState(new BlockPos(px + x, y, pz + z)).isAir()) {
                        world.setBlockState(new BlockPos(px + x, y, pz + z),
                            Blocks.STONE.getDefaultState(), SET_FLAGS);
                    }
                }
            }
        }
    }

    // Barrier box around schematic — floor + walls, no ceiling
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

    // Light posts around the arena border, themed per biome
    private static void placeLighting(ServerWorld world, int ox, int oy, int oz,
                                       int w, int h, EnvironmentStyle style) {
        Block postBlock = getPostBlock(style);
        Block lightBlock = getLightBlock(style);

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

        // Base + post + light
        for (int[] p : posts) {
            int px = ox + p[0], pz = oz + p[1];
            setIf(world, px, oy, pz, Blocks.STONE);   // ensure solid base
            set(world, px, oy + 1, pz, postBlock);
            set(world, px, oy + 2, pz, lightBlock);
        }
    }

    private static Block getPostBlock(EnvironmentStyle style) {
        return switch (style) {
            case PLAINS -> Blocks.OAK_FENCE;
            case FOREST -> Blocks.DARK_OAK_FENCE;
            case SNOWY -> Blocks.SPRUCE_FENCE;
            case MOUNTAIN -> Blocks.COBBLESTONE_WALL;
            case RIVER -> Blocks.PRISMARINE_WALL;
            case DESERT -> Blocks.SANDSTONE_WALL;
            case JUNGLE -> Blocks.JUNGLE_FENCE;
            case CAVE -> Blocks.COBBLESTONE_WALL;
            case DEEP_DARK -> Blocks.DEEPSLATE_BRICK_WALL;
            case NETHER -> Blocks.NETHER_BRICK_FENCE;
            case END -> Blocks.PURPUR_PILLAR;
        };
    }

    private static Block getLightBlock(EnvironmentStyle style) {
        return switch (style) {
            case PLAINS, FOREST, MOUNTAIN, DESERT, CAVE -> Blocks.LANTERN;
            case SNOWY, DEEP_DARK, NETHER -> Blocks.SOUL_LANTERN;
            case RIVER -> Blocks.SEA_LANTERN;
            case JUNGLE -> Blocks.SHROOMLIGHT;
            case END -> Blocks.END_ROD;
        };
    }

    // Border concrete color per biome — falls back to most common tile block
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

    private static GridPos findNearestWalkableTile(GridTile[][] tiles, GridPos requested) {
        int w = tiles.length;
        int h = w > 0 ? tiles[0].length : 0;
        if (w <= 0 || h <= 0) return new GridPos(0, 0);

        int rx = Math.max(0, Math.min(w - 1, requested.x()));
        int rz = Math.max(0, Math.min(h - 1, requested.z()));
        GridTile requestedTile = tiles[rx][rz];
        if (requestedTile != null && requestedTile.isWalkable()) {
            return new GridPos(rx, rz);
        }

        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                GridTile tile = tiles[x][z];
                if (tile == null || !tile.isWalkable()) continue;
                int dist = Math.abs(x - rx) + Math.abs(z - rz);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new GridPos(x, z);
                }
            }
        }

        return best != null ? best : new GridPos(rx, rz);
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
        int toY = Math.min(world.getTopY() - 1, maxY);
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
        var biomeRegistry = world.getRegistryManager().get(RegistryKeys.BIOME);
        var biomeKey = RegistryKey.of(RegistryKeys.BIOME, mcBiomeId);
        var optEntry = biomeRegistry.getEntry(biomeKey);
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
                chunk.setNeedsSaving(true);
                count++;
            }
        }

        CrafticsMod.LOGGER.info("BiomePainter: painted {} chunks with biome '{}' for arena at {}",
            count, mcBiomeId, origin);
    }

    private static Identifier toMinecraftBiomeId(String crafticsBiomeId) {
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
}
