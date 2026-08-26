package com.crackedgames.craftics.client.compat;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;

/**
 * Client-side compatibility for EMI, one half of {@link RecipeViewer}.
 *
 * <p>EMI's item grid draws down the side of the inventory screen, straight through the Craftics
 * stat and damage-affinity panels, and neither knows the other is there. Rather than fight for the
 * space, only one is up at a time and a keybind decides which - see {@link RecipeViewerCompat} for
 * the mechanics, which JEI shares.
 *
 * <h2>Why the master switch and not an exclusion zone</h2>
 *
 * <p>EMI does offer exclusion areas, which would let both coexist by shrinking EMI's grid around
 * our panels. That is the friendlier answer when the overlap is a few pixels. Here it is not: the
 * Craftics panels occupy most of the right-hand column and a good part of the left, so an
 * exclusion big enough to clear them leaves EMI a strip too narrow to browse. Trading a full
 * screen of one for a full screen of the other is the honest version of this trade.
 *
 * <h2>Reaching the switch</h2>
 *
 * <p>{@code EmiConfig.enabled} is EMI's own master switch - the same field its visibility keybind
 * flips, read in every one of its render paths - so turning it off is exactly as complete as a
 * player turning EMI off themselves.
 *
 * <p>Deliberately in-memory only: {@code EmiConfig.writeConfig()} is never called, so a Craftics
 * session cannot leave EMI switched off in the player's config file after they stop playing with
 * this mod. The cost is that if something else makes EMI write its config mid-session, whatever
 * state we last set gets persisted with it.
 */
public final class EmiCompat implements RecipeViewer {

    public static final EmiCompat INSTANCE = new EmiCompat();

    private static final String CONFIG_CLASS = "dev.emi.emi.config.EmiConfig";

    private boolean loaded = false;
    private boolean resolved = false;
    /** {@code EmiConfig.enabled}: public static boolean, EMI's own master switch. */
    private Field enabledField;

    private EmiCompat() {}

    @Override public String modId() { return "emi"; }

    @Override public String displayName() { return "EMI"; }

    /** Flag mod presence. Does not touch EMI's classes - they may not be loaded yet. */
    void init() {
        loaded = FabricLoader.getInstance().isModLoaded(modId());
    }

    private synchronized void resolve() {
        if (resolved || !loaded) return;
        resolved = true;
        try {
            enabledField = Class.forName(CONFIG_CLASS).getField("enabled");
        } catch (Throwable t) {
            enabledField = null;
            CrafticsMod.LOGGER.warn(
                "[Craftics x EMI] could not reach EMI's master switch ({}). EMI will be left "
                + "alone, and the inventory panels may draw underneath it.", t.toString());
        }
    }

    @Override
    public boolean isPresent() {
        resolve();
        return loaded && enabledField != null;
    }

    /**
     * Whether EMI is currently showing.
     *
     * <p>Read live rather than mirrored in a field of our own, because EMI has its own visibility
     * keybind: a cached copy would go stale the moment a player used it, and the panels would draw
     * through EMI again with nothing to explain why.
     */
    @Override
    public boolean isEnabled() {
        if (!isPresent()) return false;
        try {
            return enabledField.getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean setEnabled(boolean enabled) {
        if (!isPresent()) return false;
        try {
            enabledField.setBoolean(null, enabled);
            return true;
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics x EMI] could not toggle EMI: {}", t.toString());
            return false;
        }
    }
}
