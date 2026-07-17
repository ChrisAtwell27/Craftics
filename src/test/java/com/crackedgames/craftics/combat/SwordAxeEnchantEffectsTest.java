package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwordAxeEnchantEffectsTest {

    @Test
    void executionerIsOnePerDebuffPerLevel() {
        assertEquals(0, SwordAxeEnchantEffects.executionerBonus(0, 1));
        assertEquals(3, SwordAxeEnchantEffects.executionerBonus(3, 1));
        assertEquals(6, SwordAxeEnchantEffects.executionerBonus(3, 2));
        assertEquals(9, SwordAxeEnchantEffects.executionerBonus(3, 3));
    }

    @Test
    void executionerZeroWhenUnenchanted() {
        assertEquals(0, SwordAxeEnchantEffects.executionerBonus(5, 0));
    }

    @Test
    void executionerNegativeInputsAreSafe() {
        assertEquals(0, SwordAxeEnchantEffects.executionerBonus(-1, 3));
        assertEquals(0, SwordAxeEnchantEffects.executionerBonus(3, -1));
    }

    @Test
    void packBondCountsOtherPetsPerLevel() {
        // livingPetCount is the TOTAL alive; the acting pet does not buff itself.
        assertEquals(0, SwordAxeEnchantEffects.packBondBonus(1, 1)); // alone
        assertEquals(2, SwordAxeEnchantEffects.packBondBonus(3, 1)); // 2 others
        assertEquals(4, SwordAxeEnchantEffects.packBondBonus(3, 2));
    }

    @Test
    void packBondZeroWhenUnenchantedOrAlone() {
        assertEquals(0, SwordAxeEnchantEffects.packBondBonus(4, 0));
        assertEquals(0, SwordAxeEnchantEffects.packBondBonus(0, 3));
    }

    @Test
    void serratedBleedEqualsLevel() {
        assertEquals(0, SwordAxeEnchantEffects.serratedBleedStacks(0));
        assertEquals(1, SwordAxeEnchantEffects.serratedBleedStacks(1));
        assertEquals(3, SwordAxeEnchantEffects.serratedBleedStacks(3));
    }

    @Test
    void reversalLowHpThresholdIsAtOrBelowQuarter() {
        assertTrue(SwordAxeEnchantEffects.isLowHp(5, 20));   // exactly 25%
        assertTrue(SwordAxeEnchantEffects.isLowHp(4, 20));   // below
        assertFalse(SwordAxeEnchantEffects.isLowHp(6, 20));  // above
        assertFalse(SwordAxeEnchantEffects.isLowHp(20, 20)); // full
    }

    @Test
    void reversalMultIsThreeHalves() {
        assertEquals(1.5, SwordAxeEnchantEffects.REVERSAL_MULT, 0.0001);
    }

    @Test
    void hiltCutsToAQuarter() {
        assertEquals(3, SwordAxeEnchantEffects.hiltDamage(10)); // round(10*0.25)=round(2.5)=3
        assertEquals(1, SwordAxeEnchantEffects.hiltDamage(2));  // round(0.5)=1, floored to >=1
        assertEquals(1, SwordAxeEnchantEffects.hiltDamage(1));  // never below 1 on a real hit
        assertEquals(0, SwordAxeEnchantEffects.hiltDamage(0));  // a non-positive base passes through
    }

    @Test
    void dullCutsToAHalf() {
        assertEquals(5, SwordAxeEnchantEffects.dullDamage(10)); // round(10*0.5)=5
        assertEquals(1, SwordAxeEnchantEffects.dullDamage(1));  // round(0.5)=1
        assertEquals(0, SwordAxeEnchantEffects.dullDamage(0));  // a non-positive base passes through
    }

    @Test
    void hiltAndDullMultsAreQuarterAndHalf() {
        assertEquals(0.25, SwordAxeEnchantEffects.HILT_MULT, 0.0001);
        assertEquals(0.5, SwordAxeEnchantEffects.DULL_MULT, 0.0001);
    }
}
