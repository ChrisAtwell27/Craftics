package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShrineRewardsTest {

    @Test
    void bandThresholds_smallTier() {
        // tier 0 (small): bust 25, threshold 40. roll<25 NOTHING, <40 COMMON, <75 GOOD, <95 GREAT, else JACKPOT.
        assertEquals(ShrineRewards.Band.NOTHING, ShrineRewards.band(0, 0,   0));
        assertEquals(ShrineRewards.Band.COMMON,  ShrineRewards.band(0, 39,  0));
        assertEquals(ShrineRewards.Band.GOOD,    ShrineRewards.band(0, 40,  0));
        assertEquals(ShrineRewards.Band.GOOD,    ShrineRewards.band(0, 74,  0));
        assertEquals(ShrineRewards.Band.GREAT,   ShrineRewards.band(0, 75,  0));
        assertEquals(ShrineRewards.Band.GREAT,   ShrineRewards.band(0, 94,  0));
        assertEquals(ShrineRewards.Band.JACKPOT, ShrineRewards.band(0, 95,  0));
        assertEquals(ShrineRewards.Band.JACKPOT, ShrineRewards.band(0, 99,  0));
    }

    @Test
    void bandThresholds_largeTier() {
        // tier 2 (large): bust 8, threshold 10. roll<8 NOTHING, <10 COMMON, <45 GOOD, <65 GREAT, else JACKPOT.
        assertEquals(ShrineRewards.Band.COMMON,  ShrineRewards.band(2, 9,  0));
        assertEquals(ShrineRewards.Band.GOOD,    ShrineRewards.band(2, 10, 0));
        assertEquals(ShrineRewards.Band.GOOD,    ShrineRewards.band(2, 44, 0));
        assertEquals(ShrineRewards.Band.GREAT,   ShrineRewards.band(2, 45, 0));
        assertEquals(ShrineRewards.Band.GREAT,   ShrineRewards.band(2, 64, 0));
        assertEquals(ShrineRewards.Band.JACKPOT, ShrineRewards.band(2, 65, 0));
    }

    @Test
    void lootTierBonusShiftsRollDown() {
        // bonus shifts the roll down by 8 per tier; tier 0 bust 25.
        assertEquals(ShrineRewards.Band.COMMON,  ShrineRewards.band(0, 40, 1)); // 40-8=32, in [25,40)
        assertEquals(ShrineRewards.Band.NOTHING, ShrineRewards.band(0, 5, 1));  // 5-8 clamps to 0, <25
    }

    @Test
    void costForTier() {
        assertEquals(2,  ShrineRewards.cost(0));
        assertEquals(5,  ShrineRewards.cost(1));
        assertEquals(10, ShrineRewards.cost(2));
    }

    @Test
    void bustBand_lowRollsGiveNothing() {
        // Per-tier bust: small 25, medium 15, large 8.
        assertEquals(ShrineRewards.Band.NOTHING, ShrineRewards.band(0, 0,  0));
        assertEquals(ShrineRewards.Band.NOTHING, ShrineRewards.band(0, 24, 0));
        assertEquals(ShrineRewards.Band.COMMON,  ShrineRewards.band(0, 25, 0)); // first non-bust
        assertEquals(ShrineRewards.Band.NOTHING, ShrineRewards.band(1, 14, 0));
        assertEquals(ShrineRewards.Band.COMMON,  ShrineRewards.band(1, 15, 0));
        assertEquals(ShrineRewards.Band.NOTHING, ShrineRewards.band(2, 7,  0));
        assertEquals(ShrineRewards.Band.COMMON,  ShrineRewards.band(2, 8,  0)); // narrow common band
        assertEquals(ShrineRewards.Band.GOOD,    ShrineRewards.band(2, 10, 0));
    }

    @Test
    void upperBandsUnchangedBySust() {
        // tier 0: GOOD 40..74, GREAT 75..94, JACKPOT 95+ still hold.
        assertEquals(ShrineRewards.Band.GOOD,    ShrineRewards.band(0, 40, 0));
        assertEquals(ShrineRewards.Band.GREAT,   ShrineRewards.band(0, 75, 0));
        assertEquals(ShrineRewards.Band.JACKPOT, ShrineRewards.band(0, 95, 0));
    }
}
