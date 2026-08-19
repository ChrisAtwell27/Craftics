package com.crackedgames.craftics.api;

/**
 * A panel of Craftics' combat HUD that can be turned off.
 *
 * <p>Exists for addons that draw their own version of the same information. A compat mod whose
 * party screen already lists your creatures does not want Craftics' ally roster underneath it -
 * that is two lists of the same thing fighting for the same corner of the screen, and the one
 * the player is reading is not Craftics'.
 *
 * <p>Named panels rather than a single "hide the ally list" switch, because the redundancy
 * argument is never about one panel for long: a mod that replaces the party list usually
 * replaces the opposing side's list too.
 *
 * <p>Suppression is <b>visual only</b>. Nothing about the fight changes, and Craftics still
 * tracks and syncs everything the panel would have shown - so a panel can be turned back on
 * mid-fight and immediately shows the truth.
 *
 * @see com.crackedgames.craftics.api.CrafticsAPI#hideHudPanel
 * @since 0.4.0
 */
public enum HudPanel {

    /** Top-left roster of the player's allies: portrait, name and HP bar each. */
    ALLY_ROSTER,

    /** Top-right roster of the enemies still standing. */
    ENEMY_ROSTER,

    /** Top-center strip showing whose turn it is and what order the rest act in. */
    TURN_ORDER,

    /**
     * Top-left panel holding the player's own HP, AP and movement.
     *
     * <p>Hide this one only if you are genuinely replacing it. Unlike the rosters it is the
     * only place several numbers appear at all, and a player with no AP readout cannot tell
     * why an action is being refused.
     */
    PLAYER_STATUS
}
