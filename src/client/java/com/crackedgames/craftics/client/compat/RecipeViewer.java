package com.crackedgames.craftics.client.compat;

/**
 * A recipe-viewer mod whose item overlay competes with the Craftics inventory panels.
 *
 * <p>EMI and JEI solve the same problem the same way - a grid of every item down the side of the
 * inventory screen - and collide with the Craftics stat and damage-affinity panels in exactly the
 * same place. From this mod's point of view they differ only in which field to flip, so the
 * mechanics (hidden by default, one key swaps, never both on screen) live once above this
 * interface rather than being written out per mod.
 *
 * <p>Implementations reach their mod by reflection and no-op when it is absent, so Craftics builds
 * and runs with neither installed.
 */
public interface RecipeViewer {

    /** Fabric mod id, used only to ask whether it is installed. */
    String modId();

    /** What to call it when telling the player which one just took the screen. */
    String displayName();

    /** Installed AND reachable - a viewer whose API moved reports false and is left alone. */
    boolean isPresent();

    /** Whether its overlay is currently showing, read live from the mod itself. */
    boolean isEnabled();

    /**
     * Show or hide its overlay.
     *
     * @return true if the switch was actually thrown; false means the mod is absent or its API
     *         could not be reached, and the caller must not report a change that did not happen
     */
    boolean setEnabled(boolean enabled);
}
