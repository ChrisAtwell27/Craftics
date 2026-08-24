package com.crackedgames.craftics.combat.miniboss;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A block-backed object - a bee hive, a grave, a sculk sensor - must never be placed over a
 * hole in the arena floor.
 *
 * <p>These pin a real bug. {@code MinibossSpawns.findOpen} knows the arena's width and height
 * and nothing else, so it happily returned a VOID tile, and the block placers put a solid block
 * in the pit: it reads as ground, the grid still says the tile kills you, and mining it reverts
 * the tile to plain NORMAL floor with no world block underneath. Mob spawns were never affected
 * - they run through {@code canPlaceSpawnAt}, which has always checked {@code isSafeForSpawn}.
 */
class BlockObjectFloorTest {

    private static final int SIZE = 9;

    private static GridArena arenaOfVoidExcept(GridPos... floor) {
        GridTile[][] tiles = new GridTile[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                tiles[x][z] = new GridTile(TileType.VOID, null);
            }
        }
        GridArena arena = new GridArena(SIZE, SIZE, tiles, BlockPos.ORIGIN, 1, new GridPos(SIZE / 2, 0));
        for (GridPos p : floor) {
            arena.setTile(p, new GridTile(TileType.NORMAL, null));
        }
        return arena;
    }

    @Test
    void voidTilesAreNotPlaceable() {
        GridArena arena = arenaOfVoidExcept(new GridPos(3, 3));
        assertFalse(arena.isPlaceableFloor(new GridPos(4, 4)), "VOID tile must not take a block");
        assertTrue(arena.isPlaceableFloor(new GridPos(3, 3)));
    }

    @Test
    void outOfBoundsAndNullAreNotPlaceable() {
        GridArena arena = arenaOfVoidExcept(new GridPos(3, 3));
        assertFalse(arena.isPlaceableFloor(null));
        assertFalse(arena.isPlaceableFloor(new GridPos(-1, 3)));
        assertFalse(arena.isPlaceableFloor(new GridPos(3, SIZE)));
    }

    @Test
    void obstacleAndDeepWaterAreNotPlaceable() {
        GridArena arena = arenaOfVoidExcept(new GridPos(3, 3));
        arena.setTile(new GridPos(2, 3), new GridTile(TileType.OBSTACLE, null));
        arena.setTile(new GridPos(2, 4), new GridTile(TileType.DEEP_WATER, null));
        assertFalse(arena.isPlaceableFloor(new GridPos(2, 3)));
        assertFalse(arena.isPlaceableFloor(new GridPos(2, 4)));
    }

    @Test
    void pickerWithFloorTestNeverReturnsAVoidTile() {
        // One island of real floor in an arena that is otherwise a pit, inside the picker's
        // spawn-safe rectangle (x in [1, w-2], z in [2, h-3]).
        GridPos island = new GridPos(4, 4);
        GridArena arena = arenaOfVoidExcept(island);
        Random rng = new Random(1234);
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(SIZE / 2, 0));
        for (int i = 0; i < 200; i++) {
            GridPos pos = MinibossSpawns.findOpen(SIZE, SIZE, used, rng, arena::isPlaceableFloor);
            // Null is a fine answer - "nowhere to put it" beats "over the pit".
            if (pos != null) assertEquals(island, pos);
        }
    }

    @Test
    void pickerWithFloorTestReturnsNullWhenTheArenaIsAllPit() {
        GridArena arena = arenaOfVoidExcept();
        Random rng = new Random(99);
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(SIZE / 2, 0));
        assertNull(MinibossSpawns.findOpen(SIZE, SIZE, used, rng, arena::isPlaceableFloor));
        assertNull(MinibossSpawns.findOpenBiased(SIZE, SIZE, used, rng, SIZE / 2, SIZE / 2,
            arena::isPlaceableFloor));
    }

    @Test
    void theUnfilteredPickerStillAcceptsAnything() {
        // The 4-arg overload is what every MOB mechanic uses, and those resolve their own tile
        // downstream. It must keep its old behaviour.
        Random rng = new Random(7);
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(SIZE / 2, 0));
        assertNotNull(MinibossSpawns.findOpen(SIZE, SIZE, used, rng));
    }
}
