package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.block.ModBlocks;
import com.crackedgames.craftics.block.NpcMarkerBlock;
import com.crackedgames.craftics.block.NpcMarkerBlockEntity;
import com.crackedgames.craftics.block.SceneSpawnBlock;
import com.crackedgames.craftics.level.SchemLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a scene schematic, places it at a world origin, and scans the placed
 * volume for {@code scene_spawn}, {@code stand_marker} (corner), and
 * {@code npc_marker} markers, producing a {@link SceneLayout} via
 * {@link SceneLayoutResolver}. After recording them, every marker block is
 * replaced with the most common neighboring block so the markers are invisible
 * in the built scene (same treatment {@code ArenaBuilder} gives arena corners).
 *
 * <p>World-touching glue - verified in-game (no unit test). The booth-pairing
 * logic it delegates to ({@link SceneLayoutResolver}) is unit-tested.
 */
public final class SceneScanner {

    private SceneScanner() {}

    /**
     * Place {@code sceneName}'s schematic at {@code origin} and scan it.
     * Returns {@code null} if the schematic could not be loaded.
     */
    public static SceneLayout buildAndScan(ServerWorld world, String sceneName, BlockPos origin) {
        SchemLoader.SchemData data = loadScene(world, sceneName);
        if (data == null) {
            CrafticsMod.LOGGER.warn("Scene schematic '{}' not found", sceneName);
            return null;
        }
        data.place(world, origin.getX(), origin.getY(), origin.getZ());
        return scan(world, origin, data.width(), data.height(), data.length());
    }

    /**
     * Scan an already-placed scene volume for markers, resolve the layout, and
     * replace every marker block with its most common neighbor so the markers
     * vanish into the surrounding floor.
     */
    /**
     * Horizontal facing of a marker as positive degrees (south=0, west=90, ...).
     * 1.21.4 renamed {@code Direction.asRotation()} to
     * {@code getPositiveHorizontalDegrees()}; both return the same value, so this
     * isolates the single version split for the two scan call sites.
     */
    private static float facingDegrees(Direction facing) {
        //? if <=1.21.3 {
        /*return facing.asRotation();
        *///?} else {
        return facing.getPositiveHorizontalDegrees();
        //?}
    }

    public static SceneLayout scan(ServerWorld world, BlockPos origin, int width, int height, int length) {
        List<RawMarker> markers = new ArrayList<>();
        List<BlockPos> markerPositions = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    BlockPos pos = origin.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isOf(ModBlocks.SCENE_SPAWN_BLOCK)) {
                        markers.add(new RawMarker(RawMarker.Kind.SPAWN,
                            pos.getX(), pos.getY(), pos.getZ(),
                            facingDegrees(state.get(SceneSpawnBlock.FACING)), ""));
                        markerPositions.add(pos);
                    } else if (state.isOf(ModBlocks.STAND_MARKER_BLOCK)) {
                        // Corner marker: no facing, no occupant. Used in pairs.
                        markers.add(new RawMarker(RawMarker.Kind.STAND,
                            pos.getX(), pos.getY(), pos.getZ(), 0f, ""));
                        markerPositions.add(pos);
                    } else if (state.isOf(ModBlocks.NPC_MARKER_BLOCK)) {
                        markers.add(new RawMarker(RawMarker.Kind.NPC,
                            pos.getX(), pos.getY(), pos.getZ(),
                            facingDegrees(state.get(NpcMarkerBlock.FACING)), occupantAt(world, pos)));
                        markerPositions.add(pos);
                    }
                }
            }
        }

        SceneLayoutResolver.Result result = SceneLayoutResolver.resolveWithDiagnostics(markers);
        SceneLayout layout = result.layout();
        if (result.skippedNpcMarkers() > 0) {
            CrafticsMod.LOGGER.warn("Scene at {}: {} NPC marker(s) had no unambiguous stand-corner "
                + "rectangle around them and were skipped (booth not clickable). Check the corner "
                + "markers around each NPC marker.", origin, result.skippedNpcMarkers());
        }

        // Hide the markers: replace each with the most common neighboring block.
        // Block.FORCE_STATE skips per-block client updates; the client gets the
        // full scene chunk data after the build (matching ArenaBuilder's flag).
        for (BlockPos pos : markerPositions) {
            Block replacement = getMostCommonTouchingBlock(world, pos);
            world.setBlockState(pos, replacement.getDefaultState(), Block.FORCE_STATE);
        }

        CrafticsMod.LOGGER.info("Scanned scene at {}: spawn={}, {} booth(s)",
            origin, layout.hasSpawn(), layout.stands().size());
        return layout;
    }

    private static String occupantAt(ServerWorld world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        return (be instanceof NpcMarkerBlockEntity npc) ? npc.getOccupant() : "";
    }

    /**
     * The most common non-air, non-marker block touching {@code pos}, used to blend
     * a removed marker into the surrounding floor. Falls back to stone when there is
     * nothing suitable. Mirrors {@link ArenaBuilder}'s corner-marker cleanup.
     */
    private static Block getMostCommonTouchingBlock(ServerWorld world, BlockPos pos) {
        Map<Block, Integer> counts = new HashMap<>();
        for (Direction dir : Direction.values()) {
            Block neighbor = world.getBlockState(pos.offset(dir)).getBlock();
            if (neighbor == Blocks.AIR || neighbor == Blocks.CAVE_AIR || neighbor == Blocks.VOID_AIR) continue;
            if (neighbor == ModBlocks.SCENE_SPAWN_BLOCK
                || neighbor == ModBlocks.STAND_MARKER_BLOCK
                || neighbor == ModBlocks.NPC_MARKER_BLOCK) {
                continue; // never blend one marker into another
            }
            counts.merge(neighbor, 1, Integer::sum);
        }
        Block best = Blocks.STONE;
        int bestCount = -1;
        for (Map.Entry<Block, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    /** Disk override first (craftics_scenes/<name>.schem), then the bundled resource (data/craftics/scenes/<name>.schem). */
    private static SchemLoader.SchemData loadScene(ServerWorld world, String sceneName) {
        // Disk override: craftics_scenes/<name>.schem next to the run dir.
        Path disk = Path.of("").toAbsolutePath().resolve("craftics_scenes").resolve(sceneName + ".schem");
        SchemLoader.SchemData fromDisk = SchemLoader.load(disk);
        if (fromDisk != null) return fromDisk;

        Identifier id = Identifier.of(CrafticsMod.MOD_ID, "scenes/" + sceneName + ".schem");
        var res = world.getServer().getResourceManager().getResource(id);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().getInputStream()) {
            return SchemLoader.load(in, id.toString());
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("Failed to read bundled scene schematic {}", id, e);
            return null;
        }
    }
}
