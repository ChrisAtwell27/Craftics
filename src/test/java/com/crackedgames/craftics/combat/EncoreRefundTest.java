package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Performative encore must never change how many items the player has.
 *
 * <p>It is a free extra cast, so the stock afterwards has to match the stock before. The bug this
 * guards was one of the four cases going the wrong way: the encore handed an item over up front
 * assuming it would be spent, and Special affinity's conserve roll could decide not to spend it,
 * minting one every time both perks fired on the same potion.
 */
class EncoreRefundTest {

    @Test
    @DisplayName("the player's stock is unchanged in every case")
    void theEncoreIsAlwaysFree() {
        for (boolean lent : new boolean[]{false, true}) {
            for (boolean spent : new boolean[]{false, true}) {
                assertEquals(0, EncoreRefund.netChange(lent, spent),
                    "lent=" + lent + " spent=" + spent + " changed the player's item count");
            }
        }
    }

    @Test
    @DisplayName("conserving a lent item takes it back")
    void aConservedLoanIsReclaimed() {
        // The duplication bug, stated directly: an item was lent so the encore had something to
        // spend, the conserve roll meant it was never spent, so it must come back.
        assertEquals(-1, EncoreRefund.adjustment(true, false));
    }

    @Test
    @DisplayName("spending the player's own item is refunded")
    void aSpentItemIsGivenBack() {
        assertEquals(1, EncoreRefund.adjustment(false, true));
    }

    @Test
    @DisplayName("a lent item that gets spent needs no adjustment")
    void aLoanThatIsSpentIsAlreadyEven() {
        assertEquals(0, EncoreRefund.adjustment(true, true));
    }

    @Test
    @DisplayName("conserving the player's own item needs no adjustment")
    void conservingOwnStockIsAlreadyEven() {
        assertEquals(0, EncoreRefund.adjustment(false, false));
    }
}
