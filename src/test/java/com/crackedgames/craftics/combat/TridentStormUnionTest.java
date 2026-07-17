package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Trident Storm volley's footprint geometry.
 *
 * <p>The volley is three 3x3 splashes whose centres sit 2 tiles apart, so they OVERLAP. The
 * bug this pins down: resolving that as one AreaAttack per splash damaged the seam tiles once
 * per covering splash (up to 3x), while the telegraph deduped for display and painted a
 * 12-damage tile identically to a 4-damage one. The fix collapses the volley to the union of
 * the footprints and resolves it once, so every covered tile is hit exactly once.
 *
 * <p>Pure int logic, mirroring TidecallerAI's real centre arithmetic - no arena, no bootstrap.
 */
class TridentStormUnionTest {

    /** BossAI.getDirectionToward: snapped to the dominant axis, so always cardinal. */
    private static int[] directionToward(GridPos from, GridPos to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (Math.abs(dx) >= Math.abs(dz)) return new int[]{Integer.signum(dx), 0};
        return new int[]{0, Integer.signum(dz)};
    }

    /** The three splash centres exactly as TidecallerAI builds them. */
    private static List<GridPos> stormCenters(GridPos playerPos, GridPos bossPos) {
        int[] toward = directionToward(playerPos, bossPos);
        List<GridPos> centers = new ArrayList<>();
        centers.add(playerPos);
        centers.add(new GridPos(playerPos.x() + toward[0] * 2, playerPos.z() + toward[1] * 2));
        centers.add(new GridPos(playerPos.x() + toward[1] * 2, playerPos.z() + toward[0] * 2));
        return centers;
    }

    @Test
    void theThreeSplashesGenuinelyOverlap() {
        // Guards the premise. If this ever fails the union fix is solving a non-problem, and
        // the damage spike would have to be explained by something else.
        List<GridPos> centers = stormCenters(new GridPos(0, 0), new GridPos(4, 0));
        Map<GridPos, Integer> coverage = new HashMap<>();
        for (GridPos c : centers) {
            for (GridPos t : AoeShapes.filledDisc(c, 1)) {
                coverage.merge(t, 1, Integer::sum);
            }
        }
        long multiCovered = coverage.values().stream().filter(n -> n > 1).count();
        assertTrue(multiCovered > 0,
            "the splashes must overlap for this bug to exist; got no multi-covered tiles");
    }

    @Test
    void noTileIsHitTwiceByOneVolley() {
        // THE regression test: the union lists every covered tile exactly once, so a single
        // resolution cannot stack damage on the seams.
        List<GridPos> centers = stormCenters(new GridPos(0, 0), new GridPos(4, 0));
        List<GridPos> union = AoeShapes.unionOfDiscs(centers, 1);
        assertEquals(union.size(), union.stream().distinct().count(),
            "union contains a duplicate tile, so that tile would be damaged more than once");
    }

    @Test
    void unionHoldsFromEveryApproachDirection() {
        // The flank centre swaps the direction components, so each facing produces a different
        // arrangement. None of them may yield a duplicate.
        GridPos player = new GridPos(6, 6);
        List<GridPos> bosses = List.of(
            new GridPos(10, 6), new GridPos(2, 6),
            new GridPos(6, 10), new GridPos(6, 2));
        for (GridPos boss : bosses) {
            List<GridPos> union = AoeShapes.unionOfDiscs(stormCenters(player, boss), 1);
            assertEquals(union.size(), union.stream().distinct().count(),
                "duplicate tile in the volley union with the boss at " + boss);
        }
    }

    @Test
    void unionCoversEveryTileTheSeparateSplashesWouldHave() {
        // The fix must not shrink the volley: covering less ground would quietly weaken the
        // "a lazy sidestep walks into the next splash" design, which is not what was wrong.
        List<GridPos> centers = stormCenters(new GridPos(0, 0), new GridPos(4, 0));
        List<GridPos> union = AoeShapes.unionOfDiscs(centers, 1);
        for (GridPos c : centers) {
            for (GridPos t : AoeShapes.filledDisc(c, 1)) {
                assertTrue(union.contains(t),
                    "tile " + t + " was covered by a splash but is missing from the union");
            }
        }
    }

    @Test
    void playerOnASeamTakesTheDamageExactlyOnce() {
        // The user's report: ~12-15 damage in one hit. Standing on a triple-covered tile now
        // resolves as a single membership test, so the damage is the volley's face value.
        List<GridPos> centers = stormCenters(new GridPos(0, 0), new GridPos(4, 0));
        Map<GridPos, Integer> coverage = new HashMap<>();
        for (GridPos c : centers) {
            for (GridPos t : AoeShapes.filledDisc(c, 1)) coverage.merge(t, 1, Integer::sum);
        }
        GridPos worstSeam = coverage.entrySet().stream()
            .max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        assertTrue(coverage.get(worstSeam) > 1, "expected a genuinely overlapped tile to test");

        List<GridPos> union = AoeShapes.unionOfDiscs(centers, 1);
        assertEquals(1, union.stream().filter(worstSeam::equals).count(),
            "the worst seam tile must appear once in the union, not once per covering splash");
    }

    @Test
    void unionOfDiscsIsTheUnionAndNotTheConcatenation() {
        // Two identical centres are the degenerate overlap: 9 tiles, not 18.
        GridPos c = new GridPos(3, 3);
        assertEquals(9, AoeShapes.unionOfDiscs(List.of(c, c), 1).size());
    }

    @Test
    void discsFurtherApartThanTheirDiameterDoNotMerge() {
        // Sanity on the shape itself: non-overlapping centres keep their full footprints.
        List<GridPos> union = AoeShapes.unionOfDiscs(
            List.of(new GridPos(0, 0), new GridPos(10, 10)), 1);
        assertEquals(18, union.size());
    }
}
