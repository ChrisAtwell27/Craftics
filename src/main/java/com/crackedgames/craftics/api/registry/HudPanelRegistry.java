package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.HudPanel;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which combat HUD panels are currently drawn.
 *
 * <p>Two independent things can turn a panel off, and either is enough:
 *
 * <ul>
 *   <li><b>An addon</b>, through {@link com.crackedgames.craftics.api.CrafticsAPI#hideHudPanel},
 *       because it draws its own version of that information.</li>
 *   <li><b>The player</b>, through the config screen.</li>
 * </ul>
 *
 * <p>Neither overrules the other, and that is deliberate: an addon hiding a panel it has
 * replaced should not be undone by a player who never turned that panel off, and a player who
 * has turned one off should not have it forced back by an addon.
 *
 * <p>State is per-process and not persisted. An addon declares what it hides on every start,
 * from its own initializer, the same way it registers everything else.
 *
 * @since 0.4.0
 */
public final class HudPanelRegistry {

    private HudPanelRegistry() {}

    private static final Set<HudPanel> HIDDEN = EnumSet.noneOf(HudPanel.class);

    /** Hide a panel. Idempotent. */
    public static void hide(HudPanel panel) {
        if (panel != null) HIDDEN.add(panel);
    }

    /** Show a panel an addon had hidden. Does not override the player's own config. */
    public static void show(HudPanel panel) {
        if (panel != null) HIDDEN.remove(panel);
    }

    /** Whether an addon has asked for this panel to be hidden. */
    public static boolean isHiddenByAddon(HudPanel panel) {
        return panel != null && HIDDEN.contains(panel);
    }

    /**
     * Whether the panel should be drawn, taking both the addon request and the player's
     * config into account.
     *
     * <p>Called once per panel per frame, so it stays a set lookup and a config read.
     */
    public static boolean isVisible(HudPanel panel) {
        if (panel == null) return true;
        if (HIDDEN.contains(panel)) return false;
        return allowedByConfig(panel);
    }

    private static boolean allowedByConfig(HudPanel panel) {
        var config = com.crackedgames.craftics.CrafticsMod.CONFIG;
        // Read defensively: the HUD can render before the config finishes loading during a
        // resource reload, and a panel vanishing for a frame reads as a flicker bug.
        if (config == null) return true;
        try {
            return switch (panel) {
                case ALLY_ROSTER -> config.showAllyPanel();
                case ENEMY_ROSTER -> config.showEnemyPanel();
                // No player-facing toggle: these are the panels that carry numbers with no
                // other home, so only an addon that has genuinely replaced them may hide them.
                case TURN_ORDER, PLAYER_STATUS -> true;
            };
        } catch (Exception notLoadedYet) {
            return true;
        }
    }

    /** Forget every addon suppression. For tests. */
    public static void clear() {
        HIDDEN.clear();
    }
}
