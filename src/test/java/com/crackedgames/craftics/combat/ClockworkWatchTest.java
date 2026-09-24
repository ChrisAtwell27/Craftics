package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.ClockworkWatch.Action;
import com.crackedgames.craftics.combat.ClockworkWatch.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Clockwork chestplate's rule: repeat your first turn's first three actions, in order, at
 * the start of your second turn, and your next attack is doubled.
 */
class ClockworkWatchTest {

    /** Wind the watch with a full first turn. */
    private static ClockworkWatch wound(Action a, Action b, Action c) {
        ClockworkWatch w = new ClockworkWatch();
        assertEquals(Result.RECORDED, w.record(1, a));
        assertEquals(Result.RECORDED, w.record(1, b));
        assertEquals(Result.WOUND, w.record(1, c));
        return w;
    }

    @Test
    void sameThreeInOrder_strikes() {
        ClockworkWatch w = wound(Action.MOVE, Action.ATTACK, Action.ATTACK);
        assertEquals(Result.MATCHED, w.record(2, Action.MOVE));
        assertEquals(Result.MATCHED, w.record(2, Action.ATTACK));
        assertEquals(Result.STRUCK, w.record(2, Action.ATTACK));
        assertTrue(w.isPrimed());
    }

    @Test
    void orderMatters() {
        ClockworkWatch w = wound(Action.MOVE, Action.ATTACK, Action.ITEM);
        assertEquals(Result.JAMMED, w.record(2, Action.ATTACK));
        // Jammed for the rest of the fight: the right actions afterwards change nothing.
        assertEquals(Result.IGNORED, w.record(2, Action.MOVE));
        assertEquals(Result.IGNORED, w.record(2, Action.ATTACK));
        assertFalse(w.isPrimed());
    }

    @Test
    void wrongThirdAction_jams() {
        ClockworkWatch w = wound(Action.MOVE, Action.ATTACK, Action.ATTACK);
        w.record(2, Action.MOVE);
        w.record(2, Action.ATTACK);
        assertEquals(Result.JAMMED, w.record(2, Action.ITEM));
        assertFalse(w.consumeStrike());
    }

    @Test
    void onlyTheFirstThreeOfTheFirstTurnCount() {
        ClockworkWatch w = wound(Action.MOVE, Action.MOVE, Action.ATTACK);
        // A fourth action on turn one is not part of the sequence.
        assertEquals(Result.IGNORED, w.record(1, Action.ITEM));
        assertEquals(List.of(Action.MOVE, Action.MOVE, Action.ATTACK), w.remembered());
        w.record(2, Action.MOVE);
        w.record(2, Action.MOVE);
        assertEquals(Result.STRUCK, w.record(2, Action.ATTACK));
    }

    @Test
    void actionsAfterTheThirdOnTurnTwo_areIgnored() {
        ClockworkWatch w = wound(Action.ATTACK, Action.ATTACK, Action.ATTACK);
        w.record(2, Action.ATTACK);
        w.record(2, Action.ATTACK);
        w.record(2, Action.ATTACK);
        // A fourth, different action can't un-strike the watch.
        assertEquals(Result.IGNORED, w.record(2, Action.MOVE));
        assertTrue(w.isPrimed());
    }

    @Test
    void fewerThanThreeOnTurnOne_neverWinds() {
        ClockworkWatch w = new ClockworkWatch();
        w.record(1, Action.MOVE);
        w.record(1, Action.ATTACK);
        assertEquals(Result.UNWOUND, w.record(2, Action.MOVE));
        assertEquals(Result.IGNORED, w.record(2, Action.ATTACK));
        assertFalse(w.isPrimed());
    }

    @Test
    void noActionsOnTurnOne_neverWinds() {
        ClockworkWatch w = new ClockworkWatch();
        assertEquals(Result.UNWOUND, w.record(2, Action.MOVE));
    }

    @Test
    void secondTurnThatEndsShort_neverStrikes() {
        ClockworkWatch w = wound(Action.MOVE, Action.ATTACK, Action.ATTACK);
        w.record(2, Action.MOVE);
        w.record(2, Action.ATTACK);
        // Turn two ended after two matches. Turn three is too late to finish the sequence.
        assertEquals(Result.IGNORED, w.record(3, Action.ATTACK));
        assertFalse(w.isPrimed());
    }

    @Test
    void theThirdTurnIsNeverTheSecondChance() {
        ClockworkWatch w = wound(Action.MOVE, Action.ATTACK, Action.ATTACK);
        // Skipped turn two entirely: the watch is spent without a match.
        assertEquals(Result.IGNORED, w.record(3, Action.MOVE));
        assertEquals(Result.IGNORED, w.record(4, Action.MOVE));
    }

    @Test
    void theStrikeIsSpentExactlyOnce() {
        ClockworkWatch w = wound(Action.ITEM, Action.ITEM, Action.ITEM);
        w.record(2, Action.ITEM);
        w.record(2, Action.ITEM);
        w.record(2, Action.ITEM);
        assertTrue(w.consumeStrike());
        assertFalse(w.consumeStrike());
        assertFalse(w.isPrimed());
    }

    @Test
    void aStrikeSurvivesIntoLaterTurns() {
        // A second turn that ends on its third match with no AP left still pays out next turn.
        ClockworkWatch w = wound(Action.MOVE, Action.MOVE, Action.MOVE);
        w.record(2, Action.MOVE);
        w.record(2, Action.MOVE);
        w.record(2, Action.MOVE);
        w.record(3, Action.MOVE); // later actions don't disturb it
        assertTrue(w.consumeStrike());
    }

    @Test
    void doublesDamage() {
        assertEquals(2, ClockworkWatch.DAMAGE_MULT);
        assertEquals(3, ClockworkWatch.SEQUENCE_LENGTH);
    }

    @Test
    void describeReadsInOrder() {
        assertEquals("Move → Attack → Item",
            ClockworkWatch.describe(List.of(Action.MOVE, Action.ATTACK, Action.ITEM)));
        assertEquals("", ClockworkWatch.describe(List.of()));
    }
}
