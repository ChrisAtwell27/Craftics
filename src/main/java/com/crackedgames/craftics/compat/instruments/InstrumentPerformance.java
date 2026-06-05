package com.crackedgames.craftics.compat.instruments;

import com.crackedgames.craftics.combat.AoeShapes;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure shape resolution for instrument performances (centered on the player tile). */
public final class InstrumentPerformance {

    private InstrumentPerformance() {}

    private static final int SCATTER_COUNT = 6;
    private static final int SCATTER_SPAN = 4;   // +/- tiles from player for scatter rolls

    /**
     * The tiles a performance covers, centered on {@code player}.
     *
     * <p>{@code aim} is the tile the player clicked (or is hovering): directional
     * shapes orient toward it. Currently only {@code CONE} is directional, fanning
     * out from the player toward {@code aim}; every other shape is rotationally
     * symmetric or omnidirectional and ignores {@code aim}. Future directional
     * shapes (e.g. a line/beam) can use {@code aim} the same way. When
     * {@code aim} equals {@code player} (no meaningful direction), directional
     * shapes fall back to a default north facing.
     *
     * <p>For SCATTER the {@code seed} drives the RNG so it is deterministic.
     * EXPANDING_PULSE and STAR return the union of their tiers/arms (the
     * beat-by-beat sequencing is a VFX concern). FULL_ARENA returns empty here;
     * the caller substitutes AoeShapes.allTiles(arena).
     *
     * <p>This is the single source of instrument geometry: the server resolver,
     * the VFX builder, and the client hover preview all call it so the cone they
     * draw, hit, and animate can never disagree.
     */
    public static List<GridPos> shapeTiles(InstrumentDef def, GridPos player, GridPos aim, long seed) {
        return switch (def.shape()) {
            case RING1 -> AoeShapes.ring(player, 1);
            case RING2 -> AoeShapes.ring(player, 2);
            case FILLED_DISC2 -> AoeShapes.filledDisc(player, 2);
            case PLUS -> AoeShapes.plus(player);
            case DIAGONALS -> AoeShapes.diagonals(player, 3);
            case CONE -> AoeShapes.cone(player, facingTile(player, aim), 3);
            case STAR -> flatten(AoeShapes.starArms(player, 3));
            case EXPANDING_PULSE -> flatten(AoeShapes.expandingRingTiers(player, 3));
            case SCATTER -> scatter(player, seed);
            case FULL_ARENA -> List.of();
        };
    }

    /**
     * A tile one step from {@code player} toward {@code aim}, snapped to a cardinal
     * direction (AoeShapes.cone derives its facing from player->target via
     * cardinalDir, so any tile along the chosen cardinal works). Falls back to one
     * tile north when {@code aim} is null or equals {@code player}.
     */
    private static GridPos facingTile(GridPos player, GridPos aim) {
        if (aim == null || (aim.x() == player.x() && aim.z() == player.z())) {
            return new GridPos(player.x(), player.z() - 1); // default: north
        }
        int[] dir = AoeShapes.cardinalDir(player, aim);
        return new GridPos(player.x() + dir[0], player.z() + dir[1]);
    }

    private static List<GridPos> flatten(List<List<GridPos>> groups) {
        List<GridPos> out = new ArrayList<>();
        for (List<GridPos> g : groups) out.addAll(g);
        return out;
    }

    private static List<GridPos> scatter(GridPos player, long seed) {
        Random rng = new Random(seed);
        List<GridPos> out = new ArrayList<>(SCATTER_COUNT);
        for (int i = 0; i < SCATTER_COUNT; i++) {
            int dx = rng.nextInt(SCATTER_SPAN * 2 + 1) - SCATTER_SPAN;
            int dz = rng.nextInt(SCATTER_SPAN * 2 + 1) - SCATTER_SPAN;
            out.add(new GridPos(player.x() + dx, player.z() + dz));
        }
        return out;
    }
}
