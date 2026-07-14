package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spectral arrows Mark their target, and that Mark is NOT additive.
 *
 * <p>The whole point of the guard: a player holding a stack of spectral arrows must not be
 * able to re-mark the same target every shot. If the mark stacked (or even merely refreshed),
 * a boss could be pinned under permanent double damage for the entire fight.
 */
class SpectralArrowMarkTest {

    private static CombatEntity mob() {
        return new CombatEntity(1, "minecraft:zombie", new GridPos(0, 0),
            /* maxHp */ 20, /* attack */ 3, /* defense */ 0, /* range */ 1);
    }

    @Test
    void marksAnUnmarkedTarget() {
        CombatEntity e = mob();
        assertFalse(e.isMarked());

        assertTrue(e.markNonAdditive(1), "an unmarked target takes the mark");
        assertTrue(e.isMarked());
        assertEquals(1, e.getMarkedTurns());
    }

    /** The headline rule: a second arrow into a Marked target does nothing at all. */
    @Test
    void doesNotStackOnAnAlreadyMarkedTarget() {
        CombatEntity e = mob();
        e.markNonAdditive(1);

        assertFalse(e.markNonAdditive(1), "the second arrow reports that it did nothing");
        assertEquals(1, e.getMarkedTurns(), "and the duration must NOT have grown");
    }

    /** Not even a refresh: it must not reset a partly-expired timer back up. */
    @Test
    void doesNotRefreshAPartlyExpiredMark() {
        CombatEntity e = mob();
        e.setMarkedTurns(2);          // e.g. a spyglass mark, one turn already spent
        e.setMarkedTurns(1);

        assertFalse(e.markNonAdditive(1));
        assertEquals(1, e.getMarkedTurns(), "the timer must not tick back up");
    }

    /** Emptying a whole quiver into one target still leaves it Marked for exactly 1 turn. */
    @Test
    void aWholeQuiverCannotPinATargetUnderAPermanentMark() {
        CombatEntity e = mob();
        for (int shot = 0; shot < 20; shot++) {
            e.markNonAdditive(1);
        }
        assertEquals(1, e.getMarkedTurns(),
            "20 spectral arrows must not buy 20 turns of double damage");
    }

    /** Once the mark expires, the target can be Marked again. The rule is not a lockout. */
    @Test
    void canBeRemarkedOnceTheMarkExpires() {
        CombatEntity e = mob();
        e.markNonAdditive(1);
        e.setMarkedTurns(0); // the countdown ran out

        assertTrue(e.markNonAdditive(1), "a fresh arrow re-marks a target whose mark expired");
        assertEquals(1, e.getMarkedTurns());
    }

    /** Marked is what makes the arrow worth firing: it amplifies every damage source. */
    @Test
    void aMarkedTargetTakesAmplifiedDamage() {
        CombatEntity e = mob();
        assertEquals(1.0, e.getMarkedDamageMultiplier(), 0.001, "unmarked: no bonus");

        e.markNonAdditive(1);
        assertTrue(e.getMarkedDamageMultiplier() > 1.0,
            "a Marked target must actually take more damage");
    }
}
