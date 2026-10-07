package com.crackedgames.craftics.level.campaign;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An optional region that opens beside the campaign must leave the campaign exactly as it was.
 *
 * <p>Everything here is the same shape as the case it was built for: a campaign of
 * overworld, nether and end, and a sky region that opens when the nether's last biome falls -
 * the same moment the end does. The risk in adding one is not that the new region misbehaves
 * but that it quietly changes the old ones: a longer campaign, a different final boss, an end
 * biome opened by clearing a dungeon. So most of these assert what did NOT change.
 */
class CampaignSideRegionTest {

    /** overworld [plains, cave] - nether [wastes, deltas] - end [islands, nest]. */
    private static Campaign campaign() {
        return Campaign.builder("test:main")
            .region(CampaignRegion.builder("overworld").node("plains").node("cave").build())
            .region(CampaignRegion.builder("nether").node("wastes").node("deltas").build())
            .region(CampaignRegion.builder("end").node("islands").node("nest").build())
            .build();
    }

    /** sky [bronze, silver, gold], opening when "deltas" (campaign position 4) is cleared. */
    private static CampaignSideRegion sky() {
        return new CampaignSideRegion(
            CampaignRegion.builder("sky").displayName("The Sky")
                .node("bronze", "Bronze").node("silver").node("gold").build(),
            "deltas");
    }

    @BeforeEach
    void setUp() {
        CampaignManager.register(campaign());
        CampaignManager.registerSideRegion(sky());
    }

    @AfterEach
    void cleanup() {
        CampaignManager.clearAllForTest();
    }

    // ── The campaign is untouched ──

    @Test
    @DisplayName("the campaign line does not grow, and its final biome does not move")
    void campaignLineIsUnchanged() {
        assertEquals(List.of("plains", "cave", "wastes", "deltas", "islands", "nest"),
            CampaignManager.orderedBiomeIds(0));
        assertEquals(6, CampaignManager.totalBiomes());
        assertEquals(3, CampaignManager.regions().size(), "side regions are not campaign regions");
        assertTrue(CampaignManager.isFinalBiome("nest", 0));
        assertFalse(CampaignManager.isFinalBiome("gold", 0),
            "finishing the optional region is not finishing the campaign");
    }

    @Test
    @DisplayName("campaign biomes keep their ordinals")
    void campaignOrdinalsAreUnchanged() {
        assertEquals(0, CampaignManager.ordinalOf("plains", 0));
        assertEquals(3, CampaignManager.ordinalOf("deltas", 0));
        assertEquals(4, CampaignManager.ordinalOf("islands", 0));
        assertEquals(5, CampaignManager.ordinalOf("nest", 0));
        assertEquals(-1, CampaignManager.ordinalOf("nowhere", 0));
    }

    // ── What a side biome reads as ──

    @Test
    @DisplayName("a side biome is as hard as the campaign biome that opens beside it")
    void difficultyTracksTheParallelRegion() {
        // "deltas" is ordinal 3, so what opens next - on either track - plays at 4.
        assertEquals(CampaignManager.ordinalOf("islands", 0), CampaignManager.ordinalOf("bronze", 0));
        assertEquals(5, CampaignManager.ordinalOf("silver", 0));
        assertEquals(6, CampaignManager.ordinalOf("gold", 0), "one step past the campaign's last");
    }

    @Test
    @DisplayName("region and node lookups reach side regions")
    void lookupsReachSideRegions() {
        assertEquals("sky", CampaignManager.regionOf("silver").id());
        assertEquals("nether", CampaignManager.regionOf("deltas").id());
        assertEquals("The Sky", CampaignManager.regionById("sky").displayName());
        assertEquals("Bronze", CampaignManager.nodeOf("bronze").labelOverride());
        assertEquals("sky", CampaignManager.sideRegionOf("gold").region().id());
        assertNull(CampaignManager.sideRegionOf("islands"));
        assertNull(CampaignManager.sideRegionOf(null));
    }

    // ── Unlocking ──
    // The cursor is the 1-based position of the furthest campaign biome an island may play:
    // 1 on a fresh island, k + 1 once the biome at position k is cleared.

    @Test
    @DisplayName("the region opens the moment its anchor is cleared, alongside the next campaign region")
    void opensWithTheAnchor() {
        CampaignSideRegion sky = CampaignManager.sideRegionOf("bronze");
        assertFalse(CampaignManager.isSideRegionOpen(sky, 0, 1), "fresh island");
        assertFalse(CampaignManager.isSideRegionOpen(sky, 0, 4), "standing at the anchor, not past it");
        assertTrue(CampaignManager.isSideRegionOpen(sky, 0, 5), "anchor cleared");
        // Position 5 is "islands": the end's first biome opens on exactly the same cursor.
        assertEquals(5, CampaignManager.ordinalOf("islands", 0) + 1);
    }

    @Test
    @DisplayName("only the first side biome opens with the region")
    void sideBiomesOpenInOrder() {
        assertTrue(CampaignManager.isSideBiomeUnlocked("bronze", 0, 5, 0));
        assertFalse(CampaignManager.isSideBiomeUnlocked("silver", 0, 5, 0));
        assertTrue(CampaignManager.isSideBiomeUnlocked("silver", 0, 5, 1));
        assertFalse(CampaignManager.isSideBiomeUnlocked("gold", 0, 5, 1));
        assertTrue(CampaignManager.isSideBiomeUnlocked("gold", 0, 5, 2));
    }

    @Test
    @DisplayName("campaign progress does not open side biomes, however far it goes")
    void theCursorDoesNotOpenSideBiomes() {
        // The trap this design exists to avoid: "silver" borrows ordinal 5 for difficulty,
        // and an island that has cleared the whole campaign has a cursor far past that.
        assertFalse(CampaignManager.isSideBiomeUnlocked("silver", 0, 99, 0));
        assertFalse(CampaignManager.isSideBiomeUnlocked("gold", 0, 99, 1));
    }

    @Test
    @DisplayName("side progress does not open a region whose anchor is still standing")
    void progressDoesNotBypassTheAnchor() {
        assertFalse(CampaignManager.isSideBiomeUnlocked("bronze", 0, 4, 3));
    }

    @Test
    @DisplayName("a campaign biome is never a side biome")
    void campaignBiomesAreNotSideBiomes() {
        assertFalse(CampaignManager.isSideBiomeUnlocked("islands", 0, 99, 99));
    }

    // ── When a side region is in play ──

    @Test
    @DisplayName("a side region whose anchor the campaign lacks is not in play")
    void anchorMustBeInTheActiveCampaign() {
        CampaignManager.clearAllForTest();
        CampaignManager.register(Campaign.builder("test:other")
            .region(CampaignRegion.builder("only").node("meadow").build())
            .build());
        CampaignManager.registerSideRegion(sky());

        assertTrue(CampaignManager.sideRegions().isEmpty());
        assertEquals(-1, CampaignManager.ordinalOf("bronze", 0));
        assertNull(CampaignManager.regionOf("bronze"));
    }

    @Test
    @DisplayName("a side region that overlaps the campaign is left out rather than half-applied")
    void overlapIsRejected() {
        CampaignManager.registerSideRegion(new CampaignSideRegion(
            CampaignRegion.builder("clash").node("islands").build(), "deltas"));
        assertNull(CampaignManager.regionById("clash"));
        assertEquals("end", CampaignManager.regionOf("islands").id());

        CampaignManager.registerSideRegion(new CampaignSideRegion(
            CampaignRegion.builder("end").node("elsewhere").build(), "deltas"));
        assertNull(CampaignManager.sideRegionOf("elsewhere"), "shares an id with a campaign region");
    }

    @Test
    @DisplayName("re-registering a region id replaces it")
    void reRegistrationReplaces() {
        CampaignManager.registerSideRegion(new CampaignSideRegion(
            CampaignRegion.builder("sky").node("copper").build(), "cave"));
        assertEquals(1, CampaignManager.sideRegions().size());
        assertNull(CampaignManager.sideRegionOf("bronze"));
        assertEquals(2, CampaignManager.ordinalOf("copper", 0), "anchored on cave (ordinal 1) now");
    }

    @Test
    @DisplayName("a side region needs a region with biomes and an anchor")
    void validation() {
        CampaignRegion region = CampaignRegion.builder("sky").node("bronze").build();
        assertThrows(IllegalArgumentException.class, () -> new CampaignSideRegion(null, "deltas"));
        assertThrows(IllegalArgumentException.class, () -> new CampaignSideRegion(region, " "));
        assertThrows(IllegalArgumentException.class, () -> new CampaignSideRegion(
            CampaignRegion.builder("empty").build(), "deltas"));
    }

    // ── The progress string ──

    @Test
    @DisplayName("progress round-trips and keeps regions apart")
    void progressFormat() {
        String p = SideProgress.with("", "sky", 2);
        assertEquals("sky=2", p);
        p = SideProgress.with(p, "mymod:deep", 1);
        assertEquals(2, SideProgress.get(p, "sky"));
        assertEquals(1, SideProgress.get(p, "mymod:deep"), "a namespaced id keeps its colon");
        assertEquals(0, SideProgress.get(p, "unknown"));
        assertEquals("mymod:deep=1", SideProgress.with(p, "sky", 0), "zero removes the entry");
    }

    @Test
    @DisplayName("a damaged progress string costs progress, not a crash")
    void progressIsForgiving() {
        assertEquals(0, SideProgress.get(null, "sky"));
        assertEquals(0, SideProgress.get("", "sky"));
        assertEquals(0, SideProgress.get("sky", "sky"));
        assertEquals(0, SideProgress.get("sky=abc", "sky"));
        assertEquals(0, SideProgress.get("sky=-3", "sky"));
        assertEquals(2, SideProgress.get("junk,,sky=2,=5,other=", "sky"));
        assertEquals("sky=1", SideProgress.with(null, "sky", 1));
    }
}
