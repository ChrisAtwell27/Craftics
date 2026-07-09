package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InfiniteScoreTest {

    @Test
    void levelFastClearFullPoints() {
        // 5 base, 0-1 turns -> no penalty
        assertEquals(5, InfiniteRunManager.clearPoints(InfiniteRunManager.LEVEL_POINTS, 0));
        assertEquals(5, InfiniteRunManager.clearPoints(InfiniteRunManager.LEVEL_POINTS, 1));
    }

    @Test
    void levelPenaltyOnePerTwoTurns() {
        assertEquals(4, InfiniteRunManager.clearPoints(InfiniteRunManager.LEVEL_POINTS, 2)); // -1
        assertEquals(4, InfiniteRunManager.clearPoints(InfiniteRunManager.LEVEL_POINTS, 3)); // still -1
        assertEquals(3, InfiniteRunManager.clearPoints(InfiniteRunManager.LEVEL_POINTS, 4)); // -2
    }

    @Test
    void levelFloorsAtOne() {
        // 5 - 8/2 = 1; 5 - 20/2 = -5 -> floored to 1
        assertEquals(1, InfiniteRunManager.clearPoints(InfiniteRunManager.LEVEL_POINTS, 8));
        assertEquals(1, InfiniteRunManager.clearPoints(InfiniteRunManager.LEVEL_POINTS, 20));
    }

    @Test
    void bossFastClearFullPoints() {
        assertEquals(10, InfiniteRunManager.clearPoints(InfiniteRunManager.BOSS_POINTS, 0));
        assertEquals(10, InfiniteRunManager.clearPoints(InfiniteRunManager.BOSS_POINTS, 1));
    }

    @Test
    void bossPenaltyAndFloor() {
        assertEquals(6, InfiniteRunManager.clearPoints(InfiniteRunManager.BOSS_POINTS, 8));  // 10 - 4
        assertEquals(1, InfiniteRunManager.clearPoints(InfiniteRunManager.BOSS_POINTS, 18)); // 10 - 9 = 1
        assertEquals(1, InfiniteRunManager.clearPoints(InfiniteRunManager.BOSS_POINTS, 40)); // floored
    }

    @Test
    void negativeTurnsTreatedAsZero() {
        assertEquals(5, InfiniteRunManager.clearPoints(InfiniteRunManager.LEVEL_POINTS, -3));
    }
}
