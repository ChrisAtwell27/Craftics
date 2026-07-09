package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static com.crackedgames.craftics.combat.ArmorClassTable.Slot;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure AC math in {@link ArmorClassTable} - per-piece formula and
 * full-set totals. The registry-backed {@code getPieceAC(Item)} path is covered
 * by the in-game smoke checklist (it needs a Minecraft bootstrap).
 */
class ArmorClassTableTest {

    /** Sums a full set for a material via the pure formula. */
    private static int fullSet(int b) {
        return ArmorClassTable.pieceAC(b, Slot.LEGGINGS)
             + ArmorClassTable.pieceAC(b, Slot.CHESTPLATE)
             + ArmorClassTable.pieceAC(b, Slot.HELMET)
             + ArmorClassTable.pieceAC(b, Slot.BOOTS);
    }

    // ---- Material base AC ----

    @Test
    void baseAC_matchesSpecTable() {
        assertEquals(2, ArmorClassTable.baseAC("leather"));
        assertEquals(3, ArmorClassTable.baseAC("chainmail"));
        assertEquals(3, ArmorClassTable.baseAC("golden"));
        assertEquals(4, ArmorClassTable.baseAC("iron"));
        assertEquals(4, ArmorClassTable.baseAC("copper"));
        assertEquals(4, ArmorClassTable.baseAC("turtle"));
        assertEquals(6, ArmorClassTable.baseAC("diamond"));
        assertEquals(7, ArmorClassTable.baseAC("netherite"));
    }

    @Test
    void baseAC_unknownMaterialIsZero() {
        assertEquals(0, ArmorClassTable.baseAC("mithril"));
        assertEquals(0, ArmorClassTable.baseAC(""));
    }

    // ---- Enemy DEF compression: ceil(AC / 3), material-scaled ----

    /** Sums a full set's enemy DEF for a material via the pure formula. */
    private static int fullSetDefense(int b) {
        return ArmorClassTable.pieceDefenseFromAC(ArmorClassTable.pieceAC(b, Slot.LEGGINGS))
             + ArmorClassTable.pieceDefenseFromAC(ArmorClassTable.pieceAC(b, Slot.CHESTPLATE))
             + ArmorClassTable.pieceDefenseFromAC(ArmorClassTable.pieceAC(b, Slot.HELMET))
             + ArmorClassTable.pieceDefenseFromAC(ArmorClassTable.pieceAC(b, Slot.BOOTS));
    }

    @Test
    void pieceDefense_isCeilOfAcOverThree() {
        assertEquals(0, ArmorClassTable.pieceDefenseFromAC(0));
        assertEquals(0, ArmorClassTable.pieceDefenseFromAC(-4)); // guards non-armor
        assertEquals(1, ArmorClassTable.pieceDefenseFromAC(1));
        assertEquals(1, ArmorClassTable.pieceDefenseFromAC(3));
        assertEquals(2, ArmorClassTable.pieceDefenseFromAC(4));
        assertEquals(2, ArmorClassTable.pieceDefenseFromAC(6)); // diamond leggings
        assertEquals(3, ArmorClassTable.pieceDefenseFromAC(7)); // diamond chestplate
    }

    @Test
    void fullSetDefense_ranksMaterialsAndStaysInDefBudget() {
        // Leather < iron/copper < diamond < netherite, all within the 5%/60% DEF stat.
        assertEquals(4, fullSetDefense(2));  // leather:   1+1+1+1 -> 20% reduction
        assertEquals(6, fullSetDefense(4));  // iron/copper: 2+2+1+1 -> 30%
        assertEquals(7, fullSetDefense(6));  // diamond:   2+3+1+1 -> 35%
        assertEquals(10, fullSetDefense(7)); // netherite: 3+3+2+2 -> 50% (under 60% cap)
        assertTrue(fullSetDefense(2) < fullSetDefense(4));
        assertTrue(fullSetDefense(4) < fullSetDefense(6));
        assertTrue(fullSetDefense(6) < fullSetDefense(7));
    }

    // ---- Per-piece formula: legs=B, chest=B+1, helm=boots=ceil(B/2) ----

    @Test
    void pieceAC_leather() {
        assertEquals(2, ArmorClassTable.pieceAC(2, Slot.LEGGINGS));
        assertEquals(3, ArmorClassTable.pieceAC(2, Slot.CHESTPLATE));
        assertEquals(1, ArmorClassTable.pieceAC(2, Slot.HELMET));
        assertEquals(1, ArmorClassTable.pieceAC(2, Slot.BOOTS));
    }

    @Test
    void pieceAC_iron() {
        assertEquals(4, ArmorClassTable.pieceAC(4, Slot.LEGGINGS));
        assertEquals(5, ArmorClassTable.pieceAC(4, Slot.CHESTPLATE));
        assertEquals(2, ArmorClassTable.pieceAC(4, Slot.HELMET));
        assertEquals(2, ArmorClassTable.pieceAC(4, Slot.BOOTS));
    }

    @Test
    void pieceAC_ceilingForOddBase() {
        // B=3 → ⌈3/2⌉ = 2
        assertEquals(2, ArmorClassTable.pieceAC(3, Slot.HELMET));
        // B=7 → ⌈7/2⌉ = 4
        assertEquals(4, ArmorClassTable.pieceAC(7, Slot.HELMET));
    }

    @Test
    void pieceAC_zeroBaseContributesNothing() {
        assertEquals(0, ArmorClassTable.pieceAC(0, Slot.CHESTPLATE));
        assertEquals(0, ArmorClassTable.pieceAC(-1, Slot.LEGGINGS));
    }

    // ---- Full-set totals (spec § 1.2) ----

    @Test
    void fullSetTotals_matchSpec() {
        assertEquals(7,  fullSet(ArmorClassTable.baseAC("leather")));
        assertEquals(11, fullSet(ArmorClassTable.baseAC("chainmail")));
        assertEquals(11, fullSet(ArmorClassTable.baseAC("golden")));
        assertEquals(13, fullSet(ArmorClassTable.baseAC("iron")));
        assertEquals(13, fullSet(ArmorClassTable.baseAC("copper")));
        assertEquals(19, fullSet(ArmorClassTable.baseAC("diamond")));
        assertEquals(23, fullSet(ArmorClassTable.baseAC("netherite")));
    }
}
