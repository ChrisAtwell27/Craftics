package com.crackedgames.craftics.level.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Tests for the optional 2-way branch swap on {@link Campaign} — every biome always plays,
 * only order differs for {@code branchChoice == 1}; invalid swaps fall back to linear and
 * must not throw. Covers both the single-biome swap (length-1 segments via
 * {@link CampaignBranch#of}) and the general contiguous-segment swap.
 */
class CampaignBranchTest {

    private CampaignRegion surface() {
        return CampaignRegion.builder("surface")
            .node("village")
            .node("wildwood")
            .node("marsh")
            .build();
    }

    private CampaignRegion abyss() {
        return CampaignRegion.builder("abyss")
            .node("trench")
            .node("void_keep")
            .build();
    }

    private Campaign withBranch(CampaignBranch branch) {
        return Campaign.builder("test")
            .region(surface())
            .region(abyss())
            .branch(branch)
            .build();
    }

    @Test
    void choiceZero_keepsLinearOrder() {
        Campaign c = withBranch(CampaignBranch.of("surface", "wildwood", "marsh"));
        assertEquals(List.of("village", "wildwood", "marsh", "trench", "void_keep"),
            c.orderedBiomeIds(0));
    }

    @Test
    void choiceOne_swapsTheBranchPair() {
        Campaign c = withBranch(CampaignBranch.of("surface", "wildwood", "marsh"));
        assertEquals(List.of("village", "marsh", "wildwood", "trench", "void_keep"),
            c.orderedBiomeIds(1));
    }

    @Test
    void ordinalMovesWithSwap() {
        Campaign c = withBranch(CampaignBranch.of("surface", "wildwood", "marsh"));
        assertEquals(1, c.ordinalOf("wildwood", 0));
        assertEquals(2, c.ordinalOf("wildwood", 1));
    }

    @Test
    void totalBiomeCount_unaffectedByChoice() {
        Campaign c = withBranch(CampaignBranch.of("surface", "wildwood", "marsh"));
        assertEquals(5, c.totalBiomeCount());
        // count is branch-independent; sanity-check via both ordered lists
        assertEquals(c.orderedBiomeIds(0).size(), c.orderedBiomeIds(1).size());
    }

    @Test
    void isFinal_lastBiomeTrueForBothChoices() {
        Campaign c = withBranch(CampaignBranch.of("surface", "wildwood", "marsh"));
        assertTrue(c.isFinal("void_keep", 0));
        assertTrue(c.isFinal("void_keep", 1));
    }

    @Test
    void validBranch_reportsValid() {
        Campaign c = withBranch(CampaignBranch.of("surface", "wildwood", "marsh"));
        assertTrue(c.isBranchValid());
    }

    @Test
    void invalidSwap_fallsBackToLinearAndReportsInvalid() {
        Campaign c = withBranch(CampaignBranch.of("surface", "wildwood", "NOTHERE"));
        assertFalse(c.isBranchValid());
        // must not throw; choice 1 falls back to linear order
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void invalidRegion_fallsBackToLinearAndReportsInvalid() {
        Campaign c = withBranch(CampaignBranch.of("noregion", "wildwood", "marsh"));
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void linearCampaign_ignoresBranchChoice() {
        Campaign c = Campaign.builder("test")
            .region(surface())
            .region(abyss())
            .build();
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    // --- FIX 6: non-adjacent swap proves a position-trade, not a neighbor rotate ---

    @Test
    void nonAdjacentSwap_tradesPositionsLeavingMiddleInPlace() {
        // region [village, wildwood, marsh], swap [village, marsh] (wildwood sits between).
        Campaign c = withBranch(CampaignBranch.of("surface", "village", "marsh"));
        assertEquals(List.of("village", "wildwood", "marsh", "trench", "void_keep"),
            c.orderedBiomeIds(0));
        // village and marsh trade; wildwood stays put.
        assertEquals(List.of("marsh", "wildwood", "village", "trench", "void_keep"),
            c.orderedBiomeIds(1));
        // node objects follow the same trade.
        assertEquals(c.orderedBiomeIds(1),
            c.orderedNodes(1).stream().map(CampaignNode::biomeId).toList());
    }

    // --- FIX 6: swap involving the first node (index 0) ---

    @Test
    void firstNodeSwap_swapsIndexZero() {
        // swap [village, wildwood]: village is at index 0 of its region.
        Campaign c = withBranch(CampaignBranch.of("surface", "village", "wildwood"));
        assertEquals(List.of("wildwood", "village", "marsh", "trench", "void_keep"),
            c.orderedBiomeIds(1));
        assertEquals(0, c.ordinalOf("wildwood", 1));
        assertEquals(1, c.ordinalOf("village", 1));
    }

    // --- Segment swap: the vanilla overworld case (3-biome warm <-> 2-biome cool, pivot stays) ---

    /** Region mirroring vanilla branch 0: [a, w1,w2,w3, pivot, c1,c2, z]. */
    private Campaign segmentCampaign(CampaignBranch branch) {
        CampaignRegion world = CampaignRegion.builder("world")
            .node("a")
            .node("w1")
            .node("w2")
            .node("w3")
            .node("pivot")
            .node("c1")
            .node("c2")
            .node("z")
            .build();
        return Campaign.builder("vanillaish")
            .region(world)
            .branch(branch)
            .build();
    }

    @Test
    void segmentSwap_exchangesUnequalLengthBlocksAndKeepsPivotBetween() {
        // segA=[w1,w2,w3] (length 3) <-> segB=[c1,c2] (length 2); pivot sits between them.
        CampaignBranch branch = new CampaignBranch("world",
            List.of("w1", "w2", "w3"), List.of("c1", "c2"));
        Campaign c = segmentCampaign(branch);

        assertTrue(c.isBranchValid());
        // choice 0 stays linear.
        assertEquals(List.of("a", "w1", "w2", "w3", "pivot", "c1", "c2", "z"),
            c.orderedBiomeIds(0));
        // choice 1: the two segments trade, pivot remains between them, a and z unchanged.
        assertEquals(List.of("a", "c1", "c2", "pivot", "w1", "w2", "w3", "z"),
            c.orderedBiomeIds(1));
        // node objects mirror the swapped ids exactly.
        assertEquals(c.orderedBiomeIds(1),
            c.orderedNodes(1).stream().map(CampaignNode::biomeId).toList());
    }

    @Test
    void segmentSwap_ordinalsMoveAndPivotStaysBetween() {
        CampaignBranch branch = new CampaignBranch("world",
            List.of("w1", "w2", "w3"), List.of("c1", "c2"));
        Campaign c = segmentCampaign(branch);

        // c-block jumped to where the w-block was.
        assertEquals(1, c.ordinalOf("c1", 1));
        assertEquals(2, c.ordinalOf("c2", 1));
        // pivot shifted from index 4 to index 3 but is still strictly between the blocks.
        assertEquals(4, c.ordinalOf("pivot", 0));
        assertEquals(3, c.ordinalOf("pivot", 1));
        assertTrue(c.ordinalOf("c2", 1) < c.ordinalOf("pivot", 1));
        assertTrue(c.ordinalOf("pivot", 1) < c.ordinalOf("w1", 1));
        // w-block landed where the c-block was.
        assertEquals(4, c.ordinalOf("w1", 1));
        assertEquals(6, c.ordinalOf("w3", 1));
        // surrounding nodes untouched.
        assertEquals(0, c.ordinalOf("a", 1));
        assertEquals(7, c.ordinalOf("z", 1));
    }

    @Test
    void segmentSwap_countUnchangedAndCoversEveryBiomeOnce() {
        CampaignBranch branch = new CampaignBranch("world",
            List.of("w1", "w2", "w3"), List.of("c1", "c2"));
        Campaign c = segmentCampaign(branch);

        assertEquals(8, c.totalBiomeCount());
        assertEquals(c.orderedBiomeIds(0).size(), c.orderedBiomeIds(1).size());
        // same multiset of biomes, just reordered.
        assertEquals(c.orderedBiomeIds(0).stream().sorted().toList(),
            c.orderedBiomeIds(1).stream().sorted().toList());
    }

    @Test
    void segmentSwap_reversedSegmentArgumentsProduceSameResult() {
        // Passing segB first then segA must yield the identical swapped order (symmetry).
        Campaign forward = segmentCampaign(new CampaignBranch("world",
            List.of("w1", "w2", "w3"), List.of("c1", "c2")));
        Campaign reversed = segmentCampaign(new CampaignBranch("world",
            List.of("c1", "c2"), List.of("w1", "w2", "w3")));
        assertEquals(forward.orderedBiomeIds(1), reversed.orderedBiomeIds(1));
    }

    // --- Invalid segment cases -> linear fallback, no throw ---

    @Test
    void nonContiguousSegment_isInvalidLinearFallback() {
        // segA=[w1,w3] is NOT contiguous in [a,w1,w2,w3,...] because w2 sits between.
        Campaign c = segmentCampaign(new CampaignBranch("world",
            List.of("w1", "w3"), List.of("c1", "c2")));
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void segmentBiomeAbsentFromRegion_isInvalidLinearFallback() {
        Campaign c = segmentCampaign(new CampaignBranch("world",
            List.of("w1", "w2", "w3"), List.of("c1", "MISSING")));
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void overlappingSegments_areInvalidLinearFallback() {
        // segA=[w1,w2] and segB=[w2,w3] share w2 -> overlapping/interleaving.
        Campaign c = segmentCampaign(new CampaignBranch("world",
            List.of("w1", "w2"), List.of("w2", "w3")));
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void adjacentSegments_swapWithNoGapBetween() {
        // segA=[w1,w2,w3] directly followed by segB=[c1,c2] would need no pivot; use a region
        // [a, w1,w2,w3, c1,c2, z] so the two segments are adjacent (firstEnd+1 == secondStart).
        CampaignRegion world = CampaignRegion.builder("world")
            .node("a").node("w1").node("w2").node("w3").node("c1").node("c2").node("z")
            .build();
        Campaign c = Campaign.builder("adj")
            .region(world)
            .branch(new CampaignBranch("world", List.of("w1", "w2", "w3"), List.of("c1", "c2")))
            .build();
        assertTrue(c.isBranchValid());
        assertEquals(List.of("a", "c1", "c2", "w1", "w2", "w3", "z"), c.orderedBiomeIds(1));
    }

    // --- Constructor validation ---

    @Test
    void constructor_rejectsEmptySegment() {
        assertThrows(IllegalArgumentException.class,
            () -> new CampaignBranch("world", List.of(), List.of("c1")));
    }

    @Test
    void constructor_rejectsBlankBiomeInSegment() {
        assertThrows(IllegalArgumentException.class,
            () -> new CampaignBranch("world", List.of("w1", "  "), List.of("c1")));
    }

    @Test
    void constructor_rejectsBlankRegion() {
        assertThrows(IllegalArgumentException.class,
            () -> new CampaignBranch("  ", List.of("w1"), List.of("c1")));
    }

    @Test
    void of_buildsLengthOneSegments() {
        CampaignBranch b = CampaignBranch.of("surface", "wildwood", "marsh");
        assertEquals(List.of("wildwood"), b.segmentA());
        assertEquals(List.of("marsh"), b.segmentB());
    }

    @Test
    void segments_areUnmodifiable() {
        CampaignBranch b = new CampaignBranch("world",
            List.of("w1", "w2"), List.of("c1"));
        assertThrows(UnsupportedOperationException.class, () -> b.segmentA().add("x"));
    }
}
