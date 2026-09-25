package com.crackedgames.craftics.core;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Ender Dragon's lingering breath clouds: how long they last and how hard they bite.
 */
class GridArenaBreathCloudTest {

    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[6][6];
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        return new GridArena(6, 6, tiles, BlockPos.ORIGIN, 1, new GridPos(0, 0));
    }

    @Test
    void cloudLastsItsTurnsThenClears() {
        GridArena a = arena();
        GridPos p = new GridPos(2, 2);
        a.setBreathCloud(p, 3, 4);

        assertEquals(4, a.breathCloudDamage(p));
        assertTrue(a.tickBreathClouds().isEmpty());
        assertTrue(a.tickBreathClouds().isEmpty());
        assertTrue(a.hasBreathCloud(p), "still up after 2 of its 3 turns");
        assertEquals(List.of(p), a.tickBreathClouds());
        assertFalse(a.hasBreathCloud(p));
        assertEquals(0, a.breathCloudDamage(p));
    }

    @Test
    void cloudLandingOnACloudKeepsTheLongerAndTheHarder() {
        GridArena a = arena();
        GridPos p = new GridPos(1, 4);
        a.setBreathCloud(p, 2, 5);
        a.setBreathCloud(p, 3, 2);

        assertEquals(5, a.breathCloudDamage(p), "a weaker cloud must not soften a stronger one");
        a.tickBreathClouds();
        a.tickBreathClouds();
        assertTrue(a.hasBreathCloud(p), "the 3-turn cloud outlasts the 2-turn one it landed on");
    }

    @Test
    void cloudLayerCarriesTileTurnsAndBiteForTheClient() {
        GridArena a = arena();
        a.setBreathCloud(new GridPos(4, 1), 2, 5);
        a.tickBreathClouds(); // one round gone: 1 turn left

        assertArrayEquals(new int[]{4, 1, 1, 5}, a.breathCloudLayer());
    }

    @Test
    void cleanGroundHasNoCloud() {
        GridArena a = arena();
        assertFalse(a.hasBreathCloud(new GridPos(0, 0)));
        assertEquals(0, a.breathCloudDamage(new GridPos(0, 0)));
    }
}
