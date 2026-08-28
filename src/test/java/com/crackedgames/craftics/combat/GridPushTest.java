package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.GridPush.Cell;
import com.crackedgames.craftics.combat.GridPush.Result;
import com.crackedgames.craftics.combat.GridPush.Stop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The push rule, which until now existed in six hand-written copies that had quietly stopped
 * agreeing with each other.
 *
 * <p>The first test is the one that matters: the arena boundary stops a push and does not kill.
 * One of those six copies dropped anything crossing the edge into the void, which a Punch bow
 * turned into a free ranged one-shot past every resistance. Nothing could catch that before,
 * because the rule was unreachable from a test.
 */
class GridPushTest {

    /** A grid drawn as text rows, one character per tile. Anything unlisted is out of bounds. */
    private static GridPush.Grid grid(String... rows) {
        Map<Long, Cell> cells = new HashMap<>();
        for (int z = 0; z < rows.length; z++) {
            for (int x = 0; x < rows[z].length(); x++) {
                cells.put(key(x, z), switch (rows[z].charAt(x)) {
                    case '.' -> Cell.OPEN;
                    case '#' -> Cell.HARD_OBSTACLE;
                    case 'c' -> Cell.SOFT_OBSTACLE;
                    case 'v' -> Cell.HAZARD;
                    case 'e' -> Cell.ENTITY;
                    case 'p' -> Cell.PLAYER;
                    case 'x' -> Cell.IMPASSABLE;
                    default -> throw new IllegalArgumentException("bad tile");
                });
            }
        }
        return (x, z) -> cells.getOrDefault(key(x, z), Cell.OUT_OF_BOUNDS);
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static Result push(GridPush.Grid g, int fromX, int tiles) {
        return GridPush.resolve(g, fromX, 0, 1, 1, 1, 0, tiles, false);
    }

    // -- The boundary --------------------------------------------------------

    @Test
    @DisplayName("the arena edge stops a push and never kills")
    void theEdgeIsAWall() {
        Result r = push(grid("..."), 1, 5);
        assertEquals(2, r.x(), "stops on the last real tile");
        assertEquals(Stop.BOUNDARY, r.stop());
        assertFalse(r.enteredHazard(), "the edge is not a hazard - crossing it is simply refused");
    }

    @Test
    @DisplayName("being pushed at the edge from right against it moves nothing")
    void alreadyAtTheEdge() {
        Result r = push(grid("..."), 2, 3);
        assertEquals(2, r.x());
        assertEquals(0, r.moved());
        assertEquals(Stop.BOUNDARY, r.stop());
    }

    // -- Hazards are entered, walls are not ---------------------------------

    @Test
    @DisplayName("a hazard is stepped into and ends the push there")
    void hazardsAreEntered() {
        Result r = push(grid(".v."), 0, 3);
        assertEquals(1, r.x(), "it lands ON the hazard");
        assertEquals(Stop.HAZARD_ENTERED, r.stop());
        assertTrue(r.enteredHazard());
    }

    @Test
    @DisplayName("something hazard-immune stops in front of a hazard instead")
    void hazardImmuneStopsShort() {
        Result r = GridPush.resolve(grid(".v."), 0, 0, 1, 1, 1, 0, 3, true);
        assertEquals(0, r.x(), "it never enters");
        assertEquals(Stop.HAZARD_EDGE, r.stop());
        assertFalse(r.enteredHazard());
    }

    @Test
    @DisplayName("a wall stops the push in front of itself")
    void wallsStopShort() {
        Result r = push(grid(".#."), 0, 3);
        assertEquals(0, r.x());
        assertEquals(Stop.OBSTACLE, r.stop());
    }

    @Test
    @DisplayName("entities and players block, and are told apart")
    void blockersAreDistinguished() {
        assertEquals(Stop.ENTITY, push(grid(".e."), 0, 2).stop());
        assertEquals(Stop.PLAYER, push(grid(".p."), 0, 2).stop());
        assertEquals(Stop.IMPASSABLE, push(grid(".x."), 0, 2).stop());
    }

    // -- Cactus ---------------------------------------------------------------

    @Test
    @DisplayName("a cactus scratches but does not stop")
    void cactusIsPassedThrough() {
        Result r = push(grid(".c."), 0, 2);
        assertEquals(2, r.x(), "it keeps going");
        assertEquals(Stop.COMPLETED, r.stop());
        assertTrue(r.brushedCactus());
    }

    @Test
    @DisplayName("a cactus still scratches on a step that was blocked")
    void cactusCountsEvenWhenBlocked() {
        // The cactus and the wall share one footprint: the wall wins, the scratch still lands.
        Result r = GridPush.resolve(grid(".c", ".#"), 0, 0, 1, 2, 1, 0, 2, false);
        assertEquals(Stop.OBSTACLE, r.stop());
        assertTrue(r.brushedCactus(), "reaching the cactus is what scratches, not landing on it");
    }

    // -- Footprints -----------------------------------------------------------

    @Test
    @DisplayName("the WHOLE footprint is checked, not just its corner")
    void footprintIsCheckedInFull() {
        // Top row clear, bottom row blocked. A 1x1 walks it; a 1x2 must not.
        assertEquals(Stop.COMPLETED, GridPush.resolve(grid("...", ".#."), 0, 0, 1, 1, 1, 0, 2, false).stop());
        assertEquals(Stop.OBSTACLE, GridPush.resolve(grid("...", ".#."), 0, 0, 1, 2, 1, 0, 2, false).stop(),
            "the second row of the footprint hits the obstacle the corner missed");
    }

    // -- Distance and direction ----------------------------------------------

    @Test
    @DisplayName("it travels exactly as far as asked when nothing is in the way")
    void travelsTheFullDistance() {
        Result r = push(grid("......"), 0, 3);
        assertEquals(3, r.x());
        assertEquals(3, r.moved());
        assertEquals(Stop.COMPLETED, r.stop());
    }

    @Test
    @DisplayName("a pull is the same rule with the direction reversed")
    void worksInEveryDirection() {
        GridPush.Grid g = grid("....", "....", "....");
        assertEquals(0, GridPush.resolve(g, 3, 0, 1, 1, -1, 0, 3, false).x(), "west");
        assertEquals(2, GridPush.resolve(g, 0, 0, 1, 1, 0, 1, 2, false).z(), "south");
        assertEquals(0, GridPush.resolve(g, 0, 2, 1, 1, 0, -1, 2, false).z(), "north");
    }

    @Test
    @DisplayName("a zero-tile push is a no-op, not a step")
    void zeroDistanceDoesNothing() {
        Result r = push(grid("..."), 1, 0);
        assertEquals(1, r.x());
        assertEquals(0, r.moved());
        assertEquals(Stop.COMPLETED, r.stop());
    }

    @Test
    @DisplayName("it never stops one tile early or one tile late")
    void stopsExactlyAtTheObstacle() {
        // Walls at increasing range: the landing tile must always be the one before the wall.
        for (int wallAt = 1; wallAt <= 4; wallAt++) {
            StringBuilder row = new StringBuilder("......");
            row.setCharAt(wallAt, '#');
            Result r = push(grid(row.toString()), 0, 5);
            assertEquals(wallAt - 1, r.x(), "wall at " + wallAt);
            assertEquals(wallAt - 1, r.moved(), "wall at " + wallAt);
        }
    }
}
