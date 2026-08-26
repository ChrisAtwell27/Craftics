package com.crackedgames.craftics.client.compat;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Client-side compatibility for JEI, the other half of {@link RecipeViewer}.
 *
 * <p>JEI's ingredient list draws down the right of the inventory screen, straight through the
 * Craftics stat and damage-affinity panels - the same collision EMI has, for the same reason. The
 * handling is identical: hidden by default, one key swaps which of the two owns the space, and the
 * panels stand down whenever JEI is up.
 *
 * <h2>Reaching the switch</h2>
 *
 * <p>{@code Internal.getClientToggleState()} hands back the same object JEI's own overlay keybind
 * drives, so hiding the overlay from here is exactly as complete as a player pressing that key.
 *
 * <p>It exposes {@code toggleOverlayEnabled()} and no setter, so {@link #setEnabled} reads the
 * current state and toggles only when it differs. That makes the call idempotent, which the swap
 * logic relies on - it sets a state rather than requesting a flip.
 *
 * <p>The toggle writes an in-memory field and nothing else, so a Craftics session cannot leave
 * JEI's overlay switched off in the player's config after they stop playing with this mod. Same
 * property as the EMI side, arrived at the same way: by reading what the method actually does.
 */
public final class JeiCompat implements RecipeViewer {

    public static final JeiCompat INSTANCE = new JeiCompat();

    private static final String INTERNAL_CLASS = "mezz.jei.common.Internal";

    private boolean loaded = false;
    private boolean resolved = false;

    /** {@code Internal.getClientToggleState()} - the live object behind JEI's own overlay key. */
    private Method getToggleState;
    private Method isOverlayEnabled;
    private Method toggleOverlayEnabled;

    private JeiCompat() {}

    @Override public String modId() { return "jei"; }

    @Override public String displayName() { return "JEI"; }

    /** Flag mod presence. Does not touch JEI's classes - they may not be loaded yet. */
    void init() {
        loaded = FabricLoader.getInstance().isModLoaded(modId());
    }

    private synchronized void resolve() {
        if (resolved || !loaded) return;
        resolved = true;
        try {
            Class<?> internal = Class.forName(INTERNAL_CLASS);
            getToggleState = internal.getMethod("getClientToggleState");
            Class<?> toggleState = Class.forName("mezz.jei.common.config.IClientToggleState");
            isOverlayEnabled = toggleState.getMethod("isOverlayEnabled");
            toggleOverlayEnabled = toggleState.getMethod("toggleOverlayEnabled");
        } catch (Throwable t) {
            getToggleState = null;
            CrafticsMod.LOGGER.warn(
                "[Craftics x JEI] could not reach JEI's overlay switch ({}). JEI will be left "
                + "alone, and the inventory panels may draw underneath it.", t.toString());
        }
    }

    /**
     * The live toggle-state object, or null.
     *
     * <p>Fetched per call rather than cached: JEI replaces it across a resource reload, and a
     * stale reference would silently drive an object nothing is rendering from any more.
     */
    private Object toggleState() {
        resolve();
        if (getToggleState == null) return null;
        try {
            return getToggleState.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean isPresent() {
        return loaded && toggleState() != null;
    }

    @Override
    public boolean isEnabled() {
        Object state = toggleState();
        if (state == null) return false;
        try {
            return (boolean) isOverlayEnabled.invoke(state);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean setEnabled(boolean enabled) {
        Object state = toggleState();
        if (state == null) return false;
        try {
            if ((boolean) isOverlayEnabled.invoke(state) == enabled) return true; // already there
            toggleOverlayEnabled.invoke(state);
            return true;
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics x JEI] could not toggle the overlay: {}", t.toString());
            return false;
        }
    }
}
