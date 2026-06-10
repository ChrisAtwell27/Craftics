package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.block.ModBlocks;
import com.crackedgames.craftics.block.SceneSpawnBlock;
import com.crackedgames.craftics.block.StandMarkerBlock;
import com.crackedgames.craftics.block.StandMarkerBlockEntity;
import com.crackedgames.craftics.level.SchemLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a scene schematic, places it at a world origin, and scans the placed
 * volume for {@link SceneSpawnBlock} and {@link StandMarkerBlock} markers,
 * producing a {@link SceneLayout} via {@link SceneLayoutResolver}.
 *
 * <p>World-touching glue — verified in-game (no unit test). The classification
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

    /** Scan an already-placed scene volume for markers. */
    public static SceneLayout scan(ServerWorld world, BlockPos origin, int width, int height, int length) {
        List<RawMarker> markers = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    BlockPos pos = origin.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isOf(ModBlocks.SCENE_SPAWN_BLOCK)) {
                        markers.add(new RawMarker(RawMarker.Kind.SPAWN,
                            pos.getX(), pos.getY(), pos.getZ(),
                            state.get(SceneSpawnBlock.FACING).asRotation(), ""));
                    } else if (state.isOf(ModBlocks.STAND_MARKER_BLOCK)) {
                        markers.add(new RawMarker(RawMarker.Kind.STAND,
                            pos.getX(), pos.getY(), pos.getZ(),
                            state.get(StandMarkerBlock.FACING).asRotation(), occupantAt(world, pos)));
                    }
                }
            }
        }
        SceneLayout layout = SceneLayoutResolver.resolve(markers);
        CrafticsMod.LOGGER.info("Scanned scene at {}: spawn={}, {} stands",
            origin, layout.hasSpawn(), layout.stands().size());
        return layout;
    }

    private static String occupantAt(ServerWorld world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        return (be instanceof StandMarkerBlockEntity sm) ? sm.getOccupant() : "";
    }

    /** Bundled resource first (data/craftics/scenes/<name>.schem), then a disk override. */
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
