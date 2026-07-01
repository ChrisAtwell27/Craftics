package com.crackedgames.craftics.scene;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a small code-defined merchant scene (no .schem needed) for Stage 1. Splits the
 * pure layout math ({@link #buildLayout}) from world block placement ({@link #place}) so the
 * layout is unit-testable without a Minecraft bootstrap. A user-authored
 * craftics_scenes/&lt;name&gt;.schem still overrides this via SceneScanner when present.
 */
public final class CodeSceneBuilder {
    private CodeSceneBuilder() {}

    public static final int VILLAGE_BOOTHS = 3;
    public static final int BARTER_BOOTHS = 3;

    // Scene footprint: a straight walkway along +x with booths on the +z side.
    // Public so SceneController can carry the footprint to the client (SceneStatePayload)
    // for TileRaycast's grid bounds.
    public static final int FLOOR_WIDTH = 16;   // x span
    public static final int FLOOR_DEPTH = 8;    // z span
    private static final int BOOTH_SPACING = 4;  // x distance between booth anchors
    private static final int BOOTH_FIRST_X = 3;  // x offset of the first booth from origin

    /** Pure: compute the scene's spawn pose + booth slots for {@code sceneName}. No world access. */
    public static SceneLayout buildLayout(int ox, int oy, int oz, String sceneName) {
        int count = switch (sceneName) {
            case "village" -> VILLAGE_BOOTHS;
            case "barter_station" -> BARTER_BOOTHS;
            default -> 0;
        };
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
        int spawnX = ox + FLOOR_WIDTH / 2;
        int spawnZ = oz + 1;
        return new SceneLayout(spawnX, oy, spawnZ, 0f, stands);
    }

    /** Place floor + simple booth blocks for {@code layout}, recording overwritten states into
     *  {@code snapshot} so the scene can be restored on leave. */
    public static void place(ServerWorld world, BlockPos origin, SceneLayout layout,
                             Map<BlockPos, BlockState> snapshot) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        // Floor slab of stone bricks across the footprint.
        for (int dx = 0; dx < FLOOR_WIDTH; dx++) {
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
}
