package com.crackedgames.craftics.compat.backpacked;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Backpacked augment decisions, exercised without a game.
 *
 * <p>{@link BackpackedCompat} itself cannot be reached from here - it needs a live player, a
 * reflectively-resolved backpack and a bootstrapped registry - which is exactly why these two
 * decisions were pulled out into {@link AugmentRules}. What is left in the compat class is
 * plumbing; what is here is the part that can be wrong in an interesting way.
 */
class AugmentRulesTest {

    // -- Funnelling / Lootbound ----------------------------------------------

    @Test
    @DisplayName("a pack with no augments never claims loot")
    void bareePackDeclines() {
        assertFalse(AugmentRules.funnelClaims(false, false, true, true));
        assertFalse(AugmentRules.funnelClaims(false, false, false, false));
    }

    @Test
    @DisplayName("Funnelling alone defers entirely to its item filter")
    void funnellingFollowsFilter() {
        assertTrue(AugmentRules.funnelClaims(true, false, false, true));
        assertFalse(AugmentRules.funnelClaims(true, false, true, false));
    }

    @Test
    @DisplayName("Lootbound alone has no filter, so its mobs toggle is the whole decision")
    void lootboundAloneFollowsToggle() {
        assertTrue(AugmentRules.funnelClaims(false, true, true, false));
        assertFalse(AugmentRules.funnelClaims(false, true, false, true));
    }

    @Test
    @DisplayName("Lootbound refusing mob drops overrides a filter that would have allowed them")
    void mobsOffBeatsPermissiveFilter() {
        // Combat loot is a mob drop that never got to be an entity. A player who turned the mobs
        // toggle off said no to this specific loot, whatever the Funnelling filter permits.
        assertFalse(AugmentRules.funnelClaims(true, true, false, true));
    }

    @Test
    @DisplayName("with both fitted and mobs on, the filter decides")
    void bothFittedFollowsFilter() {
        assertTrue(AugmentRules.funnelClaims(true, true, true, true));
        assertFalse(AugmentRules.funnelClaims(true, true, true, false));
    }

    // -- Giant ---------------------------------------------------------------

    @Test
    @DisplayName("Giant costs a tile at the default speed stat")
    void giantChargesAtDefaultSpeed() {
        // Base SPEED is 3 out of the box, so the trade-off is real but survivable.
        assertEquals(1, AugmentRules.giantPenalty(3, 0, 1));
        assertEquals(2, AugmentRules.giantPenalty(3, 0, 2));
    }

    @Test
    @DisplayName("no backpack, no penalty")
    void noPenaltyWithoutGiant() {
        assertEquals(0, AugmentRules.giantPenalty(3, 0, 0));
        assertEquals(0, AugmentRules.giantPenalty(1, 0, 0));
    }

    @Test
    @DisplayName("the penalty is dropped rather than leaving a player unable to move")
    void neverStrandsThePlayer() {
        // A configured base speed of 1: charging the tile would leave zero movement, and a player
        // at zero movement cannot reach an enemy, leave a hazard or finish the level.
        assertEquals(0, AugmentRules.giantPenalty(1, 0, 1));
        // Exactly at the floor it still applies - one tile left is playable.
        assertEquals(1, AugmentRules.giantPenalty(2, 0, 1));
    }

    @Test
    @DisplayName("a negative armor set bonus can push the player under the floor on its own")
    void setBonusCountsTowardTheFloor() {
        // The floor is about the movement the player actually ends up with, not the stat alone.
        assertEquals(0, AugmentRules.giantPenalty(3, -2, 1));
        assertEquals(1, AugmentRules.giantPenalty(3, -1, 1));
    }

    @Test
    @DisplayName("a generous armor set can pay for the backpack")
    void setBonusCanAbsorbIt() {
        assertEquals(1, AugmentRules.giantPenalty(1, 2, 1));
    }
}
