package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Conduction spacing rules. The chain measures each jump from the LAST combatant
 * struck, never from the origin - clustering conducts, spreading breaks the chain, and a
 * line of bodies carries the bolt across the whole arena. These tests pin that geometry;
 * if the walk ever silently becomes "everything within N of the origin", the mechanic
 * degenerates into a plain AoE and this file fails.
 */
class ConductionChainTest {

    /** The game's link range: a combatant conducts within 2 tiles of the last one struck. */
    private static final int RANGE = 2;

    @Test
    void aCongaLineCarriesTheBoltAcrossTheArena() {
        // Each is 2 from the previous but up to 6 from the origin: only last-link
        // measurement reaches them all.
        List<GridPos> line = List.of(
            new GridPos(0, 0), new GridPos(2, 0), new GridPos(4, 0), new GridPos(6, 0));
        List<ConductionChain.Link> chain = ConductionChain.walk(line, 0, RANGE);
        assertEquals(4, chain.size(), "every link in the line must conduct");
        for (int i = 0; i < 4; i++) {
            assertEquals(i, chain.get(i).index());
            assertEquals(i, chain.get(i).depth(), "depth must grow one per jump");
        }
    }

    @Test
    void spacingBreaksTheChain() {
        List<GridPos> spread = List.of(
            new GridPos(0, 0), new GridPos(3, 0), new GridPos(0, 5));
        List<ConductionChain.Link> chain = ConductionChain.walk(spread, 0, RANGE);
        assertEquals(1, chain.size(), "3+ tiles of spacing must stop the bolt");
        assertEquals(0, chain.get(0).index());
    }

    @Test
    void aClusterAroundTheMarkAllConductsAtDepthOne() {
        List<GridPos> cluster = List.of(
            new GridPos(5, 5), new GridPos(6, 5), new GridPos(4, 4), new GridPos(5, 7));
        List<ConductionChain.Link> chain = ConductionChain.walk(cluster, 0, RANGE);
        assertEquals(4, chain.size());
        for (int i = 1; i < 4; i++) {
            assertEquals(1, chain.get(i).depth(), "everything within 2 of the mark is one jump away");
        }
    }

    /** Breadth-first: both depth-1 links strike before anything at depth 2. */
    @Test
    void strikeOrderIsBreadthFirst() {
        List<GridPos> tree = List.of(
            new GridPos(0, 0),   // mark
            new GridPos(2, 0),   // depth 1
            new GridPos(0, 2),   // depth 1
            new GridPos(4, 0));  // depth 2, via index 1
        List<ConductionChain.Link> chain = ConductionChain.walk(tree, 0, RANGE);
        assertEquals(4, chain.size());
        assertEquals(0, chain.get(0).depth());
        assertEquals(1, chain.get(1).depth());
        assertEquals(1, chain.get(2).depth());
        assertEquals(2, chain.get(3).depth());
        assertEquals(3, chain.get(3).index());
    }

    /** The mark can start mid-pack: the bolt spreads in both directions from wherever it lands. */
    @Test
    void aMarkInTheMiddleSpreadsBothWays() {
        List<GridPos> line = List.of(
            new GridPos(0, 0), new GridPos(2, 0), new GridPos(4, 0));
        List<ConductionChain.Link> chain = ConductionChain.walk(line, 1, RANGE);
        assertEquals(3, chain.size());
        assertEquals(1, chain.get(0).index());
        assertEquals(0, chain.get(0).depth());
        // Both neighbours are one jump from the centre mark.
        assertEquals(1, chain.get(1).depth());
        assertEquals(1, chain.get(2).depth());
    }

    @Test
    void anAbsentMarkFizzlesToAnEmptyChain() {
        List<GridPos> some = List.of(new GridPos(0, 0));
        assertTrue(ConductionChain.walk(some, -1, RANGE).isEmpty());
        assertTrue(ConductionChain.walk(some, 1, RANGE).isEmpty());
        assertTrue(ConductionChain.walk(List.of(), 0, RANGE).isEmpty());
    }

    /** Damage decays 1 per jump and floors at 1, matching the Guster sherd's chain. */
    @Test
    void damageDecaysPerJumpAndFloorsAtOne() {
        assertEquals(6, ConductionChain.damageAt(6, 0));
        assertEquals(5, ConductionChain.damageAt(6, 1));
        assertEquals(1, ConductionChain.damageAt(6, 5));
        assertEquals(1, ConductionChain.damageAt(4, 9), "a long chain still stings at the end");
    }
}
