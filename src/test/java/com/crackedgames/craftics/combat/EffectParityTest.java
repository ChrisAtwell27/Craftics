package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two sides must agree. This is the whole point of the exercise.
 *
 * <p>Players and enemies keep separate effect storage, so nothing structural stops them drifting
 * apart again - and they had, badly: poison was front-loaded on a player but flat on a mob,
 * wither ramped on a player but tapered on a mob, bleed stacks persisted on a player but shed one
 * per turn on a mob. Each divergence was invisible until someone read both implementations.
 *
 * <p>These tests hold the sides together by asserting the enemy tick equals the player tick once
 * the enemy's max-HP scaling term is subtracted. If someone edits one side's math, this fails.
 */
class EffectParityTest {

    /** A mob with a known max HP so its max-HP DoT bonus is predictable. */
    private static CombatEntity mob(int maxHp) {
        return new CombatEntity(1, "minecraft:zombie", new GridPos(0, 0), maxHp, 5, 0, 1);
    }

    /** The scaling term the enemy adds on top of the shared formula: max(1, maxHp/20). */
    private static int hpBonus(int maxHp) {
        return Math.max(1, maxHp / 20);
    }

    @Test
    void enemyPoisonMatchesThePlayerFormula() {
        CombatEntity e = mob(100);
        e.stackPoison(5, 0); // 5 turns, amplifier 0 = level I
        assertEquals(EffectFormulas.poisonTick(1, 5, 0) + hpBonus(100),
            e.getPoisonTickDamage(),
            "enemy poison must be the player's poison plus the max-HP term");
    }

    @Test
    void enemyPoisonFrontLoadsLikeThePlayers() {
        CombatEntity e = mob(100);
        e.stackPoison(5, 0);
        int firstTick = e.getPoisonTickDamage();
        e.setPoisonTurns(1);
        int lastTick = e.getPoisonTickDamage();
        assertTrue(firstTick > lastTick, "enemy poison must front-load, like the player's");
    }

    @Test
    void enemyWitherRampsLikeThePlayers() {
        CombatEntity e = mob(100);
        e.stackWither(5, 0);
        int firstTick = e.getWitherTickDamage();
        e.setWitherTurns(1);
        int lastTick = e.getWitherTickDamage();
        assertTrue(lastTick > firstTick,
            "enemy wither must RAMP like the player's - it used to taper, which was the bug");
    }

    /**
     * Burning's second argument is an AMPLIFIER, not a per-turn damage number. It had been read
     * both ways across its call sites, which is how a damage value ended up sitting in a slot that
     * means "level" - the same class of bug as Bane of Arthropods passing computed damage into
     * stackPoison's amplifier. The public addon API has always passed a variable named
     * {@code amplifier} here, so the slot's meaning is settled: level = amplifier + 1.
     */
    @Test
    void enemyBurningMatchesThePlayerFormula() {
        CombatEntity e = mob(100);
        e.stackBurning(3, 0); // 3 turns, amplifier 0 = level I
        assertEquals(EffectFormulas.burningTick(1, 0) + hpBonus(100),
            e.getBurningTickDamage(),
            "enemy burning must be the player's burning plus the max-HP term");
    }

    /** Burning is flat over its duration but scales with LEVEL, exactly as it does on a player. */
    @Test
    void enemyBurningScalesWithLevelNotDamage() {
        CombatEntity e = mob(100);
        e.stackBurning(3, 1); // amplifier 1 = level II
        assertEquals(EffectFormulas.burningTick(2, 0) + hpBonus(100),
            e.getBurningTickDamage(),
            "the second stackBurning argument is an amplifier: 1 must mean level II");
    }

    @Test
    void bleedIsIdenticalOnBothSides() {
        for (int stacks = 1; stacks <= 5; stacks++) {
            assertEquals(EffectFormulas.bleedTick(stacks),
                CombatEntity.computeBleedTickDamage(stacks),
                "bleed must be the shared triangular curve at " + stacks + " stacks");
        }
    }

    /** The max-HP term is the ONE intentional asymmetry, and it scales with the target. */
    @Test
    void theMaxHpTermScalesWithTheTarget() {
        CombatEntity small = mob(20);
        CombatEntity boss = mob(200);
        small.stackPoison(3, 0);
        boss.stackPoison(3, 0);
        assertTrue(boss.getPoisonTickDamage() > small.getPoisonTickDamage(),
            "DoT must stay relevant against a big health pool");
    }
}
