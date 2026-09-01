package com.crackedgames.craftics.combat;

/**
 * What the enchanter is allowed to offer for an item it has already seen the contents of.
 *
 * <p>The rule is that an enhancement has to be an improvement. Re-rolling Sharpness III onto a
 * sword that already has Sharpness III is not a bad outcome the player gambled on, it is nothing
 * at all, and it reads as the event having quietly failed. Worse, a lower roll on the same
 * enchantment looks like a downgrade even though the component keeps the higher level.
 *
 * <p>So an enchantment the item already carries stays on the table only while there is headroom
 * above it, and when it comes up the level is forced past what is already there.
 *
 * <p>Free of Minecraft types so it can be tested. The caller supplies the two numbers - what the
 * item has now, and how high that enchantment goes - because only it can read a registry.
 */
public final class EnchanterOffer {

    private EnchanterOffer() {}

    /**
     * Whether this enchantment can still do something for the item.
     *
     * @param existingLevel what the item already has, 0 for an enchantment it does not carry
     * @param maxLevel      the enchantment's own maximum, or 0 when it could not be resolved
     * @return false for an enchantment already at its ceiling, and false for one whose ceiling is
     *         unknown - if the registry cannot find it, the apply path cannot add it either, so
     *         offering it would put an outcome on the shortlist that can never happen
     */
    public static boolean canImprove(int existingLevel, int maxLevel) {
        if (maxLevel <= 0) return false;
        return existingLevel < maxLevel;
    }

    /**
     * The level to actually apply, given what the roll wanted.
     *
     * <p>Always strictly above {@code existingLevel}, so an enchantment the item already has can
     * only ever go up, and never above {@code maxLevel}, so the enchanter cannot mint a Mending II
     * that no other source in the game would produce.
     *
     * @param existingLevel what the item already has, 0 for a fresh enchantment
     * @param rolledLevel   the level the roll came up with
     * @param maxLevel      the enchantment's own maximum
     */
    public static int improvedLevel(int existingLevel, int rolledLevel, int maxLevel) {
        int level = Math.max(rolledLevel, existingLevel + 1);
        if (maxLevel > 0) level = Math.min(level, maxLevel);
        return Math.max(1, level);
    }

    /**
     * Enchantments that make the weapon carrying them worse.
     *
     * <p>Hilt quarters your damage and Dull halves it. They are meant to exist - the enchanter is
     * a gamble, and a gamble with no losing face is just a reward - but they sat in the pool at
     * exactly the same odds as Sharpness, which on a sword meant roughly one roll in ten turned a
     * good weapon into a worse one. That is too often for something the player cannot undo.
     *
     * <p>Matched on the bare path, the same string the pools and the apply path use.
     */
    public static final java.util.Set<String> DOWNSIDE_ENCHANTS = java.util.Set.of("hilt", "dull");

    /** What a downside enchantment weighs against {@link #STANDARD_WEIGHT}. */
    public static final int DOWNSIDE_WEIGHT = 1;

    /** What everything else weighs. */
    public static final int STANDARD_WEIGHT = 6;

    /** How likely this enchantment is to be the one that lands, relative to the rest of the pool. */
    public static int weightOf(String key) {
        return DOWNSIDE_ENCHANTS.contains(key) ? DOWNSIDE_WEIGHT : STANDARD_WEIGHT;
    }

    /** The sum every roll is drawn against. Never 0 for a non-empty pool, so the caller can divide. */
    public static int totalWeight(java.util.List<String> keys) {
        int total = 0;
        for (String key : keys) total += weightOf(key);
        return total;
    }

    /**
     * The enchantment a roll lands on, weighted so the downside pair comes up a sixth as often.
     *
     * <p>This deliberately does NOT change what the shortlist can show. Hilt and Dull stay just as
     * visible as possibilities the item might take, because the shortlist is the warning, and a
     * warning about something that never happens stops being read. Only the odds of actually
     * getting one move.
     *
     * @param keys the eligible pool, never empty
     * @param roll 0 to {@code totalWeight(keys) - 1}
     */
    public static String weightedPick(java.util.List<String> keys, int roll) {
        if (keys.isEmpty()) throw new IllegalArgumentException("no eligible enchantments to pick from");
        // A roll outside the range is a caller bug, but returning something valid beats throwing
        // in the middle of an event the player is already looking at.
        int cursor = Math.max(0, roll);
        for (String key : keys) {
            cursor -= weightOf(key);
            if (cursor < 0) return key;
        }
        return keys.get(keys.size() - 1);
    }
}
