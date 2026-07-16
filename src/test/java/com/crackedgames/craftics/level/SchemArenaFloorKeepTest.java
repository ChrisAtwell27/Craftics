package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down {@link SchemLoader#computeKeepMask} - the visibility cull's
 * keep/cull decision, including the arena-floor exemption.
 *
 * <p>Polygon arenas mark their outline with 3+ {@code craftics:arena_corner}
 * blocks sitting AT floor level ({@code ArenaBuilder.processPolygonStructure}
 * takes {@code arenaFloorY = corners.get(0).getY()}). The corner markers
 * themselves survive the cull via the exempt clause, but the ground layer
 * directly beneath the floor is fully buried - culling it drops the arena
 * floor into the void. Legacy rectangle arenas never hit this: their floor
 * sits at Y=1-2 against the volume's bottom edge, where out-of-bounds counts
 * as an open face.
 *
 * <p>Pure int[]/boolean[] geometry - no Minecraft bootstrap needed.
 */
class SchemArenaFloorKeepTest {

    /** palette id 0 = air, 1 = opaque full cube (stone), 2 = arena corner marker. */
    private static final boolean[] AIR = {true, false, false};
    private static final boolean[] HIDES = {false, true, true};
    private static final boolean[] EXEMPT = {false, false, true};
    private static final boolean[] CORNER = {false, false, true};

    private static int flat(int w, int l, int x, int y, int z) {
        return ((y * l) + z) * w + x;
    }

    private static int[] filled(int w, int h, int l, int fill) {
        int[] ids = new int[w * h * l];
        java.util.Arrays.fill(ids, fill);
        return ids;
    }

    /**
     * A 9x9x9 solid stone block with 4 corner markers at Y=4 forming a
     * bbox over x/z 2..6 - a miniature of desert/3 (corners at Y=13, deep
     * inside a 37x48x36 volume).
     */
    private static int[] arenaVolume() {
        int[] ids = filled(9, 9, 9, 1);
        ids[flat(9, 9, 2, 4, 2)] = 2;
        ids[flat(9, 9, 6, 4, 2)] = 2;
        ids[flat(9, 9, 2, 4, 6)] = 2;
        ids[flat(9, 9, 6, 4, 6)] = 2;
        return ids;
    }

    @Test
    void supportLayerUnderArenaFloorIsKept() {
        // The regression: (4,3,4) is fully buried stone directly under the
        // playable floor, inside the corner footprint. The cull deleted it and
        // the player saw void through the arena floor.
        boolean[] keep = SchemLoader.computeKeepMask(
            arenaVolume(), AIR, HIDES, EXEMPT, CORNER, 9, 9, 9, true);
        assertTrue(keep[flat(9, 9, 4, 3, 4)], "support layer under arena floor must be placed");
        assertTrue(keep[flat(9, 9, 4, 4, 4)], "arena floor tile itself must be placed");
    }

    @Test
    void wholeFootprintOfBothFloorLayersIsKept() {
        // Every tile of the corners' bbox at floor and floor-1, not just the
        // center: a partial keep still leaves holes in the arena.
        boolean[] keep = SchemLoader.computeKeepMask(
            arenaVolume(), AIR, HIDES, EXEMPT, CORNER, 9, 9, 9, true);
        for (int z = 2; z <= 6; z++) {
            for (int x = 2; x <= 6; x++) {
                assertTrue(keep[flat(9, 9, x, 4, z)], "floor tile " + x + "," + z);
                assertTrue(keep[flat(9, 9, x, 3, z)], "support tile " + x + "," + z);
            }
        }
    }

    @Test
    void buriedBlocksAwayFromTheArenaFloorAreStillCulled() {
        // The optimization survives: the exemption is scoped to two Y levels
        // inside the footprint, not the whole schematic.
        boolean[] keep = SchemLoader.computeKeepMask(
            arenaVolume(), AIR, HIDES, EXEMPT, CORNER, 9, 9, 9, true);
        assertFalse(keep[flat(9, 9, 4, 7, 4)], "buried block above the arena is culled");
        assertFalse(keep[flat(9, 9, 4, 1, 4)], "buried block well below the arena is culled");
        assertFalse(keep[flat(9, 9, 4, 2, 4)], "buried block two layers under the floor is culled");
    }

    @Test
    void footprintIsLimitedToTheCornersBoundingBox() {
        // Outside the corners' bbox the support layer is buried filler again.
        // x=1/z=1 rather than the volume's edge: an edge cell borders
        // out-of-bounds and is exposed on its own merits, proving nothing.
        boolean[] keep = SchemLoader.computeKeepMask(
            arenaVolume(), AIR, HIDES, EXEMPT, CORNER, 9, 9, 9, true);
        assertFalse(keep[flat(9, 9, 1, 3, 4)], "x outside the corner bbox");
        assertFalse(keep[flat(9, 9, 4, 3, 1)], "z outside the corner bbox");
    }

    @Test
    void fewerThanThreeCornersTakesTheUnchangedPath() {
        // Rectangle arenas (DIAMOND/EMERALD, zero corner markers) and any
        // schematic with a stray marker must cull exactly as before.
        int[] noCorners = filled(9, 9, 9, 1);
        int[] twoCorners = filled(9, 9, 9, 1);
        twoCorners[flat(9, 9, 2, 4, 2)] = 2;
        twoCorners[flat(9, 9, 6, 4, 6)] = 2;

        boolean[] baseline = SchemLoader.computeKeepMask(
            noCorners, AIR, HIDES, EXEMPT, CORNER, 9, 9, 9, true);
        assertFalse(baseline[flat(9, 9, 4, 3, 4)], "no corners: buried filler still culled");

        boolean[] twoKeep = SchemLoader.computeKeepMask(
            twoCorners, AIR, HIDES, EXEMPT, CORNER, 9, 9, 9, true);
        assertFalse(twoKeep[flat(9, 9, 4, 3, 4)], "2 corners is not a polygon arena");
        // Only the two marker cells differ from the no-corner baseline.
        for (int i = 0; i < baseline.length; i++) {
            if (i == flat(9, 9, 2, 4, 2) || i == flat(9, 9, 6, 4, 6)) continue;
            assertEqualsAt(baseline[i], twoKeep[i], i);
        }
    }

    @Test
    void cullDisabledKeepsEverythingSolid() {
        // cullBuried = false (the home island) is untouched by the exemption.
        boolean[] keep = SchemLoader.computeKeepMask(
            arenaVolume(), AIR, HIDES, EXEMPT, CORNER, 9, 9, 9, false);
        boolean[] expected = new boolean[9 * 9 * 9];
        java.util.Arrays.fill(expected, true);
        assertArrayEquals(expected, keep);
    }

    @Test
    void airIsNeverKept() {
        // Air is placed unconditionally by place()'s pass 1, never via keep[].
        int[] ids = arenaVolume();
        ids[flat(9, 9, 4, 3, 4)] = 0;
        boolean[] keep = SchemLoader.computeKeepMask(ids, AIR, HIDES, EXEMPT, CORNER, 9, 9, 9, true);
        assertFalse(keep[flat(9, 9, 4, 3, 4)], "air never enters keep[]");
    }

    private static void assertEqualsAt(boolean expected, boolean actual, int flat) {
        assertTrue(expected == actual, "keep[] diverged at flat index " + flat);
    }
}
