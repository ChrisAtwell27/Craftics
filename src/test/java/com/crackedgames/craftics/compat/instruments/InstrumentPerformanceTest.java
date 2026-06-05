package com.crackedgames.craftics.compat.instruments;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentPerformanceTest {

    private static InstrumentDef def(InstrumentDef.Shape shape) {
        return new InstrumentDef("evenmoreinstruments", "x", InstrumentDef.Role.ATTACK, shape, 2,
            List.of(), 5, InstrumentDef.Signature.NONE, List.of(), 0xFFFFFF);
    }

    private static final GridPos CENTER = new GridPos(5, 5);

    @Test
    void ring1_shapeTilesAreEightNeighbors() {
        List<GridPos> tiles = InstrumentPerformance.shapeTiles(def(InstrumentDef.Shape.RING1), CENTER, CENTER, 0L);
        assertEquals(8, tiles.size());
        assertTrue(tiles.contains(new GridPos(4, 4)));
        assertFalse(tiles.contains(CENTER), "center excluded for a ring");
    }

    @Test
    void filledDisc2_shapeTilesAre25() {
        List<GridPos> tiles = InstrumentPerformance.shapeTiles(def(InstrumentDef.Shape.FILLED_DISC2), new GridPos(0, 0), new GridPos(0, 0), 0L);
        assertEquals(25, tiles.size());
    }

    @Test
    void fullArena_returnsEmptyFromPureShape() {
        List<GridPos> tiles = InstrumentPerformance.shapeTiles(def(InstrumentDef.Shape.FULL_ARENA), new GridPos(0, 0), new GridPos(0, 0), 0L);
        assertTrue(tiles.isEmpty(), "FULL_ARENA is substituted by the caller with allTiles(arena)");
    }

    @Test
    void scatter_isDeterministicForSameSeed() {
        var d = def(InstrumentDef.Shape.SCATTER);
        List<GridPos> a = InstrumentPerformance.shapeTiles(d, new GridPos(0, 0), new GridPos(0, 0), 1234L);
        List<GridPos> b = InstrumentPerformance.shapeTiles(d, new GridPos(0, 0), new GridPos(0, 0), 1234L);
        assertEquals(a, b, "same seed -> same scatter tiles");
    }

    @Test
    void cone_facesAimEast() {
        // Aim to the east (+x) of the center: the cone must extend east, not north.
        GridPos aimEast = new GridPos(CENTER.x() + 3, CENTER.z());
        List<GridPos> tiles = InstrumentPerformance.shapeTiles(def(InstrumentDef.Shape.CONE), CENTER, aimEast, 0L);
        assertFalse(tiles.isEmpty());
        // Every cone tile is on the +x side of the player (east-facing), none to the west or due-north.
        for (GridPos t : tiles) {
            assertTrue(t.x() > CENTER.x(), "cone tile " + t + " should be east of center");
        }
        assertTrue(tiles.contains(new GridPos(CENTER.x() + 1, CENTER.z())), "front-center tile of an east cone");
    }

    @Test
    void cone_facesAimSouth() {
        // Aim to the south (+z): the cone must extend south.
        GridPos aimSouth = new GridPos(CENTER.x(), CENTER.z() + 3);
        List<GridPos> tiles = InstrumentPerformance.shapeTiles(def(InstrumentDef.Shape.CONE), CENTER, aimSouth, 0L);
        assertFalse(tiles.isEmpty());
        for (GridPos t : tiles) {
            assertTrue(t.z() > CENTER.z(), "cone tile " + t + " should be south of center");
        }
        assertTrue(tiles.contains(new GridPos(CENTER.x(), CENTER.z() + 1)), "front-center tile of a south cone");
    }

    @Test
    void cone_aimEqualsCenter_stillProducesACone() {
        // Degenerate aim (aim == center): must not throw or return empty; falls back to a default facing.
        List<GridPos> tiles = InstrumentPerformance.shapeTiles(def(InstrumentDef.Shape.CONE), CENTER, CENTER, 0L);
        assertFalse(tiles.isEmpty(), "aim==center should fall back to a default-facing cone, not empty");
    }
}
