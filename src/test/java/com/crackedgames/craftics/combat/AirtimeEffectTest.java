package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Airtime is a stackable, 1-turn effect: applied by a Wind Charge self-launch, it grants
 * +2 ranged range and +0.5x next-hit damage PER LEVEL, and a weapon hit consumes ONE level.
 * These are the pure-logic guarantees the rest of the feature relies on.
 */
class AirtimeEffectTest {

    @Test
    void absentByDefault() {
        CombatEffects fx = new CombatEffects();
        assertFalse(fx.hasAirtime());
        assertEquals(0, fx.getAirtimeLevel());
        assertEquals(0, fx.consumeAirtimeStack(), "consuming nothing returns 0");
    }

    @Test
    void levelReflectsAmplifierPlusOne() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.AIRTIME, 1, 0); // amplifier 0 -> level I
        assertTrue(fx.hasAirtime());
        assertEquals(1, fx.getAirtimeLevel());

        fx.addEffect(EffectType.AIRTIME, 1, 2); // amplifier 2 -> level III
        assertEquals(3, fx.getAirtimeLevel());
    }

    @Test
    void consumeReturnsPreDecrementLevelAndDropsOneStack() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.AIRTIME, 1, 2); // level III
        assertEquals(3, fx.consumeAirtimeStack(), "returns the level in effect for this hit");
        assertEquals(2, fx.getAirtimeLevel(), "dropped to level II");
        assertEquals(2, fx.consumeAirtimeStack());
        assertEquals(1, fx.getAirtimeLevel());
        assertEquals(1, fx.consumeAirtimeStack());
        assertFalse(fx.hasAirtime(), "removed after the last stack");
        assertEquals(0, fx.getAirtimeLevel());
    }

    @Test
    void airtimeIsNotADebuff() {
        assertFalse(CombatEffects.isDebuff(EffectType.AIRTIME),
            "Airtime is a buff; it must tint as a buff and survive a cleanse");
    }

    @Test
    void stackCapConstantIsFour() {
        assertEquals(4, CombatEffects.AIRTIME_MAX_AMPLIFIER);
    }

    @Test
    void levitationExpiryIsReportedSoTheManagerCanGrantAirtime() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(CombatEffects.EffectType.LEVITATION, 1, 0); // 1 turn
        fx.tickTurn(); // ticks to 0 and expires
        assertTrue(fx.getLastExpired().contains(CombatEffects.EffectType.LEVITATION),
            "expiry must surface LEVITATION so CombatManager can grant Airtime I");
        // And the manager's response — granting Airtime I — yields level 1:
        fx.addEffect(CombatEffects.EffectType.AIRTIME, 1, 0);
        assertEquals(1, fx.getAirtimeLevel());
    }
}
