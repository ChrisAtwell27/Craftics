package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which stacked floors of a room a fight can use.
 *
 * <p>The Valkyrie Queen's room is the case this was written for: a lower floor, a raised
 * floor one block up with stairs along its edge, and on that a throne dais one block
 * higher again with stairs of its own all the way round. The first raised floor was
 * always walkable. The dais read as a wall, with stairs leading up to it.
 */
class ArenaTiersTest {

    /**
     * A room drawn as text, one character a tile, rows down the page.
     * {@code .} floor, {@code s} a stair on the floor, {@code 1} the first raised floor,
     * {@code S} a stair standing on it, {@code 2} a block two high with room above,
     * {@code T} a stair standing on that, {@code 3} three high, {@code #} a wall.
     */
    private static int[][][] room(String... rows) {
        int h = rows.length, w = rows[0].length();
        int[][] have = new int[w][h];
        int[][] could = new int[w][h];
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                switch (rows[z].charAt(x)) {
                    case 's' -> have[x][z] = -1;
                    case '1' -> have[x][z] = 1;
                    case 'S' -> could[x][z] = -2;
                    case '2' -> could[x][z] = 2;
                    case 'T' -> could[x][z] = -3;
                    case '3' -> could[x][z] = 3;
                    default -> { }
                }
            }
        }
        return new int[][][]{have, could};
    }

    private static String drawn(int[][] reached, int h) {
        StringBuilder out = new StringBuilder();
        for (int z = 0; z < h; z++) {
            for (int[] column : reached) {
                int cell = column[z];
                out.append(cell == 0 ? '.' : cell == -2 ? 'S' : cell == 2 ? '2' : cell == -3 ? 'T' : cell == 3 ? '3' : '?');
            }
            if (z < h - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String reach(String... rows) {
        int[][][] r = room(rows);
        return drawn(ArenaTiers.reached(r[0], r[1]), rows.length);
    }

    @Test
    void aSecondFlightMakesTheDaisAFloor() {
        assertEquals(
            ".....\n"
          + "...S2\n"
          + "...22",
            reach(
                ".s111",
                ".s1S2",
                ".s122"));
    }

    @Test
    void theWholeDaisIsReachedNotOnlyTheTileAtTheTopOfTheStairs() {
        assertEquals(
            "......\n"
          + "..S222\n"
          + "...222\n"
          + "...222",
            reach(
                "s11111",
                "s1S222",
                "s11222",
                "s11222"));
    }

    @Test
    void aBlockTwoHighWithNoStairToItStaysAWall() {
        // A planter standing on the raised floor: flat on top, and nothing climbs it.
        assertEquals(
            ".....\n"
          + ".....\n"
          + ".....",
            reach(
                "s1111",
                "s1221",
                "s1111"));
    }

    @Test
    void aFlightThatStandsOnNothingWalkableLeadsNowhere() {
        // The stair and the dais are both there, but walled off from every floor.
        assertEquals(
            "......\n"
          + "......",
            reach(
                "s1#S22",
                "s1#222"));
    }

    @Test
    void aStraightStaircaseClimbsTwoFloorsWithoutALanding() {
        // Floor, a stair, a stair standing on a block, then the upper floor.
        assertEquals(
            "..S22",
            reach(".sS22"));
    }

    @Test
    void aThirdFloorNeedsAFlightOfItsOwn() {
        // The block three high beside the dais has no flight to it. The one past the
        // second flight does.
        assertEquals(
            "..S2T3\n"
          + "...2..",
            reach(
                "s1S2T3",
                "s132.."));
    }

    @Test
    void theFirstRaisedFloorIsLeftToTheScanThatFoundIt() {
        // Tier one in the list of candidates is not this rule's to decide.
        int[][] have = new int[2][1];
        int[][] could = new int[2][1];
        have[0][0] = -1;
        could[1][0] = 1;
        assertEquals(0, ArenaTiers.reached(have, could)[1][0]);
    }

    @Test
    void anEmptyRoomIsNothing() {
        assertEquals(0, ArenaTiers.reached(new int[0][0], new int[0][0]).length);
    }

    @Test
    void threeFloorsIsTheLimit() {
        assertEquals(3, ArenaTiers.MAX_TIER);
    }
}
