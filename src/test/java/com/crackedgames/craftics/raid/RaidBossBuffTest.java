package com.crackedgames.craftics.raid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaidBossBuffTest {

    @Test
    void resolvesByLowercaseId() {
        assertEquals(RaidBossBuff.STRENGTH, RaidBossBuff.of("strength"));
        assertEquals(RaidBossBuff.FIRE_RESISTANCE, RaidBossBuff.of("fire_resistance"));
        assertEquals(RaidBossBuff.REGENERATION, RaidBossBuff.of("REGENERATION"));
        assertNull(RaidBossBuff.of("haste"));
        assertNull(RaidBossBuff.of(null));
    }

    @Test
    void amplifierZeroIsLevelOne() {
        assertEquals(3, RaidBossBuff.attackBonus(0));
        assertEquals(2, RaidBossBuff.defenseBonus(0));
        assertEquals(2, RaidBossBuff.speedBonus(0));
        assertEquals(2, RaidBossBuff.regenPerTurn(0));
    }

    @Test
    void bonusesScaleLinearlyWithAmplifier() {
        assertEquals(6, RaidBossBuff.attackBonus(1));
        assertEquals(9, RaidBossBuff.attackBonus(2));
        assertEquals(4, RaidBossBuff.defenseBonus(1));
        assertEquals(6, RaidBossBuff.speedBonus(2));
        assertEquals(8, RaidBossBuff.regenPerTurn(3));
    }

    @Test
    void negativeAmplifiersClampToLevelOne() {
        assertEquals(3, RaidBossBuff.attackBonus(-5));
        assertEquals(2, RaidBossBuff.regenPerTurn(-1));
    }

    @Test
    void absorptionIsAQuarterOfBaseHpPerLevel() {
        assertEquals(225, RaidBossBuff.absorptionBonus(900, 0));
        assertEquals(450, RaidBossBuff.absorptionBonus(900, 1));
        assertEquals(0, RaidBossBuff.absorptionBonus(0, 3));
    }

    @Test
    void displayNamesAreHumanReadable() {
        assertEquals("Strength", RaidBossBuff.STRENGTH.displayName());
        assertEquals("Fire Resistance", RaidBossBuff.FIRE_RESISTANCE.displayName());
    }
}
