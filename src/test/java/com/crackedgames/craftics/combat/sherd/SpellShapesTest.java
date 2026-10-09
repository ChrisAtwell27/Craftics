package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shapes a spell's staging is drawn in.
 *
 * <p>They have to match how the spells themselves measure. A sherd that hits "within 2 tiles"
 * counts in steps, so it hits a diamond - and a wave drawn as a square would wash over enemies
 * the spell never touched, which reads as the spell missing.
 */
class SpellShapesTest {

    private static final GridPos CENTRE = new GridPos(5, 5);

    @Test
    void aRingIsEveryTileExactlyThatManyStepsAway() {
        List<GridPos> ring = SpellShapes.ring(CENTRE, 2);

        assertEquals(8, ring.size());
        for (GridPos tile : ring) assertEquals(2, tile.manhattanDistance(CENTRE), tile.toString());
        assertEquals(8, new HashSet<>(ring).size(), "no tile twice");
    }

    @Test
    void theFirstRingIsTheFourTilesBesideTheCentre() {
        List<GridPos> ring = SpellShapes.ring(CENTRE, 1);

        assertEquals(4, ring.size());
        assertTrue(ring.contains(new GridPos(4, 5)));
        assertTrue(ring.contains(new GridPos(6, 5)));
        assertTrue(ring.contains(new GridPos(5, 4)));
        assertTrue(ring.contains(new GridPos(5, 6)));
        assertFalse(ring.contains(new GridPos(6, 6)), "a diagonal is two steps, not one");
    }

    @Test
    void ringNoughtIsTheCentreItself() {
        assertEquals(List.of(CENTRE), SpellShapes.ring(CENTRE, 0));
        assertEquals(List.of(CENTRE), SpellShapes.ring(CENTRE, -3));
    }

    @Test
    void aDiscIsEveryRingUpToItsEdgeNearestFirst() {
        List<GridPos> disc = SpellShapes.disc(CENTRE, 2);

        // 1 + 4 + 8: the same thirteen tiles a "within 2 tiles" spell reaches.
        assertEquals(13, disc.size());
        assertEquals(CENTRE, disc.get(0));
        assertEquals(13, new HashSet<>(disc).size());
        int last = 0;
        for (GridPos tile : disc) {
            int steps = tile.manhattanDistance(CENTRE);
            assertTrue(steps >= last, "nearest first");
            last = steps;
        }
    }

    @Test
    void aLineBetweenTwoTilesLeavesOutBothEnds() {
        List<GridPos> line = SpellShapes.between(new GridPos(1, 1), new GridPos(5, 1));

        assertEquals(List.of(new GridPos(2, 1), new GridPos(3, 1), new GridPos(4, 1)), line);
    }

    @Test
    void aDiagonalLineStepsDiagonally() {
        List<GridPos> line = SpellShapes.between(new GridPos(1, 1), new GridPos(4, 4));

        assertEquals(List.of(new GridPos(2, 2), new GridPos(3, 3)), line);
    }

    @Test
    void neighboursAndTheSameTileHaveNothingBetweenThem() {
        assertTrue(SpellShapes.between(CENTRE, new GridPos(6, 5)).isEmpty());
        assertTrue(SpellShapes.between(CENTRE, new GridPos(6, 6)).isEmpty());
        assertTrue(SpellShapes.between(CENTRE, CENTRE).isEmpty());
    }

    @Test
    void aSlantedLineNeverVisitsATileTwice() {
        List<GridPos> line = SpellShapes.between(new GridPos(0, 0), new GridPos(6, 2));

        assertEquals(line.size(), new HashSet<>(line).size());
        assertEquals(5, line.size(), "one tile for each step along the long side");
    }
}
