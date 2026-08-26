package com.crackedgames.craftics.compat;

/**
 * Who gets the space either side of the inventory screen: a recipe viewer, or the Craftics stat
 * panels.
 *
 * <p>A recipe viewer (EMI or JEI) and the panels both draw there, neither knowing the other
 * exists, so the item grid runs straight through the stats and damage-affinity panels. Exactly one
 * of them is up at a time.
 *
 * <p>The rule lives here, away from Minecraft, for one reason: the client source set has no test
 * harness, and "these two can never both be on" is precisely the kind of invariant that is easy to
 * state, easy to believe, and easy to get wrong on some path nobody walked. Here it can be checked
 * exhaustively.
 */
public final class PanelExclusion {

    private PanelExclusion() {}

    /**
     * Whether the Craftics panels may draw right now.
     *
     * <p>{@code viewerEnabled} is read live from the viewer rather than from anything Craftics
     * remembers. Even with Craftics driving the switch, the viewer can change it underneath us:
     * EMI reloads its config whenever its settings screen is opened, and that resets the flag from
     * disk. The panels therefore ask at the moment they draw instead of trusting a remembered
     * answer.
     *
     * @param statsRequested whether the player has asked for the panels
     * @param viewerEnabled  whether a recipe viewer is showing, as the viewer itself reports it
     */
    public static boolean statsVisible(boolean statsRequested, boolean viewerEnabled) {
        return statsRequested && !viewerEnabled;
    }

    /**
     * What the player wants, and what a recipe viewer is showing.
     *
     * @param statsRequested the panels have been asked for
     * @param viewerEnabled  a recipe viewer is showing
     */
    public record State(boolean statsRequested, boolean viewerEnabled) {}

    /**
     * The state after the toggle key.
     *
     * <p>With a recipe viewer installed this is a straight SWAP: turning the panels off hands the
     * space to the viewer, and turning them back on puts the viewer away. The two share one screen
     * region, so "off" for one is the same event as "on" for the other, and making the player
     * press a second key to fill the space they just emptied is busywork.
     *
     * <p>With no viewer installed there is nothing to swap with, so the key does what it always
     * did: flips the panels, and leaves the space empty while they are down.
     *
     * @param viewerPresent whether any recipe viewer is installed and reachable
     */
    public static State toggle(State current, boolean viewerPresent) {
        boolean wantStats = !statsVisible(current.statsRequested(), current.viewerEnabled());
        if (!viewerPresent) return new State(wantStats, false);
        return new State(wantStats, !wantStats);
    }
}
