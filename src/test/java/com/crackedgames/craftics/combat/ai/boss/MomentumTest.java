package com.crackedgames.craftics.combat.ai.boss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MomentumTest {

    @Test
    void standingStillGivesNoBonus() {
        assertEquals(0, Momentum.bonusForTilesMoved(0));
    }

    @Test
    void bonusIsOnePerTileMoved() {
        assertEquals(1, Momentum.bonusForTilesMoved(1));
        assertEquals(3, Momentum.bonusForTilesMoved(3));
        assertEquals(7, Momentum.bonusForTilesMoved(7));
    }

    @Test
    void negativeDistanceIsTreatedAsZero() {
        // Defensive: a caller passing a bad path length must not heal the player.
        assertEquals(0, Momentum.bonusForTilesMoved(-1));
    }

    @Test
    void marchBannerIsWorthBreaking() {
        // Exact values, not just "less than". An earlier draft of the old banner curve rounded
        // the last banner down to +0, so breaking it changed nothing and the mechanic was dead
        // on its final step. A monotonic-decrease assertion PASSED against that bug; only
        // pinning the exact numbers catches it. Same rigor now that speed rides one banner:
        // a March that granted +0 while standing would be a banner with no effect at all.
        assertEquals(2, Momentum.marchSpeedBonus(true));
        assertEquals(0, Momentum.marchSpeedBonus(false));
    }

    @Test
    void breakingTheMarchRemovesAllOfItsSpeed() {
        // The drop is the whole mechanic: the March is the only source of banner speed, so
        // breaking it must return the Brute to base speed rather than shaving a point off.
        assertEquals(2, Momentum.marchSpeedBonus(true) - Momentum.marchSpeedBonus(false));
    }

    @Test
    void marchDownLeavesNoSpeedBonus() {
        // The Brute is slow with the March gone, but CombatEntity.getMoveSpeed already floors
        // enemies at 1 tile per turn, so it still closes. A boss reducible to harmless is not
        // a fight; that floor is what guarantees it.
        assertEquals(0, Momentum.marchSpeedBonus(false));
    }

    @Test
    void furyBannerIsWorthBreaking() {
        // Pinned exactly for the same reason as the March: a Fury granting +0 while standing
        // would be a banner the player could break with nothing to show for it.
        assertEquals(3, Momentum.furyAttackBonus(true));
        assertEquals(0, Momentum.furyAttackBonus(false));
    }

    @Test
    void breakingFuryRemovesAllOfItsAttack() {
        assertEquals(3, Momentum.furyAttackBonus(true) - Momentum.furyAttackBonus(false));
    }

    @Test
    void bannerBonusesAreIndependent() {
        // The two banners are separate levers on purpose. Speed must not read attack state or
        // vice versa, or the "which one do I break" decision collapses back into one choice.
        assertEquals(2, Momentum.marchSpeedBonus(true));
        assertEquals(3, Momentum.furyAttackBonus(true));
        assertEquals(0, Momentum.marchSpeedBonus(false));
        assertEquals(0, Momentum.furyAttackBonus(false));
    }
}
