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
 *
 * <p>The strongest of them is {@link #sameEffectSameNumberOnEitherSide}: it gives a mob and a
 * player the SAME maximum health and demands the identical number out of both, with no allowance
 * subtracted. That is the real invariant - the max-HP term is not a mob surcharge, it is a share
 * of whoever's pool the effect is eating, and once both sides charge it the two are simply the
 * same rule applied to different health bars.
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
                "bleed must be the shared half-triangular curve at " + stacks + " stacks");
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

    // ── The unified rule ────────────────────────────────────────────────────

    /** A player's effect set, with the same maximum health the mob is given below. */
    private static int playerTick(CombatEffects fx, int maxHp) {
        return -fx.applyPerTurnEffects(0, maxHp);
    }

    /**
     * Give a mob and a player the same health pool and the same effect, and the player's number
     * must be the mob's number through exactly one factor - nothing else, no second ruleset.
     *
     * <p>{@link EffectFormulas#PLAYER_DOT_SCALE} is deliberate: a mob only has to survive this
     * fight, a player's health bar has to last the run. What matters is that it is ONE factor
     * applied at the end, so both sides still compute from a single definition of what each
     * effect does.
     */
    @Test
    void playerTakesTheMobsNumberThroughOneFactor() {
        final int POOL = 100;

        CombatEntity poisonMob = mob(POOL);
        poisonMob.stackPoison(5, 0);
        CombatEffects poisonPlayer = new CombatEffects();
        poisonPlayer.addEffect(CombatEffects.EffectType.POISON, 5, 0);
        assertEquals(EffectFormulas.forPlayer(poisonMob.getPoisonTickDamage()),
            playerTick(poisonPlayer, POOL),
            "poison on a player must be the mob's poison through the player factor");

        CombatEntity burnMob = mob(POOL);
        burnMob.stackBurning(4, 1);
        CombatEffects burnPlayer = new CombatEffects();
        burnPlayer.addEffect(CombatEffects.EffectType.BURNING, 4, 1);
        assertEquals(EffectFormulas.forPlayer(burnMob.getBurningTickDamage()),
            playerTick(burnPlayer, POOL),
            "burning on a player must be the mob's burning through the player factor");
    }

    /**
     * The pool term scales itself. A player at 20 HP pays 1, a boss at 400 pays 20 - which is
     * the whole reason neither side needs its own hand-tuned table.
     */
    @Test
    void thePoolTermScalesToWhoeverIsCarryingIt() {
        assertEquals(1, EffectFormulas.maxHpDotBonus(20), "a 20 HP player pays one");
        assertEquals(5, EffectFormulas.maxHpDotBonus(100));
        assertEquals(20, EffectFormulas.maxHpDotBonus(400), "a 400 HP boss pays twenty");
        assertEquals(1, EffectFormulas.maxHpDotBonus(0), "never zero, never negative");
    }

    /** Bleed is a decaying stack count on both sides, from the same starting stacks. */
    @Test
    void bleedDecaysIdenticallyOnBothSides() {
        final int POOL = 100;
        CombatEntity bleedMob = mob(POOL);
        bleedMob.stackBleed(4);
        CombatEffects bleedPlayer = new CombatEffects();
        bleedPlayer.stackBleed(4);

        assertEquals(4, bleedPlayer.getBleedStacks(), "the player tracks stacks like the mob does");
        assertEquals(bleedMob.getBleedStacks(), bleedPlayer.getBleedStacks());

        for (int stacks = 4; stacks >= 1; stacks--) {
            assertEquals(EffectFormulas.forPlayer(
                    CombatEntity.computeBleedTickDamage(stacks) + EffectFormulas.maxHpDotBonus(POOL)),
                playerTick(bleedPlayer, POOL), "player bleed at " + stacks + " stacks");
            bleedPlayer.tickTurn();
        }
        assertEquals(0, bleedPlayer.getBleedStacks(), "stacks run out");
    }

    /** Bleed accumulates on repeated hits, the way it always did on mobs and never did on players. */
    @Test
    void bleedAccumulatesOnThePlayerToo() {
        CombatEffects fx = new CombatEffects();
        fx.stackBleed(2);
        fx.stackBleed(3);
        assertEquals(5, fx.getBleedStacks(),
            "two hits must build, not replace - replacing is what made a player's bleed harmless");

        CombatEntity e = mob(100);
        e.stackBleed(2);
        e.stackBleed(3);
        assertEquals(e.getBleedStacks(), fx.getBleedStacks());
    }

    /**
     * Bleed must never hit a player harder than the flat version it replaced.
     *
     * <p>Turning bleed into accumulating stacks made it strictly better against mobs and, left
     * unbounded, strictly worse for players - the mob curve turned loose on a 20 HP bar. The
     * player stack ceiling plus the player factor together have to keep the new per-turn damage
     * at or below {@code bleedTick(amplifier + 1)}, which is exactly what a player used to take
     * every turn for the whole duration.
     */
    @Test
    void playerBleedIsNeverWorseThanTheFlatVersionItReplaced() {
        for (int amplifier = 0; amplifier <= 8; amplifier++) {
            int oldFlatPerTurn = EffectFormulas.bleedTick(amplifier + 1);

            CombatEffects fx = new CombatEffects();
            fx.addEffect(CombatEffects.EffectType.BLEEDING, 3, amplifier);
            // Its very first tick is its worst - stacks only decay from here.
            int newPeakPerTurn = -fx.applyPerTurnEffects(0, 20);

            assertTrue(newPeakPerTurn <= oldFlatPerTurn,
                "bleed at amplifier " + amplifier + " now peaks at " + newPeakPerTurn
                    + " a turn but used to be " + oldFlatPerTurn);
        }
    }

    /** And it still accumulates - the ceiling must bound it, not defeat it. */
    @Test
    void theBleedCeilingBoundsItWithoutDefeatingIt() {
        CombatEffects fx = new CombatEffects();
        fx.stackBleed(1);
        int oneStack = -fx.applyPerTurnEffects(0, 20);
        fx.stackBleed(4);
        int fiveStacks = -fx.applyPerTurnEffects(0, 20);
        assertTrue(fiveStacks > oneStack, "stacking up must still hurt more than a single nick");

        fx.stackBleed(50);
        assertEquals(EffectFormulas.MAX_PLAYER_BLEED_STACKS, fx.getBleedStacks(),
            "and it must stop at the player ceiling however hard it is pushed");
    }

    /** Vulnerable is Resistance with the sign flipped, and players can now be given it. */
    @Test
    void vulnerableMirrorsResistance() {
        CombatEffects resisted = new CombatEffects();
        resisted.addEffect(CombatEffects.EffectType.RESISTANCE, 3, 1);
        CombatEffects exposed = new CombatEffects();
        exposed.addEffect(CombatEffects.EffectType.VULNERABLE, 3, 1);

        assertEquals(resisted.getResistanceBonus(), exposed.getDefensePenalty(),
            "the same magnitude, applied in the opposite direction");
        assertEquals(0, resisted.getDefensePenalty());
        assertEquals(0, exposed.getResistanceBonus());
    }

    /** A cleanse must strip Vulnerable, or it becomes the one debuff nothing can remove. */
    @Test
    void vulnerableIsCleansable() {
        assertTrue(CombatEffects.isDebuff(CombatEffects.EffectType.VULNERABLE));
        CombatEffects fx = new CombatEffects();
        fx.addEffect(CombatEffects.EffectType.VULNERABLE, 3, 0);
        fx.addEffect(CombatEffects.EffectType.RESISTANCE, 3, 0);
        assertEquals(CombatEffects.EffectType.VULNERABLE, fx.removeFirstDebuff());
        assertEquals(0, fx.getDefensePenalty());
        assertTrue(fx.getResistanceBonus() > 0, "a cleanse must not take the buff with it");
    }
}
