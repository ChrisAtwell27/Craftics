package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An effect must PAY OUT on every turn of its declared duration, including its last one.
 *
 * <p>{@code tickTurn()} decrements durations and removes anything that reaches 0, while
 * {@code applyPerTurnEffects()} only sees effects that are still present. So the two must be
 * called in the order (apply, then tick) - the reverse deletes an effect on its final turn
 * before that turn's heal/damage ever runs, and an N-turn effect pays out only N-1 times.
 *
 * <p>These tests drive the two methods in the same order {@code CombatManager} does, so they
 * fail if that ordering is ever flipped back.
 */
class EffectTickOrderTest {

    /** Run one combat turn the way CombatManager does: pay out, THEN tick down. */
    private static int runTurn(CombatEffects fx) {
        int hp = fx.applyPerTurnEffects(0);
        fx.tickTurn();
        return hp;
    }

    /** Vanilla player max health, so a DOT carries the pool term a real player's would. */
    private static final int PLAYER_POOL = 20;

    private static int runTurn(CombatEffects fx, int maxHp) {
        int hp = fx.applyPerTurnEffects(0, maxHp);
        fx.tickTurn();
        return hp;
    }

    /** A 3-turn Regeneration must heal three times, not twice. */
    @Test
    void regenerationHealsOncePerTurnOfItsDuration() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.REGENERATION, 3, 0); // level I = +2 HP/turn

        int totalHealed = 0;
        int turnsThatHealed = 0;
        for (int turn = 0; turn < 3; turn++) {
            int hp = runTurn(fx);
            if (hp > 0) turnsThatHealed++;
            totalHealed += hp;
        }

        assertEquals(3, turnsThatHealed,
            "a 3-turn regen must heal on all 3 turns - the final tick used to be dropped");
        assertEquals(6, totalHealed, "3 turns x 2 HP");
        assertFalse(fx.hasEffect(EffectType.REGENERATION), "and it is gone afterwards");
    }

    /** The effect must be fully expired after its duration - not linger for a bonus turn. */
    @Test
    void regenerationStopsExactlyOnTime() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.REGENERATION, 2, 0);

        assertTrue(runTurn(fx) > 0, "turn 1 heals");
        assertTrue(runTurn(fx) > 0, "turn 2 heals");
        assertEquals(0, runTurn(fx), "turn 3 must heal nothing - the effect is over");
    }

    /** Amplifier scales the heal: level II = +4/turn. */
    @Test
    void regenerationAmplifierScalesTheHeal() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.REGENERATION, 1, 1); // level II
        assertEquals(4, runTurn(fx));
    }

    /** The same dropped-final-tick bug hit every damage-over-time effect, not just regen. */
    @Test
    void burningDamagesOncePerTurnOfItsDuration() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.BURNING, 3, 0);

        int turnsThatHurt = 0;
        for (int turn = 0; turn < 3; turn++) {
            if (runTurn(fx) < 0) turnsThatHurt++;
        }
        assertEquals(3, turnsThatHurt, "a 3-turn burn must burn on all 3 turns");
    }

    @Test
    void poisonDamagesOncePerTurnOfItsDuration() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.POISON, 3, 0);

        int turnsThatHurt = 0;
        for (int turn = 0; turn < 3; turn++) {
            if (runTurn(fx) < 0) turnsThatHurt++;
        }
        assertEquals(3, turnsThatHurt, "a 3-turn poison must tick on all 3 turns");
    }

    /**
     * Poison is documented as front-loaded: {@code (2 x level) + turnsRemaining}, so it hits
     * hardest on turn 1 and fades. That only holds if the damage is read BEFORE the duration
     * is decremented.
     */
    @Test
    void poisonFadesAsItTicksDown() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.POISON, 3, 0);

        // Run against a real player's health pool. The no-pool overload exists for testing the
        // raw formulas, and at that size the player factor rounds two adjacent ticks together
        // and hides the very shape this test is about - which is a fact about a 3-damage
        // difference surviving integer arithmetic, not about poison.
        int first = -runTurn(fx, PLAYER_POOL);
        int second = -runTurn(fx, PLAYER_POOL);
        int third = -runTurn(fx, PLAYER_POOL);

        assertTrue(first > second, "poison must hit hardest on its first turn");
        assertTrue(second > third, "and keep fading");
    }

    /**
     * Wither ramps the opposite way, climbing to its worst on the final tick - which is
     * precisely the tick that used to be dropped, so the bug decapitated the payoff.
     */
    @Test
    void witherRampsUpAndLandsItsFinalTick() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.WITHER, 3, 0);

        int first = -runTurn(fx);
        int second = -runTurn(fx);
        int third = -runTurn(fx);

        assertTrue(third > second, "wither must climb");
        assertTrue(second > first, "wither must climb");
        assertTrue(third > 0, "the biggest wither tick is the LAST one - it must actually land");
    }

    /** Bleeding is stack-based rather than timed, but still must not lose its final tick. */
    /**
     * Bleed FADES as its stacks decay - it does not deal a flat amount for a fixed duration.
     *
     * <p>This test previously asserted the flat version, which was the player-only behaviour:
     * a mob's bleed always decayed a stack per turn and shrank with it, while a player's bleed
     * held its full value for its whole duration and then vanished. Both are now the mob rule,
     * so the expectation here changed deliberately - the old numbers were pinning the very
     * divergence this was meant to remove. {@code bleedMatchesTheMobCurve} below is the guard
     * that keeps them together from now on.
     */
    @Test
    void bleedingFadesAsItsStacksDecay() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.BLEEDING, 2, 2); // 3 stacks

        assertEquals(-2, runTurn(fx), "3 stacks");
        assertEquals(-1, runTurn(fx), "decayed to 2 stacks");
        assertEquals(-1, runTurn(fx), "decayed to 1 stack");
        assertEquals(0, runTurn(fx), "stacks exhausted");
    }

    /** The player's bleed curve, turn by turn, must be the mob's bleed curve. */
    @Test
    void bleedMatchesTheMobCurve() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.BLEEDING, 1, 4); // 5 stacks
        for (int stacks = 5; stacks >= 1; stacks--) {
            assertEquals(-EffectFormulas.forPlayer(
                    com.crackedgames.craftics.combat.CombatEntity.computeBleedTickDamage(stacks)),
                runTurn(fx), "player bleed at " + stacks + " stacks");
        }
        assertEquals(0, runTurn(fx), "stacks exhausted");
    }

    /** Frozen effects (applied in the hub) must not tick or pay out until combat begins. */
    @Test
    void frozenEffectsDoNotPayOutUntilUnfrozen() {
        CombatEffects fx = new CombatEffects();
        fx.addFrozenEffect(EffectType.REGENERATION, 3, 0);

        assertEquals(0, runTurn(fx), "a frozen effect heals nothing");
        assertEquals(0, runTurn(fx), "and never ticks down");

        fx.unfreezeAll(3);
        assertTrue(runTurn(fx) > 0, "once combat starts it pays out normally");
    }

    /** A heal and a DoT on the same turn net out rather than one clobbering the other. */
    @Test
    void regenAndPoisonNetAgainstEachOther() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.REGENERATION, 3, 0); // +2
        fx.addEffect(EffectType.POISON, 3, 0);       // -(2 + turnsRemaining)

        int net = fx.applyPerTurnEffects(0);
        assertTrue(net < 0, "poison out-damages a level-I regen on the first turn");
    }
}
