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
}
