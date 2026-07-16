package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the per-tick DOT damage on {@link CombatEntity} - poison, wither, and the
 * {@code maxHp / 20} scaling term appended to both.
 *
 * <p>The math itself now lives in {@link EffectFormulas} and is shared with the player; this
 * file pins the concrete numbers a mob takes, including the max-HP term that is the enemy
 * side's one intentional addition. {@code EffectParityTest} holds the two sides together.
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

    // ---- Poison tick formula: EffectFormulas.poisonTick(level, turnsRemaining, 0) + maxHpBonus ----
    //
    // Poison used to be a flat 1 + amplifier here, while the same effect front-loaded on a
    // player. It now computes from the shared canonical formula, so these numbers moved UP and
    // fall as the poison runs out. EffectParityTest pins this to the player's side.

    @Test
    void poisonTickDamage_zeroIfNotPoisoned() {
        assertEquals(0, makeEnemy(20).getPoisonTickDamage());
    }

    @Test
    void poisonTickDamage_zombie_baseAmpAndMaxHpBonus() {
        CombatEntity e = makeEnemy(20);
        e.stackPoison(3, 0);
        // (2*1 level) + 3 turns + 1 maxHpBonus = 6
        assertEquals(6, e.getPoisonTickDamage());
    }

    @Test
    void poisonTickDamage_boss_scalesUpWithMaxHp() {
        CombatEntity e = makeEnemy(100);
        e.stackPoison(3, 0);
        // (2*1 level) + 3 turns + 5 maxHpBonus = 10
        assertEquals(10, e.getPoisonTickDamage());
    }

    @Test
    void poisonTickDamage_addsAmplifier() {
        CombatEntity e = makeEnemy(20);
        e.stackPoison(3, 2);
        // amp 2 = level 3: (2*3) + 3 turns + 1 maxHpBonus = 10
        assertEquals(10, e.getPoisonTickDamage());
    }

    /** Poison front-loads on a mob exactly as it does on a player: worst on the first tick. */
    @Test
    void poisonTickDamage_frontLoadsAsTurnsRemainingShrink() {
        CombatEntity e = makeEnemy(20);
        e.stackPoison(3, 0);

        assertEquals(6, e.getPoisonTickDamage()); // (2*1) + 3 + 1
        e.setPoisonTurns(2);
        assertEquals(5, e.getPoisonTickDamage()); // (2*1) + 2 + 1
        e.setPoisonTurns(1);
        assertEquals(4, e.getPoisonTickDamage()); // (2*1) + 1 + 1
    }

    // ---- Wither tick formula: EffectFormulas.witherTick(level, peak, remaining, 0) + maxHpBonus ----
    //
    // Wither used to TAPER here while it ramped on a player - the same name meaning opposite
    // things. It now ramps on both sides, so the progression below runs upward.

    @Test
    void witherTickDamage_zeroIfNotWithered() {
        assertEquals(0, makeEnemy(20).getWitherTickDamage());
    }

    @Test
    void witherTickDamage_rampsAsTurnsRemainingShrink() {
        CombatEntity e = makeEnemy(20);
        e.stackWither(3, 0);

        // base (1 + level 1) = 2, elapsed = peak 3 - remaining + 1
        // Tick 1 (turns=3, elapsed 1): 2*1 + 1 maxHpBonus = 3
        assertEquals(3, e.getWitherTickDamage());
        e.setWitherTurns(2);
        // Tick 2 (turns=2, elapsed 2): 2*2 + 1 = 5
        assertEquals(5, e.getWitherTickDamage());
        e.setWitherTurns(1);
        // Tick 3 (turns=1, elapsed 3): 2*3 + 1 = 7
        assertEquals(7, e.getWitherTickDamage());
    }

    @Test
    void witherTickDamage_boss_addsLargerMaxHpBonus() {
        CombatEntity boss = makeEnemy(200);
        boss.stackWither(3, 0);
        // base 2 * elapsed 1 + 10 maxHpBonus = 12
        assertEquals(12, boss.getWitherTickDamage());
    }

    @Test
    void witherTickDamage_addsAmplifier() {
        CombatEntity e = makeEnemy(20);
        e.stackWither(4, 3);
        // amp 3 = level 4: base (1+4) = 5 * elapsed 1 + 1 maxHpBonus = 6
        assertEquals(6, e.getWitherTickDamage());
    }

    /** The ramp measures from the wither's real start, so re-applying it does not reset the
     *  elapsed count and drop the damage back to a first tick. */
    @Test
    void witherTickDamage_rampMeasuresFromPeakDuration() {
        CombatEntity e = makeEnemy(20);
        e.stackWither(3, 0);
        e.setWitherTurns(1);
        int lateTick = e.getWitherTickDamage(); // elapsed 3

        e.stackWither(2, 0); // re-applied: turns back up to 2, peak stays 3
        // elapsed = 3 - 2 + 1 = 2, so it eases off but does not restart at elapsed 1
        assertEquals(5, e.getWitherTickDamage());
        assertTrue(lateTick > e.getWitherTickDamage());
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
