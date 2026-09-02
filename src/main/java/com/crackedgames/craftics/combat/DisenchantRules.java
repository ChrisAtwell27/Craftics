package com.crackedgames.craftics.combat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which enchantments a disenchant is about to strip, and which survive it.
 *
 * <p>The player picks enchantments off a list by position, so every rule here is about POSITION
 * staying honest. The menu is rebuilt from the item on each click - one render to show the list,
 * another after each toggle, a third at the confirm - and if the order shifted between any two of
 * them, the player would tick Mending and lose Unbreaking. The caller is responsible for handing
 * over a list in a stable order; this class is responsible for never losing or duplicating an
 * entry once it has one.
 *
 * <p>Free of Minecraft so that partition can be checked exhaustively. Removing the wrong
 * enchantment destroys work the player cannot get back, and it would look like a bad roll rather
 * than a bug.
 */
public final class DisenchantRules {

    private DisenchantRules() {}

    /** Most enchantments a single item's menu will list, so the choice rows stay on screen. */
    public static final int MAX_LISTED = 8;

    /**
     * Flip one entry's selected state.
     *
     * <p>Out-of-range indices are ignored rather than throwing. The index arrives in a string
     * from the client, so a stale menu - the item changed, the fight moved on - or a hand-sent
     * packet can name a position that no longer exists, and neither should crash the event.
     *
     * @param selected currently ticked positions, never modified
     * @param index    the position clicked
     * @param count    how many enchantments the item actually has
     * @return a new set with {@code index} flipped, or an unchanged copy when it is out of range
     */
    public static Set<Integer> toggle(Set<Integer> selected, int index, int count) {
        Set<Integer> out = new LinkedHashSet<>(selected == null ? Set.of() : selected);
        if (index < 0 || index >= count) return out;
        if (!out.remove(index)) out.add(index);
        return out;
    }

    /** Nothing ticked means nothing to confirm - the button is a no-op the player can still see. */
    public static boolean canConfirm(Set<Integer> selected) {
        return selected != null && !selected.isEmpty();
    }

    /** The entries that will be stripped, in list order. */
    public static <T> List<T> removed(List<T> all, Set<Integer> selected) {
        List<T> out = new ArrayList<>();
        if (all == null) return out;
        for (int i = 0; i < all.size(); i++) {
            if (selected != null && selected.contains(i)) out.add(all.get(i));
        }
        return out;
    }

    /** The entries that survive, in list order. */
    public static <T> List<T> kept(List<T> all, Set<Integer> selected) {
        List<T> out = new ArrayList<>();
        if (all == null) return out;
        for (int i = 0; i < all.size(); i++) {
            if (selected == null || !selected.contains(i)) out.add(all.get(i));
        }
        return out;
    }

    /** Whether the confirm would strip the item completely bare. */
    public static boolean stripsEverything(List<?> all, Set<Integer> selected) {
        return all != null && !all.isEmpty() && kept(all, selected).isEmpty();
    }

    /** The confirm button's text, which has to name the count so nobody confirms blind. */
    public static String confirmLabel(int selectedCount) {
        if (selectedCount <= 0) return "\u00a78Select one to remove";
        return selectedCount == 1
            ? "\u00a7c\u2716 Remove 1 enchantment"
            : "\u00a7c\u2716 Remove " + selectedCount + " enchantments";
    }

    /**
     * One enchantment as it appears in a {@link #fingerprint}.
     *
     * <p>Level is part of it: an item whose Sharpness went from III to IV between two renders is
     * a different list to tick against, even though the same enchantments are on it.
     */
    public static String entryKey(String path, int level) {
        return path + ":" + level;
    }

    /**
     * A fingerprint of exactly what a menu was drawn from.
     *
     * <p>The disenchanter marks enchantments BY POSITION, and the menu is rebuilt from the live
     * item on every click. Between opening the list and confirming it, the item can change - a
     * teammate's effect, a swapped slot, an enchantment gained or lost - and then position 2 is a
     * different enchantment than the one the player ticked. Comparing fingerprints turns that from
     * a silent wrong removal into a re-render.
     *
     * <p>The item id is included so a slot that now holds a DIFFERENT item with coincidentally
     * identical enchantments is still caught.
     */
    public static String fingerprint(String itemId, List<String> entryKeys) {
        return (itemId == null ? "?" : itemId) + "|"
            + String.join(",", entryKeys == null ? List.of() : entryKeys);
    }

    /** Whether a menu drawn from {@code before} is still safe to act on now. */
    public static boolean stillMatches(String before, String now) {
        return before != null && before.equals(now);
    }

    /** Most names the result line spells out before it starts counting instead. */
    public static final int MAX_NAMED = 4;

    /**
     * The result line's list of what came off.
     *
     * <p>Capped because a dialogue line does not wrap: eight full enchantment names run off the
     * side of the box, and the player would be told what they lost by a sentence they cannot read.
     */
    public static String removedSummary(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        String sep = "§7, §f";
        if (names.size() <= MAX_NAMED) return String.join(sep, names);
        List<String> head = new ArrayList<>(names.subList(0, MAX_NAMED));
        return String.join(sep, head) + "§7 and " + (names.size() - MAX_NAMED) + " more";
    }

    /** One row's text: ticked or not, then the enchantment. */
    public static String rowLabel(String enchantName, boolean ticked) {
        return ticked ? "\u00a7c\u2714 " + enchantName : "\u00a77\u2610 " + enchantName;
    }
}
