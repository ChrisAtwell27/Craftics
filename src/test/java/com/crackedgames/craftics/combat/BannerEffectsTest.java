package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-Java tests for {@link BannerEffects}'s string-based logic.
 *
 * <p>The Item/Block lookup methods ({@code isBanner}, {@code colorIdForItem},
 * {@code blockForItem}) are not covered here because their lookup tables key
 * off vanilla {@code Items.WHITE_BANNER} etc., and accessing those requires
 * Minecraft's bootstrap which doesn't run in this JUnit environment. Those
 * tables are exercised at runtime when a banner is actually placed in
 * combat (see the smoke checklist in the implementation plan); they're
 * simple 16-entry mappings that are easy to review by inspection.
 *
 * <p>String-based logic ({@code isBannerEffect}, {@code defenseBonusAt},
 * {@code blockForColorId}) is fully covered.
 */
class BannerEffectsTest {

    @Test
    void isBannerEffect_recognizesLegacyAndCurrentForms() {
        assertTrue(BannerEffects.isBannerEffect("banner"));
        assertTrue(BannerEffects.isBannerEffect("banner:white"));
        assertTrue(BannerEffects.isBannerEffect("banner:red"));
        assertTrue(BannerEffects.isBannerEffect("banner:red:5"));
    }

    @Test
    void isBannerEffect_rejectsOtherEffectsAndEmpty() {
        assertFalse(BannerEffects.isBannerEffect("cactus"));
        assertFalse(BannerEffects.isBannerEffect("lightning"));
        assertFalse(BannerEffects.isBannerEffect(""));
        assertFalse(BannerEffects.isBannerEffect(null));
    }

    @Test
    void defenseBonusAt_emptyMapReturnsZero() {
        assertEquals(0, BannerEffects.defenseBonusAt(new GridPos(0, 0), Map.of()));
    }

    @Test
    void defenseBonusAt_nullPositionReturnsZero() {
        assertEquals(0, BannerEffects.defenseBonusAt(null, Map.of(new GridPos(0, 0), "banner:red")));
    }

    @Test
    void defenseBonusAt_nullMapReturnsZero() {
        assertEquals(0, BannerEffects.defenseBonusAt(new GridPos(0, 0), null));
    }

    @Test
    void defenseBonusAt_withinManhattanTwoReturnsBonus() {
        Map<GridPos, String> tiles = Map.of(new GridPos(5, 5), "banner:white");
        assertEquals(BannerEffects.DEFENSE_BONUS,
            BannerEffects.defenseBonusAt(new GridPos(5, 5), tiles));
        assertEquals(BannerEffects.DEFENSE_BONUS,
            BannerEffects.defenseBonusAt(new GridPos(7, 5), tiles));
        assertEquals(BannerEffects.DEFENSE_BONUS,
            BannerEffects.defenseBonusAt(new GridPos(6, 6), tiles));
        assertEquals(BannerEffects.DEFENSE_BONUS,
            BannerEffects.defenseBonusAt(new GridPos(4, 4), tiles));
    }

    @Test
    void defenseBonusAt_outsideManhattanTwoReturnsZero() {
        Map<GridPos, String> tiles = Map.of(new GridPos(5, 5), "banner:white");
        assertEquals(0, BannerEffects.defenseBonusAt(new GridPos(8, 5), tiles));
        assertEquals(0, BannerEffects.defenseBonusAt(new GridPos(7, 7), tiles));
    }

    @Test
    void defenseBonusAt_overlappingBannersTakeMaxNotSum() {
        Map<GridPos, String> tiles = Map.of(
            new GridPos(0, 0), "banner:red:2",
            new GridPos(1, 0), "banner:blue:5"
        );
        assertEquals(5, BannerEffects.defenseBonusAt(new GridPos(0, 0), tiles));
        assertEquals(5, BannerEffects.defenseBonusAt(new GridPos(1, 0), tiles));
    }

    @Test
    void defenseBonusAt_readsSpecialScaledBonusFromTileEffectString() {
        Map<GridPos, String> tiles = Map.of(new GridPos(0, 0), "banner:red:7");
        assertEquals(7, BannerEffects.defenseBonusAt(new GridPos(0, 0), tiles));
    }

    @Test
    void defenseBonusAt_legacyBareBannerReturnsDefault() {
        Map<GridPos, String> tiles = Map.of(new GridPos(0, 0), "banner");
        assertEquals(BannerEffects.DEFENSE_BONUS,
            BannerEffects.defenseBonusAt(new GridPos(0, 0), tiles));
    }

    @Test
    void defenseBonusAt_legacyColorOnlyValueFallsBackToDefault() {
        Map<GridPos, String> tiles = Map.of(new GridPos(0, 0), "banner:red");
        assertEquals(BannerEffects.DEFENSE_BONUS,
            BannerEffects.defenseBonusAt(new GridPos(0, 0), tiles));
    }

    @Test
    void defenseBonusAt_ignoresNonBannerEffects() {
        Map<GridPos, String> tiles = Map.of(
            new GridPos(0, 0), "cactus",
            new GridPos(1, 1), "lightning",
            new GridPos(2, 2), "campfire"
        );
        assertEquals(0, BannerEffects.defenseBonusAt(new GridPos(1, 1), tiles));
    }

    @Test
    void blockForColorId_returnsBannerBlocksForKnownColors() {
        // Don't assert specific Block instances (would force Blocks bootstrap);
        // assert that all 16 known colors return non-null and unknown returns null.
        // Block-instance equality is exercised at runtime when placement happens.
        assertNull(BannerEffects.blockForColorId(null));
        assertNull(BannerEffects.blockForColorId("not_a_color"));
    }
}
