package com.crackedgames.craftics.core;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which below-floor falls kill, and which are rescued.
 *
 * <p>A player who drops below the floor used to die on the spot whatever was under them, which is
 * what "insta-killed walking on a normal tile" was. The rule now is narrow on purpose: a fall only
 * kills over a VOID tile. Over anything else - ground whose block has gone missing, deep water, a
 * wall, the edge of the arena - the world and the grid disagree, and the player is put back.
 */
class GridArenaFallThroughTest {

    private static final BlockPos ORIGIN = new BlockPos(100, 64, 200);

    /** A 4x4 arena of plain floor sitting at {@link #ORIGIN}. */
    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[4][4];
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        return new GridArena(4, 4, tiles, ORIGIN, 1, new GridPos(0, 0));
    }

    /** Built through the constructor: {@code setType} resolves a vanilla block, and this harness has no bootstrap. */
    private static void paint(GridArena a, TileType type, int x, int z) {
        a.setTile(new GridPos(x, z), new GridTile(type, null));
    }

    @Test
    void columnMapsToItsGridTile() {
        GridArena a = arena();
        assertEquals(new GridPos(2, 3), a.gridPosAtColumn(102, 203));
    }

    @Test
    void offTheEdgeIsNoTile() {
        GridArena a = arena();
        assertNull(a.gridPosAtColumn(99, 200));
        assertNull(a.gridPosAtColumn(104, 200));
        assertNull(a.gridPosAtColumn(100, 204));
    }

    @Test
    void onlyAVoidTileKills() {
        GridArena a = arena();
        paint(a, TileType.VOID, 1, 1);
        assertTrue(a.fallIsLethalAt(101, 201));
    }

    @Test
    void groundThatHasGoneMissingDoesNotKill() {
        GridArena a = arena();
        paint(a, TileType.WATER, 0, 0);
        paint(a, TileType.LAVA, 1, 0);
        paint(a, TileType.LOW_GROUND, 2, 0);
        paint(a, TileType.TALL_GRASS, 3, 0);
        assertFalse(a.fallIsLethalAt(100, 203)); // NORMAL
        assertFalse(a.fallIsLethalAt(100, 200));
        assertFalse(a.fallIsLethalAt(101, 200));
        assertFalse(a.fallIsLethalAt(102, 200));
        assertFalse(a.fallIsLethalAt(103, 200));
    }

    @Test
    void deepWaterAndWallsDoNotKill() {
        GridArena a = arena();
        paint(a, TileType.DEEP_WATER, 2, 2);
        paint(a, TileType.OBSTACLE, 3, 3);
        assertFalse(a.fallIsLethalAt(102, 202));
        assertFalse(a.fallIsLethalAt(103, 203));
    }

    @Test
    void offTheEdgeDoesNotKill() {
        GridArena a = arena();
        assertFalse(a.fallIsLethalAt(99, 200));
        assertFalse(a.fallIsLethalAt(100, 204));
    }
}
