package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the pure hybrid-mechanic math in {@link HybridSetEffects}. */
class HybridSetEffectsTest {

    @Test
    void stonewall_capsIncomingDamage() {
        assertEquals(6, HybridSetEffects.capIncomingDamage(10));
        assertEquals(6, HybridSetEffects.capIncomingDamage(6));
        assertEquals(3, HybridSetEffects.capIncomingDamage(3));
        assertEquals(0, HybridSetEffects.capIncomingDamage(0));
    }

    @Test
    void berserker_critChanceScalesWithMissingHp() {
        assertEquals(0.0,  HybridSetEffects.berserkerCritChance(20f, 20f), 1e-9);
        assertEquals(0.25, HybridSetEffects.berserkerCritChance(10f, 20f), 1e-9);
        assertEquals(0.5,  HybridSetEffects.berserkerCritChance(0f,  20f), 1e-9);
        assertEquals(0.0,  HybridSetEffects.berserkerCritChance(10f, 0f),  1e-9);
    }

    @Test
    void luckyStreak_critChancePerKill() {
        assertEquals(0.0, HybridSetEffects.luckyStreakCritChance(0), 1e-9);
        assertEquals(0.3, HybridSetEffects.luckyStreakCritChance(3), 1e-9);
    }
}
