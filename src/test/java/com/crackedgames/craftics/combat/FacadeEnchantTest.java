package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Facade: an axe hits 1.5x while its bearer is suffering a debuff.
 *
 * <p>Only the two halves that are free of Minecraft types are testable here: the damage math
 * ({@link AxeEnchantEffects#facadeDamage}) and the debuff test
 * ({@link AxeEnchantEffects#hasAnyDebuff}). The held-axe check needs an ItemStack and the test JVM
 * has no MC bootstrap, so {@code facadeActive} and the CombatManager wiring stay on the in-game
 * checklist.
 */
class FacadeEnchantTest {

    @Test
    void damageWithADebuff_is1_5xTheDamageWithout() {
        for (int base = 1; base <= 100; base++) {
            int without = AxeEnchantEffects.facadeDamage(base, false);
            int with = AxeEnchantEffects.facadeDamage(base, true);
            assertEquals(base, without, "an inactive Facade must not touch the damage");
            assertEquals(Math.round(base * 1.5), with, "Facade should be 1.5x of " + base);
            assertTrue(with > without, "Facade must never be a downgrade at base " + base);
        }
    }

    @Test
    void facadeRounds_ratherThanTruncating() {
        // The whole point of Math.round over an int cast: an odd base keeps its half point.
        assertEquals(8, AxeEnchantEffects.facadeDamage(5, true), "5 * 1.5 = 7.5 should round to 8");
        assertEquals(15, AxeEnchantEffects.facadeDamage(10, true));
        assertEquals(2, AxeEnchantEffects.facadeDamage(1, true), "1 * 1.5 = 1.5 should round to 2");
    }

    @Test
    void nonPositiveDamage_isLeftAlone() {
        // An immune target has already been zeroed; Facade must not resurrect the hit.
        assertEquals(0, AxeEnchantEffects.facadeDamage(0, true));
        assertEquals(-3, AxeEnchantEffects.facadeDamage(-3, true));
    }

    @Test
    void everyDebuff_armsFacade() {
        // Drives the real isDebuff list rather than a copy of it, so an effect added to
        // CombatEffects.isDebuff later is covered here without touching this test.
        int debuffs = 0;
        for (CombatEffects.EffectType type : CombatEffects.EffectType.values()) {
            if (!CombatEffects.isDebuff(type)) continue;
            debuffs++;
            CombatEffects effects = new CombatEffects();
            effects.addEffect(type, 3, 0);
            assertTrue(AxeEnchantEffects.hasAnyDebuff(effects),
                type + " is a debuff, so it should arm Facade");
        }
        assertTrue(debuffs > 0, "isDebuff should classify at least one effect as harmful");
    }

    @Test
    void buffsAlone_doNotArmFacade() {
        for (CombatEffects.EffectType type : CombatEffects.EffectType.values()) {
            if (CombatEffects.isDebuff(type)) continue;
            CombatEffects effects = new CombatEffects();
            effects.addEffect(type, 3, 0);
            assertFalse(AxeEnchantEffects.hasAnyDebuff(effects),
                type + " is not a debuff, so it must not arm Facade");
        }
    }

    @Test
    void noEffectsAtAll_doNotArmFacade() {
        assertFalse(AxeEnchantEffects.hasAnyDebuff(new CombatEffects()));
        assertFalse(AxeEnchantEffects.hasAnyDebuff(null));
    }

    @Test
    void aBuffAlongsideADebuff_stillArmsFacade() {
        CombatEffects effects = new CombatEffects();
        effects.addEffect(CombatEffects.EffectType.STRENGTH, 3, 0);
        effects.addEffect(CombatEffects.EffectType.POISON, 3, 0);
        assertTrue(AxeEnchantEffects.hasAnyDebuff(effects),
            "a debuff should arm Facade regardless of any buffs held alongside it");
    }

    @Test
    void facadeIsASingleLevelEnchantment() {
        assertEquals(1, CrafticsEnchantments.FACADE.maxLevel(),
            "the 1.5x is flat, so Facade has no per-level curve");
        assertEquals(CrafticsEnchantments.Tool.AXE, CrafticsEnchantments.FACADE.tool());
    }
}
