package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enemy-side Airtime/Levitation is visual+movement only: state fields with a turn count, a
 * per-level movement slow for Levitation, and no damage hooks. These are the pure-logic
 * guarantees Tasks 4-7 build on.
 */
class CombatEntityAirLevTest {

    private static CombatEntity mob() {
        // Explicit speedOverride=4 so base getMoveSpeed() is deterministic:
        // CONFIG is null in tests -> enemyMoveSpeedMultiplier defaults to 1.0,
        // and this entity is non-ally so the floor is max(1, ...).
        return new CombatEntity(1, "minecraft:zombie", new GridPos(0, 0), 20, 5, 2, 4, -1, 4);
    }

    @Test
    void airtimeStateApplyHasTick() {
        CombatEntity e = mob();
        assertFalse(e.hasAirtimeState());
        e.applyAirtimeState(1);
        assertTrue(e.hasAirtimeState());
        assertEquals(1, e.getAirtimeStateTurns());
        e.tickAirtimeState();
        assertFalse(e.hasAirtimeState());
    }

    @Test
    void levitationStateSlowsPerLevel() {
        CombatEntity e = mob();
        int base = e.getMoveSpeed();
        assertEquals(4, base);
        e.applyLevitationState(3, 0); // level I -> -1
        assertEquals(Math.max(1, base - 1), e.getMoveSpeed());
        e.applyLevitationState(3, 1); // level II -> -2 (amplifier max-stacks)
        assertEquals(Math.max(1, base - 2), e.getMoveSpeed());
        assertTrue(e.hasLevitationState());
        assertEquals(1, e.getLevitationStateAmplifier());
    }

    @Test
    void levitationStateTicksDown() {
        CombatEntity e = mob();
        e.applyLevitationState(2, 0);
        e.tickLevitationState();
        assertTrue(e.hasLevitationState());
        e.tickLevitationState();
        assertFalse(e.hasLevitationState());
    }
}
