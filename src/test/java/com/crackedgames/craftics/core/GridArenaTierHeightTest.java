package com.crackedgames.craftics.core;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where a body stands on a room with more than one floor.
 *
 * <p>A stair is half a block up and a raised floor a whole one. A second floor, reached by
 * a second flight, is a block higher again, and every teleport, walk and knockback in a
 * fight asks this one method where that is.
 */
class GridArenaTierHeightTest {

    private static final BlockPos ORIGIN = new BlockPos(100, 64, 200);
    /** Where feet go on the arena's own floor. */
    private static final double FLOOR = 65.0;

    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[4][1];
        for (int x = 0; x < 4; x++) tiles[x][0] = new GridTile(TileType.NORMAL, null);
        return new GridArena(4, 1, tiles, ORIGIN, 1, new GridPos(0, 0));
    }

    private static GridPos put(GridArena a, int x, TileType type, int rise) {
        GridTile tile = new GridTile(type, null);
        tile.setRise(rise);
        GridPos pos = new GridPos(x, 0);
        a.setTile(pos, tile);
        return pos;
    }

    @Test
    void theFirstFlightAndTheFirstRaisedFloorAreWhereTheyAlwaysWere() {
        GridArena a = arena();
        assertEquals(FLOOR, a.getEntityY(new GridPos(0, 0)));
        assertEquals(FLOOR + 0.5, a.getEntityY(put(a, 1, TileType.STAIR, 0)));
        assertEquals(FLOOR + 1, a.getEntityY(put(a, 2, TileType.ELEVATED, 0)));
    }

    @Test
    void aSecondFlightAndASecondFloorAreABlockHigher() {
        GridArena a = arena();
        assertEquals(FLOOR + 1.5, a.getEntityY(put(a, 1, TileType.STAIR, 1)));
        assertEquals(FLOOR + 2, a.getEntityY(put(a, 2, TileType.ELEVATED, 1)));
        assertEquals(FLOOR + 3, a.getEntityY(put(a, 3, TileType.ELEVATED, 2)));
    }

    @Test
    void aFlyerSitsOnTheSameFloorAsAWalker() {
        GridArena a = arena();
        assertEquals(FLOOR + 1.5, a.getEntityY(put(a, 1, TileType.STAIR, 1), true));
        assertEquals(FLOOR + 2, a.getEntityY(put(a, 2, TileType.ELEVATED, 1), true));
    }

    @Test
    void aTileOnTheFloorHasNoRiseAndCannotBeGivenLessThanNone() {
        GridTile tile = new GridTile(TileType.ELEVATED, null);
        assertEquals(0, tile.getRise());
        tile.setRise(-3);
        assertEquals(0, tile.getRise());
    }

    @Test
    void whatASpawnAddsIsTheHeightOfTheTileAboveTheFloor() {
        GridArena a = arena();
        assertEquals(0.0, a.getSurfaceLift(new GridPos(0, 0)));
        assertEquals(0.5, a.getSurfaceLift(put(a, 1, TileType.STAIR, 0)));
        assertEquals(1.0, a.getSurfaceLift(put(a, 2, TileType.ELEVATED, 0)));
        assertEquals(2.0, a.getSurfaceLift(put(a, 3, TileType.ELEVATED, 1)));
        assertEquals(0.0, a.getSurfaceLift(new GridPos(9, 9)), "nothing off the grid");
    }

    @Test
    void onlyStairsAndRaisedFloorsAreOffTheArenasOwnFloor() {
        GridArena a = arena();
        put(a, 1, TileType.STAIR, 0);
        put(a, 2, TileType.ELEVATED, 0);
        put(a, 3, TileType.WATER, 0);
        assertTrue(a.getTile(new GridPos(0, 0)).isOnArenaFloor());
        assertFalse(a.getTile(new GridPos(1, 0)).isOnArenaFloor());
        assertFalse(a.getTile(new GridPos(2, 0)).isOnArenaFloor());
        assertTrue(a.getTile(new GridPos(3, 0)).isOnArenaFloor(), "water is still the floor of the room");
    }
}
