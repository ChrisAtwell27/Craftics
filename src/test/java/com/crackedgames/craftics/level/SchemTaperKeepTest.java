package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the taper-aware half of {@link SchemLoader#computeKeepMask}.
 *
 * <p>The bug this covers: {@code ArenaBuilder.taperSchemEdges} carves a stepped
 * slope into the outer {@code TAPER_FADE} tiles of an arena schematic AFTER the
 * visibility cull has already hollowed the volume out to its visible shell. The
 * carve therefore cut straight into that hollow, and the surviving stepped
 * geometry (the cliff banks with their lanterns) was left hanging over the open
 * space under the arena floor. Keeping the carved columns solid closes the
 * cavity before the carve opens it.
 *
 * <p>Filling from the world side afterwards is not an option and no test should
 * ask for it: once placed, a cull cavity is indistinguishable from a gap the
 * schematic author left open (river/1 has 1027 such cells in its taper zone).
 *
 * <p>Pure int[]/boolean[] geometry - no Minecraft bootstrap needed.
 */
class SchemTaperKeepTest {

    /** palette id 0 = air, 1 = opaque full cube (stone), 2 = arena corner marker. */
    private static final boolean[] AIR = {true, false, false};
    private static final boolean[] HIDES = {false, true, true};
    private static final boolean[] EXEMPT = {false, false, true};
    private static final boolean[] CORNER = {false, false, true};

    private static int flat(int w, int l, int x, int y, int z) {
        return ((y * l) + z) * w + x;
    }

    /** A solid stone volume, the shape of a schematic's terrain block. */
    private static int[] solid(int w, int h, int l) {
        int[] ids = new int[w * h * l];
        java.util.Arrays.fill(ids, 1);
        return ids;
    }

    @Test
    void taperConstantsAgreeAcrossTheTwoFiles() {
        // The keep mask mirrors ArenaBuilder's carve geometry. If they drift,
        // the cull seals the wrong columns and the cliffs hang again.
        assertEquals(ArenaBuilder.TAPER_FADE, SchemLoader.TAPER_FADE,
            "SchemLoader.TAPER_FADE must match ArenaBuilder.TAPER_FADE");
    }

    @Test
    void taperExposedMatchesArenaBuilderCarveGeometry() {
        // taperExposedAt must keep exactly the cells at or below the surface
        // ArenaBuilder.taperMaxKeepLocalY carves to, for every column.
        int w = 20, h = 12, l = 20;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < l; z++) {
                int dist = ArenaBuilder.taperEdgeDist(x, z, w, l);
                int maxKeep = ArenaBuilder.taperMaxKeepLocalY(dist, h);
                for (int y = 0; y < h; y++) {
                    boolean sealed = SchemLoader.taperExposedAt(w, h, l, x, y, z);
                    boolean shouldSeal = dist < ArenaBuilder.TAPER_FADE && y <= maxKeep;
                    assertEquals(shouldSeal, sealed,
                        "seal decision at " + x + "," + y + "," + z + " (dist " + dist + ")");
                }
            }
        }
    }

    @Test
    void carvedColumnsAreKeptSolidUnderTheSlope() {
        // The regression: buried stone under the taper's stepped surface used to
        // be culled, so the carve exposed a cavity. Every cell at or below the
        // step in a fade column must now be kept.
        int w = 20, h = 12, l = 20;
        boolean[] keep = SchemLoader.computeKeepMask(
            solid(w, h, l), AIR, HIDES, EXEMPT, CORNER, w, h, l, true, true);

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < l; z++) {
                int dist = ArenaBuilder.taperEdgeDist(x, z, w, l);
                if (dist >= ArenaBuilder.TAPER_FADE) continue;
                for (int y = 0; y <= ArenaBuilder.taperMaxKeepLocalY(dist, h); y++) {
                    assertTrue(keep[flat(w, l, x, y, z)],
                        "carved column cell " + x + "," + y + "," + z + " must be solid");
                }
            }
        }
    }

    @Test
    void theCullStillDropsBuriedFillerAwayFromTheEdges() {
        // The optimization must survive: the taper exemption is scoped to the
        // outer TAPER_FADE tiles, not the whole schematic. Deep interior filler
        // is still culled.
        int w = 20, h = 12, l = 20;
        boolean[] keep = SchemLoader.computeKeepMask(
            solid(w, h, l), AIR, HIDES, EXEMPT, CORNER, w, h, l, true, true);

        assertFalse(keep[flat(w, l, 10, 5, 10)], "buried interior filler is still culled");
        assertFalse(keep[flat(w, l, 8, 3, 8)], "buried filler inside the fade ring is still culled");
    }

    @Test
    void aboveTheSlopeIsNotSealed() {
        // The taper carves those cells to air anyway; keeping them would just
        // place blocks the carve immediately deletes.
        int w = 20, h = 12, l = 20;
        // dist 1 -> the slope keeps up to local y=2, so y=3+ is carved away.
        assertFalse(SchemLoader.taperExposedAt(w, h, l, 1, 3, 10), "above the step is carved, not sealed");
        assertTrue(SchemLoader.taperExposedAt(w, h, l, 1, 2, 10), "the step surface itself is sealed");
    }

    @Test
    void taperFlagOffLeavesTheMaskUntouched() {
        // Boss and trial-chamber schematics skip the taper (preserveSchematicGround),
        // so their cull must be byte-for-byte what it was before this change.
        int w = 16, h = 10, l = 16;
        int[] ids = solid(w, h, l);
        boolean[] plain = SchemLoader.computeKeepMask(ids, AIR, HIDES, EXEMPT, CORNER, w, h, l, true);
        boolean[] untapered = SchemLoader.computeKeepMask(
            ids, AIR, HIDES, EXEMPT, CORNER, w, h, l, true, false);

        for (int i = 0; i < plain.length; i++) {
            assertEquals(plain[i], untapered[i], "keep[] diverged at flat index " + i);
        }
    }

    @Test
    void airInTheTaperZoneIsNeverKept() {
        // A gap the author left open inside the fade ring stays open: the seal
        // only re-keeps cells the schematic itself has as solid.
        int w = 20, h = 12, l = 20;
        int[] ids = solid(w, h, l);
        ids[flat(w, l, 1, 1, 10)] = 0;

        boolean[] keep = SchemLoader.computeKeepMask(
            ids, AIR, HIDES, EXEMPT, CORNER, w, h, l, true, true);
        assertFalse(keep[flat(w, l, 1, 1, 10)], "authored air in the taper zone stays air");
    }
}
