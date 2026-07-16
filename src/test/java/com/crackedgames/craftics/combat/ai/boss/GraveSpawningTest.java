package com.crackedgames.craftics.combat.ai.boss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraveSpawningTest {

    @Test
    void eachGraveAllowsOneZombie() {
        assertEquals(3, GraveSpawning.zombieCap(3, 1));
        assertEquals(1, GraveSpawning.zombieCap(1, 1));
    }

    @Test
    void noGravesMeansNoZombies() {
        // Destroying every grave shuts the stream off entirely. That is the reward.
        assertEquals(0, GraveSpawning.zombieCap(0, 1));
    }

    @Test
    void phaseTwoGravesRaiseTheCeiling() {
        // Phase 2 tears out 2 more graves: the ceiling the player thought they had lowered
        // jumps back up.
        assertEquals(5, GraveSpawning.zombieCap(5, 1));
    }

    @Test
    void absoluteCapIsTenInSinglePlayer() {
        // A safety net against runaway spawning, not a target. If a normal fight reaches it,
        // the spawn rate is mistuned, not the cap.
        assertEquals(10, GraveSpawning.zombieCap(50, 1));
    }

    @Test
    void absoluteCapGrowsByOnePerExtraPlayer() {
        assertEquals(11, GraveSpawning.zombieCap(50, 2));
        assertEquals(13, GraveSpawning.zombieCap(50, 4));
    }

    @Test
    void graveCountStillBindsBelowTheAbsoluteCap() {
        // The absolute cap must not become a floor: 2 graves in a 4-player game is still 2.
        assertEquals(2, GraveSpawning.zombieCap(2, 4));
    }

    @Test
    void degenerateInputsAreSafe() {
        assertEquals(0, GraveSpawning.zombieCap(-1, 1));
        assertEquals(3, GraveSpawning.zombieCap(3, 0));
    }
}
