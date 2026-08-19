package com.crackedgames.craftics.api.client;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The client half of the Craftics addon API: hooks that can only exist where there is a screen.
 *
 * <p>Separate from {@link com.crackedgames.craftics.api.CrafticsAPI} because it lives in the
 * client source set and touches rendering types a dedicated server never loads. Call it from
 * your own {@code ClientModInitializer} - there is no ordering hazard, since everything here is
 * a plain static registry.
 *
 * @since 0.4.0
 */
public final class CrafticsClientAPI {

    private CrafticsClientAPI() {}

    private static final List<CombatPortraitRenderer> PORTRAITS = new ArrayList<>();

    /**
     * A renderer that threw. Kept out of the loop rather than retried, because this runs once
     * per combatant per frame and a renderer that fails once fails sixty times a second.
     */
    private static final Map<CombatPortraitRenderer, Boolean> BROKEN = new IdentityHashMap<>();

    /**
     * Take over drawing combatant portraits in the combat HUD.
     *
     * <p>For mods where the entity type does not identify the creature - one type standing in
     * for many - so no registered head texture could be right. See
     * {@link CombatPortraitRenderer} for the contract and an example.
     *
     * <p>Applies everywhere Craftics draws a combatant icon: both rosters, the turn-order strip
     * and the hover inspect panel. Registering once covers all of them, which is the point: a
     * portrait in one panel and a blank square in the next looks more broken than blank squares
     * everywhere.
     *
     * @param renderer asked before Craftics' own head lookup; returns false to decline
     * @since 0.4.0
     */
    public static void registerPortraitRenderer(CombatPortraitRenderer renderer) {
        if (renderer == null || PORTRAITS.contains(renderer)) return;
        PORTRAITS.add(renderer);
    }

    /** Whether any renderer has been registered. Lets the HUD skip the loop entirely. */
    public static boolean hasPortraitRenderers() {
        return !PORTRAITS.isEmpty();
    }

    /**
     * Offer one combatant to the registered renderers.
     *
     * <p>Internal - called by the HUD immediately before its own head lookup.
     *
     * @return true if a renderer drew it, so the caller must not draw its own icon
     */
    public static boolean drawPortrait(DrawContext ctx, int entityId, String typeId,
                                       int x, int y, int size, float damageTint) {
        if (PORTRAITS.isEmpty()) return false;
        for (CombatPortraitRenderer renderer : PORTRAITS) {
            if (BROKEN.containsKey(renderer)) continue;
            try {
                if (renderer.drawPortrait(ctx, entityId, typeId, x, y, size, damageTint)) {
                    return true;
                }
            } catch (Throwable t) {
                // Reported once and then never asked again. Falling through to Craftics'
                // own icon costs the addon its portraits and keeps the fight playable,
                // which is the right way round - the HUD is not the place to die.
                BROKEN.put(renderer, Boolean.TRUE);
                CrafticsMod.LOGGER.error(
                    "Combat portrait renderer threw and has been disabled for this session", t);
            }
        }
        return false;
    }

    /** Forget every registered renderer. For tests. */
    public static void clearPortraitRenderers() {
        PORTRAITS.clear();
        BROKEN.clear();
    }
}
