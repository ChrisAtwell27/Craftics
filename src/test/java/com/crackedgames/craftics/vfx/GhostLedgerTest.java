package com.crackedgames.craftics.vfx;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blocks that are shown but never placed have to be un-shown at the right time.
 *
 * <p>The ledger is the only thing that knows when. If it lets a block go early, the real floor
 * pops back in the middle of an effect; if it forgets one, a player is left looking at fire
 * that is not there.
 */
class GhostLedgerTest {

    private static final BlockPos A = new BlockPos(10, 64, 10);
    private static final BlockPos B = new BlockPos(11, 64, 10);

    @Test
    void aBlockIsReleasedOnceItsTimeIsUp() {
        GhostLedger ledger = new GhostLedger();
        ledger.hold(A, 100);

        assertTrue(ledger.release(99).isEmpty(), "not yet");
        assertEquals(List.of(A), ledger.release(100));
        assertTrue(ledger.isEmpty());
    }

    @Test
    void aReleasedBlockIsNotReleasedTwice() {
        GhostLedger ledger = new GhostLedger();
        ledger.hold(A, 100);
        ledger.release(100);

        assertTrue(ledger.release(200).isEmpty());
    }

    @Test
    void twoEffectsOnOneBlockKeepItUntilTheLaterIsDone() {
        // A wave's crest lasts a moment and the flooded floor under it lasts longer. The crest
        // ending must not put the real floor back while the flood is still showing.
        GhostLedger ledger = new GhostLedger();
        ledger.hold(A, 130);
        ledger.hold(A, 105);

        assertTrue(ledger.release(105).isEmpty());
        assertTrue(ledger.holds(A));
        assertEquals(List.of(A), ledger.release(130));
    }

    @Test
    void aLaterEffectExtendsAnEarlierOne() {
        GhostLedger ledger = new GhostLedger();
        ledger.hold(A, 105);
        ledger.hold(A, 130);

        assertTrue(ledger.release(105).isEmpty());
        assertEquals(List.of(A), ledger.release(130));
    }

    @Test
    void onlyTheBlocksThatAreDueAreReleased() {
        GhostLedger ledger = new GhostLedger();
        ledger.hold(A, 100);
        ledger.hold(B, 140);

        assertEquals(List.of(A), ledger.release(120));
        assertTrue(ledger.holds(B));
        assertFalse(ledger.holds(A));
    }

    @Test
    void whenTheFightEndsEverythingIsReleasedAtOnce() {
        GhostLedger ledger = new GhostLedger();
        ledger.hold(A, 100);
        ledger.hold(B, 9999);

        List<BlockPos> all = ledger.releaseAll();

        assertEquals(2, all.size());
        assertTrue(all.contains(A) && all.contains(B));
        assertTrue(ledger.isEmpty());
        assertTrue(ledger.releaseAll().isEmpty());
    }

    @Test
    void aPositionThatIsLaterChangedByItsOwnerIsStillTheOneHeld() {
        // Callers often walk a BlockPos.Mutable across an area. What was held must be the
        // position as it was when it was held.
        GhostLedger ledger = new GhostLedger();
        BlockPos.Mutable cursor = new BlockPos.Mutable(10, 64, 10);
        ledger.hold(cursor, 100);
        cursor.set(50, 70, 50);

        assertEquals(List.of(A), ledger.release(100));
    }

    @Test
    void aHoldCanBeDroppedBeforeItsTimeIsUp() {
        // A block shown until a blow lands is put right when it lands, not when a timer says.
        GhostLedger ledger = new GhostLedger();
        ledger.hold(A, 9999);
        ledger.hold(B, 9999);

        assertTrue(ledger.drop(A));
        assertFalse(ledger.holds(A));
        assertTrue(ledger.holds(B), "only the one asked for");
        assertFalse(ledger.drop(A), "it was not being held any more");
    }
}
