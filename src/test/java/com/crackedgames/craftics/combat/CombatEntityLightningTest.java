package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatEntityLightningTest {

    // defense 0 so lightning math is exact (no DEF reduction).
    private static CombatEntity enemy(int maxHp) {
        return new CombatEntity(1, "minecraft:zombie", new GridPos(0, 0), maxHp, 3, 0, 1);
    }

    @Test
    void isSoaked_reflectsSoakedTurns() {
        CombatEntity e = enemy(20);
        assertFalse(e.isSoaked());
        e.setSoakedTurns(2);
        assertTrue(e.isSoaked());
        e.setSoakedTurns(0);
        assertFalse(e.isSoaked());
    }

    @Test
    void lightningDamage_singleWhenDry() {
        CombatEntity e = enemy(20);
        int dealt = e.takeLightningDamage(5);
        assertEquals(5, dealt, "dry target takes raw lightning damage");
        assertEquals(15, e.getCurrentHp());
    }

    @Test
    void lightningDamage_doubleWhenSoaked() {
        CombatEntity e = enemy(20);
        e.setSoakedTurns(2);
        int dealt = e.takeLightningDamage(5);
        assertEquals(10, dealt, "soaked target takes 2x lightning damage");
        assertEquals(10, e.getCurrentHp());
    }

    @Test
    void specialLightningDamage_doublesFlatPlusPercentWhenSoaked() {
        // maxHp 100, pct 0.10 => percentMaxHpDamage = 10; base = flat(5) + 10 = 15.
        CombatEntity dry = enemy(100);
        assertEquals(15, dry.takeSpecialLightningDamage(5, 0.10), "dry: flat + %maxHp");

        CombatEntity wet = enemy(100);
        wet.setSoakedTurns(2);
        assertEquals(30, wet.takeSpecialLightningDamage(5, 0.10), "soaked: 2x of (flat + %maxHp)");
    }
}
