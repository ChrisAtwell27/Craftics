package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-geometry tests for {@link AoeShapes}. */
class AoeShapesTest {

    private static Set<GridPos> set(List<GridPos> tiles) {
        return tiles.stream().collect(Collectors.toSet());
    }

    // ---- cardinalDir ----

    @Test
    void cardinalDir_dominantAxisWins() {
        // Mostly east, slightly north → snaps to east.
        assertArrayEquals(new int[]{1, 0}, AoeShapes.cardinalDir(new GridPos(0, 0), new GridPos(3, 1)));
        // Mostly south → snaps to south (+z).
        assertArrayEquals(new int[]{0, 1}, AoeShapes.cardinalDir(new GridPos(0, 0), new GridPos(1, 3)));
        // West.
        assertArrayEquals(new int[]{-1, 0}, AoeShapes.cardinalDir(new GridPos(5, 5), new GridPos(0, 5)));
    }

    @Test
    void cardinalDir_sameTileIsZero() {
        assertArrayEquals(new int[]{0, 0}, AoeShapes.cardinalDir(new GridPos(2, 2), new GridPos(2, 2)));
    }

    // ---- slam3x3 ----

    @Test
    void slam3x3_coversNineTiles() {
        List<GridPos> tiles = AoeShapes.slam3x3(new GridPos(5, 5));
        assertEquals(9, tiles.size());
        assertTrue(tiles.contains(new GridPos(5, 5)));   // center
        assertTrue(tiles.contains(new GridPos(4, 4)));   // corner
        assertTrue(tiles.contains(new GridPos(6, 6)));   // opposite corner
    }

    // ---- plus ----

    @Test
    void plus_centerAndFourCardinals() {
        Set<GridPos> tiles = set(AoeShapes.plus(new GridPos(3, 3)));
        assertEquals(5, tiles.size());
        assertTrue(tiles.contains(new GridPos(3, 3)));
        assertTrue(tiles.contains(new GridPos(4, 3)));
        assertTrue(tiles.contains(new GridPos(2, 3)));
        assertTrue(tiles.contains(new GridPos(3, 4)));
        assertTrue(tiles.contains(new GridPos(3, 2)));
        // No diagonals.
        assertFalse(tiles.contains(new GridPos(4, 4)));
    }

    // ---- sweepingEdge ----

    @Test
    void sweepingEdge_lv1_isThreeWideChop() {
        // Attacking east: target (6,5), player (5,5). Perpendicular axis is z.
        Set<GridPos> tiles = set(AoeShapes.sweepingEdge(new GridPos(5, 5), new GridPos(6, 5), 1));
        assertEquals(3, tiles.size());
        assertTrue(tiles.contains(new GridPos(6, 5)));   // target
        assertTrue(tiles.contains(new GridPos(6, 4)));   // perpendicular
        assertTrue(tiles.contains(new GridPos(6, 6)));   // perpendicular
    }

    @Test
    void sweepingEdge_lv2_isFiveWide() {
        Set<GridPos> tiles = set(AoeShapes.sweepingEdge(new GridPos(5, 5), new GridPos(6, 5), 2));
        assertEquals(5, tiles.size());
        assertTrue(tiles.contains(new GridPos(6, 3)));
        assertTrue(tiles.contains(new GridPos(6, 7)));
    }

    @Test
    void sweepingEdge_lv3_isFullRingAroundPlayer() {
        Set<GridPos> tiles = set(AoeShapes.sweepingEdge(new GridPos(5, 5), new GridPos(6, 5), 3));
        assertEquals(8, tiles.size());
        assertFalse(tiles.contains(new GridPos(5, 5))); // never the player tile
        assertTrue(tiles.contains(new GridPos(4, 4)));
        assertTrue(tiles.contains(new GridPos(6, 6)));
        // Independent of target direction at lv3.
        Set<GridPos> tiles2 = set(AoeShapes.sweepingEdge(new GridPos(5, 5), new GridPos(5, 9), 3));
        assertEquals(tiles, tiles2);
    }

    // ---- cone ----

    @Test
    void cone_widensEachRow() {
        // Attacking east from (5,5), depth 2.
        Set<GridPos> tiles = set(AoeShapes.cone(new GridPos(5, 5), new GridPos(6, 5), 2));
        // Row 1 (depth 1): 3 tiles. Row 2 (depth 2): 5 tiles. Total 8.
        assertEquals(8, tiles.size());
        assertTrue(tiles.contains(new GridPos(6, 5)));   // row1 center
        assertTrue(tiles.contains(new GridPos(6, 4)));   // row1 edge
        assertTrue(tiles.contains(new GridPos(6, 6)));   // row1 edge
        assertTrue(tiles.contains(new GridPos(7, 3)));   // row2 far edge
        assertTrue(tiles.contains(new GridPos(7, 7)));   // row2 far edge
    }

    // ---- ring ----

    @Test
    void ring_radius1IsEightTiles() {
        Set<GridPos> tiles = set(AoeShapes.ring(new GridPos(5, 5), 1));
        assertEquals(8, tiles.size());
        assertFalse(tiles.contains(new GridPos(5, 5))); // hollow center
        assertTrue(tiles.contains(new GridPos(4, 4)));
    }

    @Test
    void ring_radius2IsSixteenTileOutline() {
        Set<GridPos> tiles = set(AoeShapes.ring(new GridPos(5, 5), 2));
        assertEquals(16, tiles.size()); // perimeter of a 5x5 square
        assertTrue(tiles.contains(new GridPos(3, 3)));   // corner
        assertTrue(tiles.contains(new GridPos(5, 3)));   // edge midpoint
        assertFalse(tiles.contains(new GridPos(4, 4)));  // inner (radius 1) excluded
    }

    // ---- fireAspectShape (scales with sweeping edge level) ----

    @Test
    void fireAspectShape_noSweep_isForwardCone() {
        Set<GridPos> a = set(AoeShapes.fireAspectShape(new GridPos(5, 5), new GridPos(6, 5), 1, 0));
        Set<GridPos> b = set(AoeShapes.cone(new GridPos(5, 5), new GridPos(6, 5), 2));
        assertEquals(b, a);
    }

    @Test
    void fireAspectShape_sweep2_isWiderThanPlainCone() {
        Set<GridPos> wide = set(AoeShapes.fireAspectShape(new GridPos(5, 5), new GridPos(6, 5), 1, 2));
        Set<GridPos> plain = set(AoeShapes.cone(new GridPos(5, 5), new GridPos(6, 5), 2));
        assertTrue(wide.size() > plain.size(), "wide cone should cover more tiles");
        // Reaches further onto the sides than the plain cone.
        assertTrue(wide.contains(new GridPos(6, 7)) || wide.contains(new GridPos(5, 6)));
    }

    @Test
    void fireAspectShape_sweep3_isRingsAroundPlayer() {
        Set<GridPos> rings = set(AoeShapes.fireAspectShape(new GridPos(5, 5), new GridPos(6, 5), 1, 3));
        // radius-1 ring (8) + radius-2 ring (16) = 24, none being the player.
        assertEquals(24, rings.size());
        assertFalse(rings.contains(new GridPos(5, 5)));
        assertTrue(rings.contains(new GridPos(4, 4)));   // inner ring
        assertTrue(rings.contains(new GridPos(3, 3)));   // outer ring corner
        // Independent of aim direction (it's centered on the player).
        Set<GridPos> rings2 = set(AoeShapes.fireAspectShape(new GridPos(5, 5), new GridPos(5, 9), 1, 3));
        assertEquals(rings, rings2);
    }

    // ---- lineBehind ----

    @Test
    void lineBehind_excludesTargetExtendsForward() {
        // East attack: target (6,5), should give (7,5),(8,5),(9,5) for length 3.
        List<GridPos> tiles = AoeShapes.lineBehind(new GridPos(5, 5), new GridPos(6, 5), 3);
        assertEquals(List.of(new GridPos(7, 5), new GridPos(8, 5), new GridPos(9, 5)), tiles);
    }

    // ---- lineOutward ----

    @Test
    void lineOutward_includesTarget() {
        List<GridPos> tiles = AoeShapes.lineOutward(new GridPos(5, 5), new GridPos(6, 5), 3);
        assertEquals(List.of(new GridPos(6, 5), new GridPos(7, 5), new GridPos(8, 5)), tiles);
    }

    // ---- pierceBehind ----

    @Test
    void pierceBehind_targetPlusOneBehind() {
        List<GridPos> tiles = AoeShapes.pierceBehind(new GridPos(5, 5), new GridPos(6, 5));
        assertEquals(List.of(new GridPos(6, 5), new GridPos(7, 5)), tiles);
    }
}
