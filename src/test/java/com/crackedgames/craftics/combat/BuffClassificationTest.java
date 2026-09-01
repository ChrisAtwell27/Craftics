package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What counts as a buff, which is what the Alchemist feat is counting.
 *
 * <p>The feat asks for five different buffs at once. That number is only meaningful if "buff" has
 * a stable answer, and the failure mode is quiet in both directions: classify too much and the
 * feat is handed out for standing in a poison cloud, classify too little and it is unreachable -
 * which is the state it was already in, since nothing ever fed the counter at all.
 */
class BuffClassificationTest {

    @Test
    @DisplayName("nothing is both a buff and a debuff")
    void theTwoSetsDoNotOverlap() {
        for (EffectType type : EffectType.values()) {
            assertFalse(CombatEffects.isBuff(type) && CombatEffects.isDebuff(type),
                type + " is classified as both a buff and a debuff");
        }
    }

    @Test
    @DisplayName("the obvious buffs are buffs and the obvious harms are not")
    void theClassificationIsRight() {
        for (EffectType good : new EffectType[]{EffectType.SPEED, EffectType.STRENGTH,
                EffectType.RESISTANCE, EffectType.REGENERATION, EffectType.ABSORPTION,
                EffectType.HASTE, EffectType.FIRE_RESISTANCE}) {
            assertTrue(CombatEffects.isBuff(good), good + " should count toward Alchemist");
        }
        for (EffectType bad : new EffectType[]{EffectType.POISON, EffectType.WITHER,
                EffectType.BLEEDING, EffectType.WEAKNESS, EffectType.VULNERABLE,
                EffectType.MARKED, EffectType.SOAKED}) {
            assertFalse(CombatEffects.isBuff(bad), bad + " must never count toward Alchemist");
        }
    }

    @Test
    @DisplayName("a positional state is not a buff")
    void neutralStatesDoNotCount() {
        // AIRTIME is where the entity is, not something helping it. It is the reason isBuff is a
        // whitelist rather than !isDebuff: under a negation this would silently count.
        assertFalse(CombatEffects.isBuff(EffectType.AIRTIME));
        assertFalse(CombatEffects.isDebuff(EffectType.AIRTIME));
    }

    @Test
    @DisplayName("the feat is reachable: at least five buffs exist to stack")
    void fiveBuffsArePossible() {
        long buffs = java.util.Arrays.stream(EffectType.values())
            .filter(CombatEffects::isBuff).count();
        assertTrue(buffs >= 5,
            "Alchemist wants 5 simultaneous buffs but only " + buffs + " buffs exist");
    }

    @Test
    @DisplayName("counting active buffs ignores debuffs entirely")
    void theCountIsBuffsOnly() {
        CombatEffects fx = new CombatEffects();
        assertEquals(0, fx.countActiveBuffs(), "a fresh effect set holds nothing");

        fx.addEffect(EffectType.POISON, 5, 0);
        fx.addEffect(EffectType.WITHER, 5, 0);
        assertEquals(0, fx.countActiveBuffs(), "debuffs must not count toward Alchemist");

        fx.addEffect(EffectType.SPEED, 5, 0);
        fx.addEffect(EffectType.STRENGTH, 5, 0);
        fx.addEffect(EffectType.RESISTANCE, 5, 0);
        fx.addEffect(EffectType.HASTE, 5, 0);
        assertEquals(4, fx.countActiveBuffs());

        // Re-applying one already held is a refresh, not a fifth buff. The feat says five
        // DIFFERENT buffs, and drinking the same potion twice must not earn it.
        fx.addEffect(EffectType.SPEED, 9, 1);
        assertEquals(4, fx.countActiveBuffs(), "re-applying a held buff is not a new one");

        fx.addEffect(EffectType.ABSORPTION, 5, 0);
        assertTrue(fx.countActiveBuffs() >= 5, "five distinct buffs should reach the threshold");
    }
}
