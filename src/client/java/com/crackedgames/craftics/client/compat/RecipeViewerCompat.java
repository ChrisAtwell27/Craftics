package com.crackedgames.craftics.client.compat;

import com.crackedgames.craftics.CrafticsMod;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Whichever recipe viewers are installed, treated as one switch.
 *
 * <p>EMI and JEI both draw an item grid down the side of the inventory screen and both collide
 * with the Craftics stat and damage-affinity panels. The rest of the mod does not need to know
 * which is present, or that both can be at once, so everything above this point asks one question:
 * is a viewer on screen, and put it away.
 *
 * <h2>Both installed</h2>
 *
 * <p>Perfectly possible, and handled deliberately rather than by luck. {@link #isEnabled} is true
 * when ANY viewer is showing, so the panels stand down for either of them; {@link #setEnabled}
 * addresses ALL of them, so hiding does not leave a second grid behind the first. Getting that
 * backwards - asking only the first viewer, or setting only the first - would produce exactly the
 * overlap this exists to prevent, with no obvious cause.
 */
public final class RecipeViewerCompat {

    /** Every viewer Craftics knows how to drive, installed or not. */
    private static final List<RecipeViewer> KNOWN =
        List.of(EmiCompat.INSTANCE, JeiCompat.INSTANCE);

    private RecipeViewerCompat() {}

    /**
     * Flag which viewers are installed and put them away.
     *
     * <p>They start hidden so a new player meets the Craftics panels first; the swap key hands the
     * space over when they want it.
     */
    public static void init() {
        EmiCompat.INSTANCE.init();
        JeiCompat.INSTANCE.init();

        List<String> found = new ArrayList<>();
        for (RecipeViewer viewer : KNOWN) {
            if (!viewer.isPresent()) continue;
            found.add(viewer.displayName());
            viewer.setEnabled(false);
        }
        if (!found.isEmpty()) {
            CrafticsMod.LOGGER.info(
                "[Craftics x recipe viewers] {} detected and started hidden; the toggle key swaps "
                + "with the inventory stat panels", String.join(" + ", found));
        }
    }

    /** Every installed, reachable viewer. */
    private static List<RecipeViewer> present() {
        return KNOWN.stream().filter(RecipeViewer::isPresent).collect(Collectors.toList());
    }

    /** Whether any recipe viewer is installed at all. */
    public static boolean isPresent() {
        return !present().isEmpty();
    }

    /** What to call the viewer(s) when telling the player what just happened. */
    public static String displayName() {
        List<String> names = present().stream()
            .map(RecipeViewer::displayName).collect(Collectors.toList());
        return names.isEmpty() ? "Recipe viewer" : String.join(" + ", names);
    }

    /**
     * Whether ANY viewer is currently showing.
     *
     * <p>Read live from the mods themselves rather than mirrored here, because both have their own
     * visibility keybinds: a cached copy goes stale the moment a player uses one, and the panels
     * would draw through the grid again with nothing to explain why.
     */
    public static boolean isEnabled() {
        for (RecipeViewer viewer : present()) {
            if (viewer.isEnabled()) return true;
        }
        return false;
    }

    /**
     * Show or hide every installed viewer.
     *
     * @return true if at least one actually moved
     */
    public static boolean setEnabled(boolean enabled) {
        boolean any = false;
        for (RecipeViewer viewer : present()) {
            any |= viewer.setEnabled(enabled);
        }
        return any;
    }
}
