package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which height of a marked-out room is its floor.
 *
 * <p>The counts are the real ones from the rooms that came out wrong: how many tiles
 * inside each outline can be stood on at the markers' height, one above and two above.
 */
class ArenaFloorLevelTest {

    @Test
    void aWalledRoomIsStoodOnAtItsFloorNotItsWallTops() {
        // Bronze Dungeon, sixth room. The markers lie in the floor under walls two blocks
        // high, so their own columns point two blocks up, at the tops of the walls.
        assertEquals(0, ArenaFloorLevel.pick(2, new int[]{0, 1, 2}, new int[]{252, 30, 44}));
    }

    @Test
    void wallsTallerThanAMarkerCanBeBuriedDoNotPutTheFloorInsideThem() {
        // Silver Dungeon. Three blocks of wall over each marker: followed up two, the
        // markers point at the middle of the wall.
        assertEquals(24, ArenaFloorLevel.pick(26, new int[]{24, 25, 26}, new int[]{125, 4, 10}));
        assertEquals(24, ArenaFloorLevel.pick(26, new int[]{24, 25, 26}, new int[]{209, 5, 17}));
    }

    @Test
    void aMarkerHiddenUnderTheFloorStillPointsUpToIt() {
        // Buried one deep in open ground: nothing can be stood on at the marker's own
        // height, the whole room can a block above.
        assertEquals(11, ArenaFloorLevel.pick(11, new int[]{10, 11, 12}, new int[]{0, 146, 0}));
    }

    @Test
    void anOpenFieldStaysWhereItsMarkersAre() {
        assertEquals(32, ArenaFloorLevel.pick(32, new int[]{32, 33, 34}, new int[]{146, 0, 0}));
    }

    @Test
    void bouldersOnTheFloorDoNotLiftIt() {
        // A third of the room is one-block rocks with flat tops. Their tops can be stood
        // on, but most of the room is still the ground they sit on.
        assertEquals(10, ArenaFloorLevel.pick(10, new int[]{10, 11, 12}, new int[]{100, 50, 0}));
    }

    @Test
    void theMarkersSettleATie() {
        assertEquals(11, ArenaFloorLevel.pick(11, new int[]{10, 11, 12}, new int[]{60, 60, 0}));
        assertEquals(10, ArenaFloorLevel.pick(10, new int[]{10, 11, 12}, new int[]{60, 60, 0}));
    }

    @Test
    void aRoomWithNowhereToStandKeepsTheMarkersAnswer() {
        // All water, or all pit: nothing to go on, so nothing changes.
        assertEquals(12, ArenaFloorLevel.pick(12, new int[]{10, 11, 12}, new int[]{0, 0, 0}));
    }
}
