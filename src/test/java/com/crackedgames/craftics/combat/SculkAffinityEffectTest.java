package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deeper and Darker's Sculk Affinity potion as a combat effect.
 *
 * <p>The bug this guards: the potion mapping only recognised vanilla effects, by object identity,
 * so a modded potion matched nothing. It was drunk, cost its AP, and did nothing at all.
 */
class SculkAffinityEffectTest {

    @Test
    void theModdedEffectIdMapsToSculkAffinity() {
        assertEquals(EffectType.SCULK_AFFINITY, CombatEffects.moddedEffect("deeperdarker", "sculk_affinity"));
    }

    @Test
    void lookalikeIdsDoNotMap() {
        // Same path under another mod, or another effect from the same mod, is not this effect.
        assertNull(CombatEffects.moddedEffect("minecraft", "sculk_affinity"));
        assertNull(CombatEffects.moddedEffect("deeperdarker", "something_else"));
        assertNull(CombatEffects.moddedEffect(null, null));
    }

    @Test
    void itIsABuffAndNotADebuff() {
        assertTrue(CombatEffects.isBuff(EffectType.SCULK_AFFINITY));
        assertFalse(CombatEffects.isDebuff(EffectType.SCULK_AFFINITY));
    }

    @Test
    void aPlayerWhoDrankItHasItUntilItRunsOut() {
        CombatEffects effects = new CombatEffects();
        assertFalse(effects.hasEffect(EffectType.SCULK_AFFINITY));

        effects.addEffect(EffectType.SCULK_AFFINITY, 2, 0);
        assertTrue(effects.hasEffect(EffectType.SCULK_AFFINITY));

        effects.tickTurn();
        effects.tickTurn();
        assertFalse(effects.hasEffect(EffectType.SCULK_AFFINITY), "gone after its 2 turns");
    }
}
