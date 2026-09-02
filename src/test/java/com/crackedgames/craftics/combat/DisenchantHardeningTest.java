package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guards that keep the disenchanter honest when the world moves underneath it.
 *
 * <p>The menu marks enchantments by position and is redrawn from the live item on every click, so
 * between opening a list and confirming it the item can change. The fingerprint is what turns that
 * from a silent removal of the wrong enchantment into a re-render, and it is the only thing
 * standing between a shifting inventory and destroyed work.
 */
class DisenchantHardeningTest {

    private static final String SWORD = "minecraft:diamond_sword";

    /** Build entries the way the real caller does, so entryKey is under test too. */
    private static List<String> runes(String... pathAndLevel) {
        List<String> out = new java.util.ArrayList<>();
        for (String e : pathAndLevel) {
            int colon = e.indexOf(':');
            out.add(DisenchantRules.entryKey(e.substring(0, colon),
                Integer.parseInt(e.substring(colon + 1))));
        }
        return out;
    }

    @Test
    @DisplayName("an entry key distinguishes the same enchantment at different levels")
    void theEntryKeyCarriesTheLevel() {
        // Dropping the level here would make a Sharpness IV -> V change invisible to the
        // fingerprint, and the player would confirm against a list they never saw.
        assertFalse(DisenchantRules.entryKey("sharpness", 4)
            .equals(DisenchantRules.entryKey("sharpness", 5)));
        assertFalse(DisenchantRules.entryKey("sharpness", 1)
            .equals(DisenchantRules.entryKey("smite", 1)));
        assertEquals(DisenchantRules.entryKey("mending", 1),
            DisenchantRules.entryKey("mending", 1));
    }

    @Test
    @DisplayName("an unchanged item still matches")
    void thePrintIsStableForAnUnchangedItem() {
        String before = DisenchantRules.fingerprint(SWORD, runes("mending:1", "sharpness:5"));
        String now = DisenchantRules.fingerprint(SWORD, runes("mending:1", "sharpness:5"));
        assertTrue(DisenchantRules.stillMatches(before, now));
    }

    @Test
    @DisplayName("gaining or losing an enchantment breaks the match")
    void aChangedRuneListIsCaught() {
        String before = DisenchantRules.fingerprint(SWORD, runes("mending:1", "sharpness:5"));

        assertFalse(DisenchantRules.stillMatches(before,
            DisenchantRules.fingerprint(SWORD, runes("sharpness:5"))),
            "losing one must not go unnoticed - every later position shifts");
        assertFalse(DisenchantRules.stillMatches(before,
            DisenchantRules.fingerprint(SWORD, runes("looting:3", "mending:1", "sharpness:5"))),
            "gaining one must not go unnoticed");
        assertFalse(DisenchantRules.stillMatches(before,
            DisenchantRules.fingerprint(SWORD, runes())),
            "a stripped item must not match");
    }

    @Test
    @DisplayName("a level change breaks the match")
    void aChangedLevelIsCaught() {
        // Same enchantments, different strength. The player ticked "Sharpness IV"; confirming
        // against a Sharpness V they never saw is not what they agreed to.
        String before = DisenchantRules.fingerprint(SWORD, runes("sharpness:4"));
        assertFalse(DisenchantRules.stillMatches(before,
            DisenchantRules.fingerprint(SWORD, runes("sharpness:5"))));
    }

    @Test
    @DisplayName("a different item in the same slot breaks the match")
    void aSwappedItemIsCaught() {
        // The reason the item id is in the fingerprint at all: two pieces can carry identical
        // enchantments, and a slot can change hands between clicks.
        String before = DisenchantRules.fingerprint(SWORD, runes("mending:1"));
        assertFalse(DisenchantRules.stillMatches(before,
            DisenchantRules.fingerprint("minecraft:netherite_sword", runes("mending:1"))));
    }

    @Test
    @DisplayName("a missing fingerprint never matches")
    void anAbsentPrintIsNotAMatch() {
        // A toggle arriving with no recorded print means no menu was opened - a stale packet, or
        // state cleared by a disconnect. It must fail closed, not sail through.
        assertFalse(DisenchantRules.stillMatches(null,
            DisenchantRules.fingerprint(SWORD, runes("mending:1"))));
        assertFalse(DisenchantRules.stillMatches(null, null));
    }

    @Test
    @DisplayName("the removed list is spelled out when short and counted when long")
    void theSummaryStaysReadable() {
        assertEquals("", DisenchantRules.removedSummary(List.of()));
        assertEquals("", DisenchantRules.removedSummary(null));

        String two = DisenchantRules.removedSummary(List.of("Mending", "Sharpness V"));
        assertTrue(two.contains("Mending") && two.contains("Sharpness V"));

        List<String> many = List.of("A", "B", "C", "D", "E", "F");
        String summary = DisenchantRules.removedSummary(many);
        assertTrue(summary.contains("and 2 more"), "should count the overflow: " + summary);
        assertFalse(summary.contains("F"), "should not spell out past the cap: " + summary);
    }

    @Test
    @DisplayName("exactly at the cap nothing is counted away")
    void theCapBoundaryIsExact() {
        List<String> four = List.of("A", "B", "C", "D");
        String summary = DisenchantRules.removedSummary(four);
        assertFalse(summary.contains("more"), "four names fit and must all be shown: " + summary);
        for (String n : four) assertTrue(summary.contains(n));
    }
}
