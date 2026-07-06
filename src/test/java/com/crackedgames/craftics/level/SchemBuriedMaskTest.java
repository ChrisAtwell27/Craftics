package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down {@link SchemLoader#isExposedAt} - the single source of truth for
 * "what the visibility cull skips". The home-island hollow bug was this mask
 * applied to a solid structure; the {@code /craftics world repairhollow} fill
 * and the {@code undorepair} removal both derive their target set from the
 * SAME mask, so these tests guarantee the repair and its undo are exact
 * inverses over exactly the interior the cull skipped.
 *
 * <p>Pure int[]/boolean[] geometry - no Minecraft bootstrap needed.
 */
class SchemBuriedMaskTest {

    /** palette id 0 = air (never hides), id 1 = opaque full cube. */
    private static final boolean[] HIDES = {false, true};

    private static int flat(int w, int l, int x, int y, int z) {
        return ((y * l) + z) * w + x;
    }

    /** A w×h×l volume filled entirely with palette id {@code fill}. */
    private static int[] filled(int w, int h, int l, int fill) {
        int[] ids = new int[w * h * l];
        java.util.Arrays.fill(ids, fill);
        return ids;
    }

    private static int countBuried(int[] ids, boolean[] hides, int w, int h, int l) {
        int buried = 0;
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    if (ids[flat(w, l, x, y, z)] == 0) continue; // air is never buried
                    if (!SchemLoader.isExposedAt(ids, hides, w, h, l, x, y, z)) buried++;
                }
            }
        }
        return buried;
    }

    @Test
    void solidCubeBuriesExactlyTheInterior() {
        // 5x5x5 solid: the 3x3x3 core (27 blocks) is buried, the shell (98) exposed.
        int[] ids = filled(5, 5, 5, 1);
        assertEquals(27, countBuried(ids, HIDES, 5, 5, 5));
        assertFalse(SchemLoader.isExposedAt(ids, HIDES, 5, 5, 5, 2, 2, 2)); // dead center
        assertTrue(SchemLoader.isExposedAt(ids, HIDES, 5, 5, 5, 0, 2, 2));  // shell face
        assertTrue(SchemLoader.isExposedAt(ids, HIDES, 5, 5, 5, 2, 0, 2));  // bottom face
        assertTrue(SchemLoader.isExposedAt(ids, HIDES, 5, 5, 5, 2, 4, 2));  // top face
    }

    @Test
    void interiorCavityKeepsItsWallsExposed() {
        // Hollow out the center: its 6 neighbors gain an open face and must be
        // kept - this is what preserves interior rooms in arena schematics.
        int[] ids = filled(5, 5, 5, 1);
        ids[flat(5, 5, 2, 2, 2)] = 0;
        assertTrue(SchemLoader.isExposedAt(ids, HIDES, 5, 5, 5, 1, 2, 2));
        assertTrue(SchemLoader.isExposedAt(ids, HIDES, 5, 5, 5, 2, 1, 2));
        assertTrue(SchemLoader.isExposedAt(ids, HIDES, 5, 5, 5, 2, 2, 3));
        // 27 core - the air block itself - its 6 now-exposed walls = 20 buried.
        assertEquals(20, countBuried(ids, HIDES, 5, 5, 5));
    }

    @Test
    void thinSlabHasNoBuriedBlocks() {
        // 1-block-thick floating slab: out-of-bounds above AND below count as
        // open (the home island floats - its underside must exist).
        int[] ids = filled(5, 1, 5, 1);
        assertEquals(0, countBuried(ids, HIDES, 5, 1, 5));
    }

    @Test
    void nonHidingNeighborsNeverBury() {
        // A palette that doesn't hide faces (glass, stairs, water) buries nothing.
        int[] ids = filled(5, 5, 5, 1);
        boolean[] noneHide = {false, false};
        assertEquals(0, countBuried(ids, noneHide, 5, 5, 5));
    }

    @Test
    void outOfPaletteIdsCountAsOpen() {
        // Defensive: a corrupt id must read as an open face, not crash or hide.
        int[] ids = filled(3, 3, 3, 1);
        ids[flat(3, 3, 1, 1, 0)] = 99; // bogus palette id next to the center column
        assertTrue(SchemLoader.isExposedAt(ids, HIDES, 3, 3, 3, 1, 1, 1));
    }
}
