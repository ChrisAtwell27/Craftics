package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The canonical effect math, shared by players and enemies.
 *
 * <p>These formulas are the player's, by design: the player's behavior is the definition of what
 * an effect does, and the enemy side conforms to it. Every number here is lifted from
 * {@link CombatEffects#applyPerTurnEffects}, which was the only implementation of the real rules
 * before this class existed.
 */
class EffectFormulasTest {

    /** Poison front-loads: it is worst on the first tick and fades as its duration runs out. */
    @Test
    void poisonFrontLoads() {
        // level 1, 5 turns left, no affinity: (2*1) + 5 + 0 = 7
        assertEquals(7, EffectFormulas.poisonTick(1, 5, 0));
        // same effect, 1 turn left: (2*1) + 1 + 0 = 3
        assertEquals(3, EffectFormulas.poisonTick(1, 1, 0));
        assertTrue(EffectFormulas.poisonTick(1, 5, 0) > EffectFormulas.poisonTick(1, 1, 0),
            "poison must weaken as it runs out");
    }

    @Test
    void poisonScalesWithLevelAndAffinity() {
        assertEquals(9, EffectFormulas.poisonTick(2, 5, 0));  // (2*2) + 5
        assertEquals(10, EffectFormulas.poisonTick(1, 5, 3)); // (2*1) + 5 + 3
    }

    /** Wither ramps: it is weakest on the first tick and worst on the last. */
    @Test
    void witherRamps() {
        // level 1, peak 5, 5 turns left -> elapsed 1: (1+1+0) * 1 = 2
        assertEquals(2, EffectFormulas.witherTick(1, 5, 5, 0));
        // ...1 turn left -> elapsed 5: (1+1+0) * 5 = 10
        assertEquals(10, EffectFormulas.witherTick(1, 5, 1, 0));
        assertTrue(EffectFormulas.witherTick(1, 5, 1, 0) > EffectFormulas.witherTick(1, 5, 5, 0),
            "wither must strengthen as it runs");
    }

    /** Burning is flat: the same every turn. */
    @Test
    void burningIsFlat() {
        assertEquals(2, EffectFormulas.burningTick(1, 0)); // 1 + 1
        assertEquals(3, EffectFormulas.burningTick(2, 0)); // 1 + 2
        assertEquals(5, EffectFormulas.burningTick(1, 3)); // 1 + 1 + 3
    }

    /** Bleed is triangular: 1, 3, 6, 10, 15 as stacks climb. */
    @Test
    void bleedIsTriangular() {
        assertEquals(1, EffectFormulas.bleedTick(1));
        assertEquals(3, EffectFormulas.bleedTick(2));
        assertEquals(6, EffectFormulas.bleedTick(3));
        assertEquals(10, EffectFormulas.bleedTick(4));
        assertEquals(15, EffectFormulas.bleedTick(5));
        assertEquals(0, EffectFormulas.bleedTick(0));
        assertEquals(0, EffectFormulas.bleedTick(-1), "a negative stack count can't heal");
    }

    /** A single bleed/burn tick is clamped so a runaway stack can't one-shot a full-HP target. */
    @Test
    void dotTicksCapAt100() {
        // Triangular bleed passes 100 at 14 stacks (14*15/2 = 105); it must clamp there.
        assertEquals(91, EffectFormulas.bleedTick(13), "13 stacks (91) is still under the cap");
        assertEquals(EffectFormulas.MAX_DOT_TICK, EffectFormulas.bleedTick(14), "14 stacks (105) clamps to 100");
        assertEquals(EffectFormulas.MAX_DOT_TICK, EffectFormulas.bleedTick(1000), "no stack count exceeds the cap");
        // Burning is flat and small, but a huge affinity would still clamp.
        assertEquals(EffectFormulas.MAX_DOT_TICK, EffectFormulas.burningTick(1, 1000));
        assertEquals(100, EffectFormulas.MAX_DOT_TICK);
    }

    /** Every damaging tick floors at 1: an effect that is active always does something. */
    @Test
    void everyDotFloorsAtOne() {
        assertEquals(1, EffectFormulas.poisonTick(0, 0, -99));
        assertEquals(1, EffectFormulas.witherTick(0, 1, 1, -99));
        assertEquals(1, EffectFormulas.burningTick(0, -99));
    }

    /** Blindness is -2 range per level, Darkness -1, and they stack. */
    @Test
    void rangePenaltyStacksBlindnessAndDarkness() {
        assertEquals(2, EffectFormulas.rangePenalty(1, 0));
        assertEquals(4, EffectFormulas.rangePenalty(2, 0));
        assertEquals(1, EffectFormulas.rangePenalty(0, 1));
        assertEquals(3, EffectFormulas.rangePenalty(1, 1));
        assertEquals(0, EffectFormulas.rangePenalty(0, 0));
    }
}
