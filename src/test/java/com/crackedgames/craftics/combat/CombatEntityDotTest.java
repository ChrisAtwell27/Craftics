package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the per-tick DOT damage formulas on {@link CombatEntity} —
 * poison, wither, and the {@code maxHp / 20} scaling shared between them.
 *
 * <p>Tick application itself is in {@code CombatManager.tickEnemyDeciding}
 * and is verified by the in-game smoke checklist; this file covers the
 * pure formula on the entity side.
 */
class CombatEntityDotTest {

    private static CombatEntity makeEnemy(int maxHp) {
        return new CombatEntity(1, "minecraft:zombie", new GridPos(0, 0),
            maxHp, 3, 0, 1);
    }

    // ---- Max-HP DOT bonus ----

    @Test
    void maxHpDotBonus_floorsAtOne() {
        // tiny mob: 5 HP / 20 = 0, but floored to 1
        assertEquals(1, makeEnemy(5).getMaxHpDotBonus());
        assertEquals(1, makeEnemy(1).getMaxHpDotBonus());
    }

    @Test
    void maxHpDotBonus_scalesLinearlyAfterFloor() {
        assertEquals(1, makeEnemy(20).getMaxHpDotBonus());   // 20/20 = 1
        assertEquals(2, makeEnemy(40).getMaxHpDotBonus());   // 40/20 = 2
        assertEquals(5, makeEnemy(100).getMaxHpDotBonus());  // 100/20 = 5
        assertEquals(10, makeEnemy(200).getMaxHpDotBonus()); // 200/20 = 10
    }

    // ---- Poison tick formula: 1 + amplifier + maxHpBonus ----

    @Test
    void poisonTickDamage_zeroIfNotPoisoned() {
        assertEquals(0, makeEnemy(20).getPoisonTickDamage());
    }

    @Test
    void poisonTickDamage_zombie_baseAmpAndMaxHpBonus() {
        CombatEntity e = makeEnemy(20);
        e.stackPoison(3, 0);
        // 1 base + 0 amp + 1 maxHpBonus = 2
        assertEquals(2, e.getPoisonTickDamage());
    }

    @Test
    void poisonTickDamage_boss_scalesUpWithMaxHp() {
        CombatEntity e = makeEnemy(100);
        e.stackPoison(3, 0);
        // 1 base + 0 amp + 5 maxHpBonus = 6
        assertEquals(6, e.getPoisonTickDamage());
    }

    @Test
    void poisonTickDamage_addsAmplifier() {
        CombatEntity e = makeEnemy(20);
        e.stackPoison(3, 2);
        // 1 base + 2 amp + 1 maxHpBonus = 4
        assertEquals(4, e.getPoisonTickDamage());
    }

    // ---- Wither tick formula: remainingTurns + 1 + amplifier + maxHpBonus ----

    @Test
    void witherTickDamage_zeroIfNotWithered() {
        assertEquals(0, makeEnemy(20).getWitherTickDamage());
    }

    @Test
    void witherTickDamage_tapersAsTurnsRemainingShrink() {
        CombatEntity e = makeEnemy(20);
        e.stackWither(3, 0);

        // Tick 1 (turns=3): 3 + 1 + 0 + 1 = 5
        assertEquals(5, e.getWitherTickDamage());
        e.setWitherTurns(2);
        // Tick 2 (turns=2): 2 + 1 + 0 + 1 = 4
        assertEquals(4, e.getWitherTickDamage());
        e.setWitherTurns(1);
        // Tick 3 (turns=1): 1 + 1 + 0 + 1 = 3
        assertEquals(3, e.getWitherTickDamage());
    }

    @Test
    void witherTickDamage_boss_addsLargerMaxHpBonus() {
        CombatEntity boss = makeEnemy(200);
        boss.stackWither(3, 0);
        // turns=3 + 1 + 0 amp + 10 maxHpBonus = 14
        assertEquals(14, boss.getWitherTickDamage());
    }

    @Test
    void witherTickDamage_addsAmplifier() {
        CombatEntity e = makeEnemy(20);
        e.stackWither(4, 3);
        // turns=4 + 1 + 3 amp + 1 maxHpBonus = 9
        assertEquals(9, e.getWitherTickDamage());
    }

    // ---- stackWither semantics ----

    @Test
    void stackWither_extendsDurationToMax() {
        CombatEntity e = makeEnemy(20);
        e.stackWither(3, 0);
        assertEquals(3, e.getWitherTurns());
        // re-cast with shorter duration: doesn't shorten
        e.stackWither(2, 0);
        assertEquals(3, e.getWitherTurns());
        // re-cast with longer duration: extends
        e.stackWither(5, 0);
        assertEquals(5, e.getWitherTurns());
    }

    @Test
    void stackWither_stacksAmplifier() {
        CombatEntity e = makeEnemy(20);
        e.stackWither(3, 1);
        assertEquals(1, e.getWitherAmplifier());
        e.stackWither(3, 1);
        assertEquals(2, e.getWitherAmplifier());
    }
}
