package com.crackedgames.craftics.level.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Tests for the pure {@link Campaign} model — flatten order, ordinal/region/node
 * lookups, final detection, total count, and validation.
 */
class CampaignTest {

    private Campaign twoRegionCampaign() {
        CampaignRegion surface = CampaignRegion.builder("surface")
            .displayName("Surface")
            .node("village")
            .node("wildwood", "The Wildwood")
            .node("marsh")
            .build();
        CampaignRegion abyss = CampaignRegion.builder("abyss")
            .node("trench")
            .node("void_keep")
            .build();
        return Campaign.builder("test")
            .displayName("Test Campaign")
            .region(surface)
            .region(abyss)
            .build();
    }

    @Test
    void orderedBiomeIds_flattensInRegionThenNodeOrder() {
        Campaign c = twoRegionCampaign();
        assertEquals(List.of("village", "wildwood", "marsh", "trench", "void_keep"),
            c.orderedBiomeIds(0));
    }

    @Test
    void ordinalOf_returnsFlattenedIndex() {
        Campaign c = twoRegionCampaign();
        assertEquals(0, c.ordinalOf("village", 0));
        assertEquals(1, c.ordinalOf("wildwood", 0));
        assertEquals(2, c.ordinalOf("marsh", 0));
        assertEquals(3, c.ordinalOf("trench", 0));
        assertEquals(4, c.ordinalOf("void_keep", 0));
    }

    @Test
    void regionOf_findsOwningRegion() {
        Campaign c = twoRegionCampaign();
        assertEquals("surface", c.regionOf("village").id());
        assertEquals("surface", c.regionOf("marsh").id());
        assertEquals("abyss", c.regionOf("trench").id());
        assertEquals("abyss", c.regionOf("void_keep").id());
    }

    @Test
    void isFinal_onlyTrueForLastBiome() {
        Campaign c = twoRegionCampaign();
        assertTrue(c.isFinal("void_keep", 0));
        assertFalse(c.isFinal("trench", 0));
        assertFalse(c.isFinal("village", 0));
    }

    @Test
    void totalBiomeCount_countsAllNodes() {
        Campaign c = twoRegionCampaign();
        assertEquals(5, c.totalBiomeCount());
    }

    @Test
    void lookups_returnAbsentSignalsForUnknownBiome() {
        Campaign c = twoRegionCampaign();
        assertEquals(-1, c.ordinalOf("nonexistent", 0));
        assertNull(c.regionOf("nonexistent"));
        assertNull(c.nodeOf("nonexistent"));
    }

    @Test
    void build_throwsOnEmptyRegions() {
        // Validation now lives in the canonical constructor (IllegalArgumentException).
        assertThrows(IllegalArgumentException.class,
            () -> Campaign.builder("empty").build());
    }

    @Test
    void constructor_throwsOnEmptyRegions() {
        assertThrows(IllegalArgumentException.class,
            () -> new Campaign("empty", "Empty", List.of(), null));
    }

    @Test
    void nodeOf_labelOverrideRoundTrips() {
        Campaign c = twoRegionCampaign();
        assertEquals("The Wildwood", c.nodeOf("wildwood").labelOverride());
        assertNull(c.nodeOf("village").labelOverride());
    }

    // --- FIX 4: orderedNodes returns node objects in branch-swapped run order ---

    @Test
    void orderedNodes_choiceZero_returnsNodesInLinearOrder() {
        Campaign c = twoRegionCampaign();
        List<CampaignNode> nodes = c.orderedNodes(0);
        assertEquals(List.of("village", "wildwood", "marsh", "trench", "void_keep"),
            nodes.stream().map(CampaignNode::biomeId).toList());
        // The actual node objects must be returned (labelOverride readable per ordinal).
        assertEquals("The Wildwood", nodes.get(1).labelOverride());
    }

    @Test
    void orderedNodes_choiceOne_reordersNodesToMatchOrderedBiomeIds() {
        CampaignRegion surface = CampaignRegion.builder("surface")
            .node("village")
            .node("wildwood", "The Wildwood")
            .node("marsh", "The Marsh")
            .build();
        CampaignRegion abyss = CampaignRegion.builder("abyss")
            .node("trench")
            .node("void_keep")
            .build();
        Campaign c = Campaign.builder("test")
            .region(surface)
            .region(abyss)
            .branch(CampaignBranch.of("surface", "wildwood", "marsh"))
            .build();

        List<CampaignNode> nodes = c.orderedNodes(1);
        // Node ids must mirror orderedBiomeIds(1) exactly.
        assertEquals(c.orderedBiomeIds(1),
            nodes.stream().map(CampaignNode::biomeId).toList());
        assertEquals(List.of("village", "marsh", "wildwood", "trench", "void_keep"),
            nodes.stream().map(CampaignNode::biomeId).toList());
        // The swapped node OBJECTS travel with their ids: marsh's node sits at ordinal 1.
        assertEquals("The Marsh", nodes.get(1).labelOverride());
        assertEquals("The Wildwood", nodes.get(2).labelOverride());
    }

    @Test
    void orderedNodes_isUnmodifiable() {
        Campaign c = twoRegionCampaign();
        assertThrows(UnsupportedOperationException.class,
            () -> c.orderedNodes(0).add(CampaignNode.of("x")));
    }

    // --- FIX 5: null displayName defaults to id via the canonical constructor ---

    @Test
    void constructor_nullDisplayNameDefaultsToId() {
        Campaign c = new Campaign("solo",
            null,
            List.of(CampaignRegion.builder("only").node("a").build()),
            null);
        assertEquals("solo", c.displayName());
    }

    // --- FIX 6: single-region campaign ---

    @Test
    void singleRegionCampaign_flattenOrdinalIsFinalAndCountAllWork() {
        Campaign c = Campaign.builder("solo")
            .region(CampaignRegion.builder("only")
                .node("a")
                .node("b")
                .node("c")
                .build())
            .build();
        assertEquals(List.of("a", "b", "c"), c.orderedBiomeIds(0));
        assertEquals(0, c.ordinalOf("a", 0));
        assertEquals(2, c.ordinalOf("c", 0));
        assertTrue(c.isFinal("c", 0));
        assertFalse(c.isFinal("a", 0));
        assertEquals(3, c.totalBiomeCount());
    }

    // --- FIX 6: duplicate biome id contract (first occurrence wins) ---

    @Test
    void duplicateBiomeId_firstOccurrenceWinsForLookups() {
        CampaignRegion first = CampaignRegion.builder("first")
            .node("shared", "First Label")
            .build();
        CampaignRegion second = CampaignRegion.builder("second")
            .node("shared", "Second Label")
            .build();
        Campaign c = Campaign.builder("dup")
            .region(first)
            .region(second)
            .build();
        // Documented contract: first occurrence wins for ordinalOf/regionOf/nodeOf.
        assertEquals(0, c.ordinalOf("shared", 0));
        assertEquals("first", c.regionOf("shared").id());
        assertEquals("First Label", c.nodeOf("shared").labelOverride());
        // Both nodes still flatten into the order (count is by node, not distinct id).
        assertEquals(2, c.totalBiomeCount());
        assertEquals(List.of("shared", "shared"), c.orderedBiomeIds(0));
    }
}
