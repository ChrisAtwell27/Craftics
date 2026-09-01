package com.crackedgames.craftics.combat;

/**
 * How many items to hand back after a Performative encore.
 *
 * <p>The encore is meant to be free: whatever the player is holding when it finishes should match
 * what they were holding before it started. Two things can vary independently - whether an item
 * had to be lent so the encore had something to spend, and whether the encore actually spent it,
 * which Special affinity's conserve roll can decide against.
 *
 * <p>The original code assumed the encore always spends, and handed an item over UP FRONT. When
 * conserve fired, that item was simply minted, which on a potion is a reliable duplication bug.
 *
 * <p>Free of Minecraft so the four cases can be checked directly. They are easy to get wrong by
 * reasoning and trivial to confirm by enumeration.
 */
public final class EncoreRefund {

    private EncoreRefund() {}

    /**
     * The change to apply to the player's stock after the encore.
     *
     * @param lentOne        an item was put in hand so the encore had something to spend
     * @param encoreSpentOne the encore actually consumed one
     * @return +1 to give one back, -1 to take one back, 0 to leave it alone
     */
    public static int adjustment(boolean lentOne, boolean encoreSpentOne) {
        if (encoreSpentOne && !lentOne) return 1;   // it spent the player's own; the echo is free
        if (lentOne && !encoreSpentOne) return -1;  // conserved, so the loan was never used
        return 0;                                   // lent and spent, or neither: already even
    }

    /**
     * What the player's stock changed by overall, which must always be 0.
     *
     * <p>Exists so the invariant can be asserted rather than described.
     */
    public static int netChange(boolean lentOne, boolean encoreSpentOne) {
        int lent = lentOne ? 1 : 0;
        int spent = encoreSpentOne ? 1 : 0;
        return lent - spent + adjustment(lentOne, encoreSpentOne);
    }
}
