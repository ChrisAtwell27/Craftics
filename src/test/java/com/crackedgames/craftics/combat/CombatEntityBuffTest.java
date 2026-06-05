package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatEntityBuffTest {

    private static CombatEntity ally(int maxHp) {
        return new CombatEntity(1, "minecraft:iron_golem", new GridPos(0, 0), maxHp, 3, 0, 1);
    }

    @Test
    void regeneration_healsPerTickThenExpires() {
        CombatEntity e = ally(20);
        e.takeDamage(10);                 // currentHp = 10
        e.applyRegeneration(2, 0);        // 2 turns, +2 HP/turn (level I)
        e.tickBuffs();                    // +2 -> 12, 1 turn left
        assertEquals(12, e.getCurrentHp());
        e.tickBuffs();                    // +2 -> 14, expired
        assertEquals(14, e.getCurrentHp());
        e.tickBuffs();                    // no more regen
        assertEquals(14, e.getCurrentHp());
    }

    @Test
    void absorption_addsTemporaryHpAndDecays() {
        CombatEntity e = ally(20);
        e.applyAbsorption(4, 3);
        assertEquals(4, e.getAbsorption());
        e.tickBuffs();
        assertEquals(4, e.getAbsorption());
    }

    @Test
    void slowFalling_flagActiveWhileTurnsRemain() {
        CombatEntity e = ally(20);
        assertFalse(e.hasSlowFalling());
        e.applySlowFalling(2);
        assertTrue(e.hasSlowFalling());
        e.tickBuffs();
        assertTrue(e.hasSlowFalling());
        e.tickBuffs();
        assertFalse(e.hasSlowFalling());
    }

    @Test
    void timedAttackBuff_expires() {
        CombatEntity e = ally(20);
        e.applyAttackBuff(3, 2);
        assertEquals(3, e.getAttackBuffBonus());
        e.tickBuffs();
        assertEquals(3, e.getAttackBuffBonus());
        e.tickBuffs();
        assertEquals(0, e.getAttackBuffBonus());
    }

    @Test
    void absorption_soaksDamageBeforeRealHp() {
        CombatEntity e = ally(20);
        e.applyAbsorption(5, 3);
        int dealt = e.takeDamage(8);   // 5 soaked by absorption, 3 to real HP
        assertEquals(0, e.getAbsorption(), "absorption fully consumed");
        assertEquals(17, e.getCurrentHp(), "only 3 reached real HP");
        assertTrue(dealt >= 3);
    }

    @Test
    void resistance_reducesIncomingDamage() {
        CombatEntity e = ally(20);
        e.applyResistance(2, 3);       // -2 per hit
        e.takeDamage(8);
        assertEquals(14, e.getCurrentHp(), "8 - 2 resistance = 6 dealt");
    }

    @Test
    void clearDebuffs_removesNegativeStatusButKeepsBuffs() {
        CombatEntity e = ally(20);
        e.stackPoison(3, 1);
        e.stackSlowness(3, 1);
        e.applyRegeneration(3, 0);     // a buff — must survive cleanse
        e.clearDebuffs();
        assertEquals(0, e.getPoisonTurns(), "poison cleared");
        assertEquals(0, e.getSlownessTurns(), "slowness cleared");
        // regen buff still active: it heals on the next tick
        e.takeDamage(5);               // 20 -> 15
        e.tickBuffs();                 // +2 regen -> 17
        assertEquals(17, e.getCurrentHp(), "buffs survive a debuff cleanse");
    }
}
