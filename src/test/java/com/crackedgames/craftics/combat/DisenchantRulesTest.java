package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The disenchanter's selection maths.
 *
 * <p>This is the one event that destroys something the player cannot get back, and it addresses
 * enchantments by POSITION in a list that gets rebuilt on every click. An off-by-one here does not
 * throw, does not log, and does not look like a bug: the player ticks Mending, loses Unbreaking,
 * and concludes the event is buggy or that they misclicked.
 */
class DisenchantRulesTest {

    private static final List<String> RUNES =
        List.of("Efficiency V", "Fortune III", "Mending", "Unbreaking III");

    private static Set<Integer> setOf(int... values) {
        Set<Integer> out = new LinkedHashSet<>();
        for (int v : values) out.add(v);
        return out;
    }

    @Test
    @DisplayName("removed and kept always partition the list exactly")
    void thePartitionIsTotalAndDisjoint() {
        // Every possible selection over a 4-entry list. The invariant is that nothing is lost and
        // nothing is duplicated, whichever combination the player ticks.
        for (int mask = 0; mask < (1 << RUNES.size()); mask++) {
            Set<Integer> picked = new LinkedHashSet<>();
            for (int i = 0; i < RUNES.size(); i++) {
                if ((mask & (1 << i)) != 0) picked.add(i);
            }
            List<String> removed = DisenchantRules.removed(RUNES, picked);
            List<String> kept = DisenchantRules.kept(RUNES, picked);

            assertEquals(RUNES.size(), removed.size() + kept.size(),
                "mask " + mask + " lost or duplicated an entry");

            List<String> rejoined = new ArrayList<>(removed);
            rejoined.addAll(kept);
            assertTrue(rejoined.containsAll(RUNES), "mask " + mask + " dropped an enchantment");
            for (String r : removed) {
                assertFalse(kept.contains(r), "mask " + mask + " both removed and kept " + r);
            }
        }
    }

    @Test
    @DisplayName("ticking a position removes exactly that enchantment")
    void thePositionsLineUp() {
        // The bug this exists to prevent, stated plainly: tick index 2, lose index 2.
        assertEquals(List.of("Mending"), DisenchantRules.removed(RUNES, setOf(2)));
        assertEquals(List.of("Efficiency V", "Fortune III", "Unbreaking III"),
            DisenchantRules.kept(RUNES, setOf(2)));

        assertEquals(List.of("Efficiency V", "Unbreaking III"),
            DisenchantRules.removed(RUNES, setOf(0, 3)));
    }

    @Test
    @DisplayName("a toggle goes on and back off")
    void togglingIsReversible() {
        Set<Integer> none = setOf();
        Set<Integer> one = DisenchantRules.toggle(none, 1, RUNES.size());
        assertEquals(setOf(1), one);
        assertEquals(setOf(), DisenchantRules.toggle(one, 1, RUNES.size()));
        // The original is never modified - the menu re-reads it on the next render.
        assertEquals(setOf(), none);
    }

    @Test
    @DisplayName("a position the item does not have is ignored")
    void outOfRangeTogglesAreRefused() {
        // The index arrives as text from the client, so a stale menu or a hand-sent packet can
        // name a position that does not exist. It must not select anything and must not throw.
        for (int bad : new int[]{-1, RUNES.size(), RUNES.size() + 50, Integer.MAX_VALUE}) {
            assertEquals(setOf(), DisenchantRules.toggle(setOf(), bad, RUNES.size()),
                "index " + bad + " should be ignored");
        }
        // And it must not disturb a selection already made.
        assertEquals(setOf(2), DisenchantRules.toggle(setOf(2), 99, RUNES.size()));
    }

    @Test
    @DisplayName("confirming needs at least one mark")
    void anEmptyConfirmDoesNothing() {
        assertFalse(DisenchantRules.canConfirm(setOf()));
        assertFalse(DisenchantRules.canConfirm(null));
        assertTrue(DisenchantRules.canConfirm(setOf(0)));
    }

    @Test
    @DisplayName("stripping every rune is recognised as stripping every rune")
    void aFullStripIsFlagged() {
        assertTrue(DisenchantRules.stripsEverything(RUNES, setOf(0, 1, 2, 3)));
        assertFalse(DisenchantRules.stripsEverything(RUNES, setOf(0, 1, 2)));
        assertFalse(DisenchantRules.stripsEverything(RUNES, setOf()));
        // An item with nothing on it is not "stripped bare" - it was never enchanted.
        assertFalse(DisenchantRules.stripsEverything(List.of(), setOf()));
    }

    @Test
    @DisplayName("the confirm button always says how many will go")
    void theConfirmLabelNamesTheCount() {
        assertTrue(DisenchantRules.confirmLabel(0).toLowerCase().contains("select"));
        assertTrue(DisenchantRules.confirmLabel(1).contains("1 enchantment"));
        assertTrue(DisenchantRules.confirmLabel(3).contains("3 enchantments"));
        // Nobody should be able to confirm without seeing a number.
        for (int n = 1; n <= 8; n++) {
            assertTrue(DisenchantRules.confirmLabel(n).contains(String.valueOf(n)),
                "confirm label for " + n + " does not name the count");
        }
    }

    @Test
    @DisplayName("a marked row reads differently from an unmarked one")
    void theRowsAreDistinguishable() {
        String on = DisenchantRules.rowLabel("Mending", true);
        String off = DisenchantRules.rowLabel("Mending", false);
        assertFalse(on.equals(off), "a ticked row must not look identical to an unticked one");
        assertTrue(on.contains("Mending") && off.contains("Mending"));
    }

    @Test
    @DisplayName("a null selection keeps everything")
    void nullSelectionIsSafe() {
        // Reachable: a player who opens an item and backs out without ticking anything.
        assertEquals(RUNES, DisenchantRules.kept(RUNES, null));
        assertEquals(List.of(), DisenchantRules.removed(RUNES, null));
    }
}
