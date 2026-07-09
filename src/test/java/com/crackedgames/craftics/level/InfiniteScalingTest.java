package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InfiniteScalingTest {
    @Test
    void bossHpCurve() {
        assertEquals(70,  InfiniteScaling.bossHp(0, 70, 50));
        assertEquals(120, InfiniteScaling.bossHp(1, 70, 50));
        assertEquals(170, InfiniteScaling.bossHp(2, 70, 50));
        assertEquals(320, InfiniteScaling.bossHp(5, 70, 50));
    }
    @Test
    void bossHpNegativeOrdinalClampsToBase() {
        assertEquals(70, InfiniteScaling.bossHp(-3, 70, 50));
    }
    @Test
    void enemyHpCurve() {
        assertEquals(8,  InfiniteScaling.enemyHp(0, 0, 8, 2, 8)); // biome1 L1
        assertEquals(10, InfiniteScaling.enemyHp(1, 0, 8, 2, 8)); // biome1 L2
        assertEquals(16, InfiniteScaling.enemyHp(0, 1, 8, 2, 8)); // biome2 L1
        assertEquals(20, InfiniteScaling.enemyHp(2, 1, 8, 2, 8)); // biome2 L3
    }
    @Test
    void enemyHpNegativeClamps() {
        assertEquals(8, InfiniteScaling.enemyHp(-1, -1, 8, 2, 8));
    }
    @Test
    void floorsAtOne() {
        assertEquals(1, InfiniteScaling.bossHp(0, 0, 0));
        assertEquals(1, InfiniteScaling.enemyHp(0, 0, 0, 0, 0));
    }
}
