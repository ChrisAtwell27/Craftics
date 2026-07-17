package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down {@link SchemLoader#computeGravitySupport} - the guarantee that every
 * kept gravity block (sand, gravel, concrete powder, powder snow) has a solid
 * block under it once the arena is placed.
 *
 * <p>The regression this covers: the pass used to scan {@code y >= 1}, so
 * gravity blocks sitting on the volume's bottom edge (y=0) were never
 * considered. An arena's ground runs to that edge, and arenas are hollow
 * underneath, so sand on the floor layer had nothing beneath it and fell into
 * the void on the first block update. Real data: 40 of the shipped arenas carry
 * gravity blocks at y=0 (desert/1 alone has 119).
 *
 * <p>Pure int[]/boolean[] geometry - no Minecraft bootstrap needed.
 */
class SchemGravitySupportTest {

    /** palette id 0 = air, 1 = stone (solid), 2 = sand (gravity), 3 = chest (exempt, fall-through). */
    private static final boolean[] GRAVITY = {false, false, true, false};
    private static final boolean[] FALL_THROUGH = {true, false, false, true};
    private static final boolean[] EXEMPT = {false, false, false, true};
    private static final boolean[] PRESENT = {true, true, true, true};

    private static int flat(int w, int l, int x, int y, int z) {
        return ((y * l) + z) * w + x;
    }

    private record Result(boolean[] keep, int[] synthSource, int[] bottomSynth) {}

    /** Run the pass over a volume where every non-air cell starts out kept. */
    private static Result run(int[] ids, int w, int h, int l) {
        boolean[] keep = new boolean[w * h * l];
        for (int i = 0; i < ids.length; i++) keep[i] = ids[i] != 0;
        int[] synthSource = new int[w * h * l];
        int[] bottomSynth = new int[w * l];
        SchemLoader.computeGravitySupport(ids, GRAVITY, FALL_THROUGH, EXEMPT, PRESENT,
            w, h, l, keep, synthSource, bottomSynth);
        return new Result(keep, synthSource, bottomSynth);
    }

    @Test
    void sandOnTheVolumeFloorGetsASupportBelowTheVolume() {
        // The bug: sand at y=0 has no cell below it inside the volume, so the
        // old pass (y >= 1) skipped it entirely and it fell into the hollow.
        int[] ids = new int[3 * 3 * 3];
        ids[flat(3, 3, 1, 0, 1)] = 2;

        Result r = run(ids, 3, 3, 3);
        assertEquals(flat(3, 3, 1, 0, 1), r.bottomSynth()[1 * 3 + 1],
            "sand on the volume floor must request a support one block below the volume");
    }

    @Test
    void everyKeptGravityBlockEndsUpSupported() {
        // The invariant that matters, asserted over a whole volume rather than
        // one cell: after the pass, each kept gravity block either rests on a
        // kept non-fall-through block, or on a synthesized support.
        int[] ids = new int[4 * 4 * 4];
        ids[flat(4, 4, 0, 0, 0)] = 2;            // floor sand -> below-volume support
        ids[flat(4, 4, 1, 0, 1)] = 1;            // stone...
        ids[flat(4, 4, 1, 1, 1)] = 2;            // ...with sand on top -> real support
        ids[flat(4, 4, 2, 2, 2)] = 2;            // floating sand -> synthetic support
        ids[flat(4, 4, 3, 1, 3)] = 2;            // sand stack over air
        ids[flat(4, 4, 3, 2, 3)] = 2;

        Result r = run(ids, 4, 4, 4);
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    int f = flat(4, 4, x, y, z);
                    if (ids[f] != 2 || !r.keep()[f]) continue;
                    if (y == 0) {
                        assertTrue(r.bottomSynth()[z * 4 + x] >= 0,
                            "floor sand at " + x + "," + z + " needs a below-volume support");
                        continue;
                    }
                    int below = flat(4, 4, x, y - 1, z);
                    boolean realSupport = r.keep()[below]
                        && (!FALL_THROUGH[ids[below]] || EXEMPT[ids[below]]);
                    assertTrue(realSupport || r.synthSource()[below] >= 0,
                        "sand at " + x + "," + y + "," + z + " is unsupported");
                }
            }
        }
    }

    @Test
    void buriedRealSupportIsForceKept() {
        // Stone under sand may be fully buried and would be culled on its own
        // merits; the pass must force-keep it rather than synthesize over it.
        int[] ids = new int[3 * 3 * 3];
        ids[flat(3, 3, 1, 0, 1)] = 1;
        ids[flat(3, 3, 1, 1, 1)] = 2;

        boolean[] keep = new boolean[27];
        keep[flat(3, 3, 1, 1, 1)] = true;   // sand kept, stone culled as buried
        int[] synthSource = new int[27];
        int[] bottomSynth = new int[9];
        SchemLoader.computeGravitySupport(ids, GRAVITY, FALL_THROUGH, EXEMPT, PRESENT,
            3, 3, 3, keep, synthSource, bottomSynth);

        assertTrue(keep[flat(3, 3, 1, 0, 1)], "buried stone under sand must be force-kept");
        assertEquals(-1, synthSource[flat(3, 3, 1, 0, 1)], "no synthetic support over a real one");
    }

    @Test
    void gravityStacksCascadeTheirSupportsDownward() {
        // Scanning top-down means a force-kept sand block below another sand
        // block is itself resolved on a later iteration.
        int[] ids = new int[3 * 4 * 3];
        ids[flat(3, 3, 1, 1, 1)] = 2;
        ids[flat(3, 3, 1, 2, 1)] = 2;
        ids[flat(3, 3, 1, 3, 1)] = 2;

        Result r = run(ids, 3, 4, 3);
        // The bottom sand rests on air at y=0, so one synthetic support appears
        // under it - and nothing is synthesized between the sand blocks.
        assertEquals(flat(3, 3, 1, 1, 1), r.synthSource()[flat(3, 3, 1, 0, 1)],
            "the bottom of the stack gets the support");
        assertEquals(-1, r.synthSource()[flat(3, 3, 1, 1, 1)], "sand supports the sand above it");
        assertEquals(-1, r.synthSource()[flat(3, 3, 1, 2, 1)], "sand supports the sand above it");
    }

    @Test
    void exemptFallThroughBlocksCountAsSupport() {
        // A chest is fall-through by vanilla's rule but is always placed, and
        // sand does rest on it - so it must not get a synthetic support that
        // would overwrite the chest.
        int[] ids = new int[3 * 3 * 3];
        ids[flat(3, 3, 1, 0, 1)] = 3;
        ids[flat(3, 3, 1, 1, 1)] = 2;

        Result r = run(ids, 3, 3, 3);
        assertEquals(-1, r.synthSource()[flat(3, 3, 1, 0, 1)],
            "an exempt block below sand must not be overwritten by a support");
    }

    @Test
    void culledGravityBlocksNeedNoSupport() {
        // A gravity block the cull dropped is never placed, so nothing should
        // be synthesized under it.
        int[] ids = new int[3 * 3 * 3];
        ids[flat(3, 3, 1, 1, 1)] = 2;

        boolean[] keep = new boolean[27];   // nothing kept
        int[] synthSource = new int[27];
        int[] bottomSynth = new int[9];
        SchemLoader.computeGravitySupport(ids, GRAVITY, FALL_THROUGH, EXEMPT, PRESENT,
            3, 3, 3, keep, synthSource, bottomSynth);

        assertEquals(-1, synthSource[flat(3, 3, 1, 0, 1)], "no support under a culled gravity block");
    }

    @Test
    void noGravityBlocksMeansNoSupports() {
        int[] ids = new int[3 * 3 * 3];
        ids[flat(3, 3, 1, 1, 1)] = 1;

        Result r = run(ids, 3, 3, 3);
        for (int v : r.synthSource()) assertEquals(-1, v, "stone needs no support");
        for (int v : r.bottomSynth()) assertEquals(-1, v, "stone needs no below-volume support");
    }
}
