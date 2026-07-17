package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * removeFirstDebuff powers the Reversal enchant's cleanse: while at or below a quarter HP, a hit
 * strips one debuff off the wielder. It removes exactly one harmful effect and leaves buffs alone.
 */
class CombatEffectsRemoveFirstDebuffTest {

    @Test
    void removesOneDebuffAndReturnsIt() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.POISON, 4, 0);

        EffectType removed = fx.removeFirstDebuff();

        assertEquals(EffectType.POISON, removed);
        assertFalse(fx.hasEffect(EffectType.POISON), "the debuff must be gone");
    }

    @Test
    void leavesBuffsUntouched() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.STRENGTH, 4, 0);

        EffectType removed = fx.removeFirstDebuff();

        assertNull(removed, "there is no debuff to remove");
        assertTrue(fx.hasEffect(EffectType.STRENGTH), "the buff must remain");
    }

    @Test
    void removesOnlyOneDebuffPerCall() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.POISON, 4, 0);
        fx.addEffect(EffectType.WITHER, 4, 0);

        EffectType removed = fx.removeFirstDebuff();

        assertNotNull(removed);
        assertTrue(CombatEffects.isDebuff(removed));
        // Exactly one of the two debuffs was cleared, the other stays.
        boolean poisonGone = !fx.hasEffect(EffectType.POISON);
        boolean witherGone = !fx.hasEffect(EffectType.WITHER);
        assertTrue(poisonGone ^ witherGone, "exactly one debuff must be removed per call");
    }

    @Test
    void returnsNullWhenNoDebuffsPresent() {
        CombatEffects fx = new CombatEffects();
        assertNull(fx.removeFirstDebuff(), "an empty effect set has nothing to cleanse");
    }
}
