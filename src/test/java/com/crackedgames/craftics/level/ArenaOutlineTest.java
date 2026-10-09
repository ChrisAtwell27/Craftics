package com.crackedgames.craftics.level;

import com.crackedgames.craftics.level.ArenaOutline.Point;
import com.crackedgames.craftics.level.ArenaOutline.Traced;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The outline a room's corner markers draw.
 *
 * <p>The marker sets below are the real ones, read out of the Aether dungeon rooms that
 * came out wrong: a room of the Bronze Dungeon and two of the Silver. Each was built as
 * rooms joined by doorways, with a marker wherever the builder saw a corner.
 */
class ArenaOutlineTest {

    private static List<Point> points(int... xz) {
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) out.add(new Point(xz[i], xz[i + 1]));
        return out;
    }

    /** The floor of an outline, one character a tile: {@code .} floor, {@code +} rim, blank outside. */
    private static String drawn(List<Point> markers) {
        Traced traced = ArenaOutline.trace(markers);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Point p : markers) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
        }
        int w = maxX - minX + 1, h = maxZ - minZ + 1;
        boolean[][] filled = ArenaOutline.filled(traced.ring(), minX, minZ, w, h);
        boolean[][] floor = ArenaOutline.floor(filled, traced.ring(), minX, minZ);
        StringBuilder out = new StringBuilder();
        for (int z = 0; z < h; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < w; x++) row.append(floor[x][z] ? '.' : filled[x][z] ? '+' : ' ');
            out.append(row.toString().stripTrailing());
            if (z < h - 1) out.append('\n');
        }
        return out.toString();
    }

    /** Silver Dungeon, second room: three cells in a row and one below the middle. */
    private static final List<Point> SILVER_TWO = points(
        25, 7, 46, 7, 25, 14, 32, 14, 39, 14, 46, 14, 32, 21, 39, 21);

    /** Silver Dungeon, third room: the same, and two more cells above. */
    private static final List<Point> SILVER_THREE = points(
        25, 0, 39, 0, 25, 7, 32, 7, 39, 7, 46, 7,
        25, 14, 32, 14, 39, 14, 46, 14, 32, 21, 39, 21);

    /** Bronze Dungeon, sixth room: two rooms side by side, a passage between, three leading off. */
    private static final List<Point> BRONZE_SIX = points(
        19, 12, 24, 12, 0, 17, 19, 17, 24, 17, 27, 17, 11, 20, 27, 20, 32, 20,
        11, 25, 16, 25, 27, 25, 32, 25, 0, 28, 16, 28, 19, 28, 24, 28, 27, 28, 19, 33, 24, 33);

    @Test
    void aRectangleIsItsFourCorners() {
        Traced traced = ArenaOutline.trace(points(0, 0, 5, 0, 5, 4, 0, 4));
        assertEquals(4, traced.ring().size());
        assertTrue(traced.spare().isEmpty());
        assertEquals(String.join("\n",
            "++++++",
            "+....+",
            "+....+",
            "+....+",
            "++++++"), drawn(points(0, 0, 5, 0, 5, 4, 0, 4)));
    }

    @Test
    void anLShapeKeepsItsNotch() {
        // The corner the room turns round is a rim tile, not floor.
        assertEquals(String.join("\n",
            "+++++++",
            "+.....+",
            "+.....+",
            "+..++++",
            "+..+",
            "++++"), drawn(points(0, 0, 6, 0, 6, 3, 3, 3, 3, 5, 0, 5)));
    }

    @Test
    void aTShapeKeepsTheRowOfFloorBesideItsInwardWalls() {
        // The two short walls under the arms of the T look straight at the middle of the
        // room. They used to drop off the outline, and the row of floor against each of
        // them went with them: a strip along the wall that could not be stepped on.
        Traced traced = ArenaOutline.trace(SILVER_TWO);
        assertTrue(traced.spare().isEmpty());
        assertEquals(String.join("\n",
            "++++++++++++++++++++++",
            "+....................+",
            "+....................+",
            "+....................+",
            "+....................+",
            "+....................+",
            "+....................+",
            "++++++++......++++++++",
            "       +......+",
            "       +......+",
            "       +......+",
            "       +......+",
            "       +......+",
            "       +......+",
            "       ++++++++"), drawn(SILVER_TWO));
    }

    @Test
    void aMarkerOnAStraightWallIsNotACorner() {
        Traced traced = ArenaOutline.trace(points(0, 0, 3, 0, 6, 0, 6, 4, 0, 4));
        assertEquals(List.of(new Point(3, 0)), traced.spare());
        assertEquals(4, traced.ring().size());
    }

    @Test
    void aRoomOfCellsMarkedAtEveryCellCornerIsStillOneRoom() {
        // Twelve markers, ten corners. One spare is half way down the left wall and the
        // other is in the middle, where the cells meet. Taken in order round the centre,
        // the middle one cut a wedge out of the top of the room.
        Traced traced = ArenaOutline.trace(SILVER_THREE);
        assertEquals(10, traced.ring().size());
        assertEquals(2, traced.spare().size());
        assertTrue(traced.spare().contains(new Point(25, 7)));
        assertTrue(traced.spare().contains(new Point(32, 7)));
        assertEquals(String.join("\n",
            "+++++++++++++++",
            "+.............+",
            "+.............+",
            "+.............+",
            "+.............+",
            "+.............+",
            "+.............+",
            "+.............++++++++",
            "+....................+",
            "+....................+",
            "+....................+",
            "+....................+",
            "+....................+",
            "+....................+",
            "++++++++......++++++++",
            "       +......+",
            "       +......+",
            "       +......+",
            "       +......+",
            "       +......+",
            "       +......+",
            "       ++++++++"), drawn(SILVER_THREE));
    }

    @Test
    void aMarkerInsideTheRoomIsFloorLikeAnyOther() {
        // Whatever is built there decides what the tile is. The outline does not punch a
        // hole in the floor for a marker it had no use for.
        Traced traced = ArenaOutline.trace(SILVER_THREE);
        boolean[][] filled = ArenaOutline.filled(traced.ring(), 25, 0, 22, 22);
        boolean[][] floor = ArenaOutline.floor(filled, traced.ring(), 25, 0);
        assertTrue(floor[32 - 25][7]);
    }

    @Test
    void twoRoomsAndTheirPassagesAreOneArena() {
        // Twenty markers that do not pair up row by row. Four are set aside and what is
        // left is both rooms, the passage between and the three passages leading off.
        Traced traced = ArenaOutline.trace(BRONZE_SIX);
        assertEquals(16, traced.ring().size());
        assertEquals(4, traced.spare().size());
        assertEquals(String.join("\n",
            "                   ++++++",
            "                   +....+",
            "                   +....+",
            "                   +....+",
            "                   +....+",
            "++++++++++++++++++++....++++",
            "+..........................+",
            "+..........................+",
            "+..........................++++++",
            "+...............................+",
            "+...............................+",
            "+...............................+",
            "+...............................+",
            "+..........................++++++",
            "+..........................+",
            "+..........................+",
            "++++++++++++++++++++....++++",
            "                   +....+",
            "                   +....+",
            "                   +....+",
            "                   +....+",
            "                   ++++++"), drawn(BRONZE_SIX));
    }

    @Test
    void aDiamondIsLeftAlone() {
        // No rows or columns to pair, and no marker that could be spared: every one of
        // them sticks out. It is taken in order round its centre, as it always was.
        List<Point> diamond = points(4, 0, 8, 4, 4, 8, 0, 4);
        Traced traced = ArenaOutline.trace(diamond);
        assertEquals(4, traced.ring().size());
        assertTrue(traced.spare().isEmpty());
        assertEquals(String.join("\n",
            "    +",
            "   +.+",
            "  +...+",
            " +.....+",
            "+.......+",
            " +.....+",
            "  +...+",
            "   +.+",
            "    +"), drawn(diamond));
    }

    @Test
    void anOctagonIsNotCutDownToARectangle() {
        // Its markers do pair up in rows and columns, four at a time, and any four of them
        // make a rectangle. The other four would be left outside it, which is how this
        // knows they were never spare.
        List<Point> octagon = points(2, 0, 5, 0, 7, 2, 7, 5, 5, 7, 2, 7, 0, 5, 0, 2);
        Traced traced = ArenaOutline.trace(octagon);
        assertEquals(8, traced.ring().size());
        assertTrue(traced.spare().isEmpty());
    }

    @Test
    void twoMarkersInOneColumnAreOneCorner() {
        Traced traced = ArenaOutline.trace(points(0, 0, 0, 0, 5, 0, 5, 4, 0, 4));
        assertEquals(4, traced.ring().size());
        assertTrue(traced.spare().isEmpty());
    }

    @Test
    void everyTileOnTheOutlineIsFilledWhicheverWayItsEdgeFaces() {
        Traced traced = ArenaOutline.trace(SILVER_TWO);
        List<Point> ring = traced.ring();
        for (int i = 0; i < ring.size(); i++) {
            Point a = ring.get(i), b = ring.get((i + 1) % ring.size());
            int steps = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
            for (int s = 0; s <= steps; s++) {
                int x = a.x() + Integer.signum(b.x() - a.x()) * s;
                int z = a.z() + Integer.signum(b.z() - a.z()) * s;
                assertTrue(ArenaOutline.contains(ring, x, z), "outline tile " + x + "," + z);
            }
        }
    }
}
