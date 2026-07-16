package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure geometry for the Revenant's surfacing anchors. The arena-dependent half of Burrow
 * (occupancy, the untargetable flag) needs a ServerWorld and is covered by the in-game
 * checklist instead.
 */
class RevenantBurrowTest {

    private static final GridPos GRAVE = new GridPos(5, 5);

    @Test
    void neverSurfacesOnTheGraveTile() {
        // The whole point: the grave is a real 1x1 combatant, and erupting on top of it would
        // destroy the thing the ability depends on.
        for (GridPos anchor : RevenantAI.surfacingAnchors(GRAVE, 2, 2)) {
            assertFalse(covers(anchor, 2, 2, GRAVE),
                "anchor " + anchor + " would put the 2x2 footprint on the grave");
        }
    }

    @Test
    void everyAnchorTouchesTheGrave() {
        // It erupts FROM the grave, so a footprint that merely lands nearby is wrong.
        for (GridPos anchor : RevenantAI.surfacingAnchors(GRAVE, 2, 2)) {
            assertEquals(1, footprintDistance(anchor, 2, 2, GRAVE),
                "anchor " + anchor + " is not adjacent to the grave");
        }
    }

    @Test
    void twoByTwoBossHasAnchorsOnAllFourSides() {
        List<GridPos> anchors = RevenantAI.surfacingAnchors(GRAVE, 2, 2);
        assertFalse(anchors.isEmpty());
        // A 2x2 can hug the grave from any side, so the escape is never geometrically boxed in
        // on an open arena; only real occupancy can refuse it.
        assertTrue(anchors.stream().anyMatch(a -> a.x() > GRAVE.x()), "no anchor east");
        assertTrue(anchors.stream().anyMatch(a -> a.x() + 1 < GRAVE.x()), "no anchor west");
        assertTrue(anchors.stream().anyMatch(a -> a.z() > GRAVE.z()), "no anchor south");
        assertTrue(anchors.stream().anyMatch(a -> a.z() + 1 < GRAVE.z()), "no anchor north");
    }

    @Test
    void oneByOneCaseStillExcludesTheGraveTile() {
        // Guards the size-agnostic path: a 1x1 has exactly the 4 orthogonal neighbours and
        // never the grave's own tile.
        List<GridPos> anchors = RevenantAI.surfacingAnchors(GRAVE, 1, 1);
        assertEquals(4, anchors.size());
        assertFalse(anchors.contains(GRAVE));
    }

    @Test
    void oneByOneIsTheRevenantsRealFootprintAndHasAnchorsOnAllFourSides() {
        // The Revenant is 1x1: it never overrides BossAI.getGridSize (which returns 1) and a
        // zombie's footprint is 1x1. The 2x2 cases above are kept only as size-agnostic coverage;
        // THIS is the size the live boss actually surfaces with.
        List<GridPos> anchors = RevenantAI.surfacingAnchors(GRAVE, 1, 1);
        assertEquals(List.of(
            new GridPos(GRAVE.x() - 1, GRAVE.z()),
            new GridPos(GRAVE.x(), GRAVE.z() - 1),
            new GridPos(GRAVE.x(), GRAVE.z() + 1),
            new GridPos(GRAVE.x() + 1, GRAVE.z())
        ), anchors.stream().sorted(
            java.util.Comparator.comparingInt(GridPos::x).thenComparingInt(GridPos::z)
        ).toList());
    }

    @Test
    void anchorsAreDeterministicForAGivenGrave() {
        // Randomness lives in the grave CHOICE, not in the geometry. Keeping this ordering
        // stable is what makes the random-grave pick the only source of variation.
        assertEquals(RevenantAI.surfacingAnchors(GRAVE, 2, 2),
                     RevenantAI.surfacingAnchors(GRAVE, 2, 2));
    }

    private static boolean covers(GridPos anchor, int sizeX, int sizeZ, GridPos target) {
        return target.x() >= anchor.x() && target.x() < anchor.x() + sizeX
            && target.z() >= anchor.z() && target.z() < anchor.z() + sizeZ;
    }

    private static int footprintDistance(GridPos anchor, int sizeX, int sizeZ, GridPos target) {
        int best = Integer.MAX_VALUE;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                int d = Math.abs(anchor.x() + dx - target.x()) + Math.abs(anchor.z() + dz - target.z());
                if (d < best) best = d;
            }
        }
        return best;
    }
}
