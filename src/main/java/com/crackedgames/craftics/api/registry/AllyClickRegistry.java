package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.AllyClickHandler;
import com.crackedgames.craftics.combat.CombatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Handlers for clicking one of your own allies on the combat grid.
 *
 * <p>Asked in registration order; the first to claim the click wins and Craftics does nothing
 * further with it.
 *
 * @see AllyClickHandler
 * @since 0.4.0
 */
public final class AllyClickRegistry {

    private AllyClickRegistry() {}

    private static final List<AllyClickHandler> HANDLERS = new ArrayList<>();

    /** Register a handler. Registering the same instance twice is a no-op. */
    public static void register(AllyClickHandler handler) {
        if (handler == null || HANDLERS.contains(handler)) return;
        HANDLERS.add(handler);
    }

    /** Whether anything is listening, so the combat path can skip the loop. */
    public static boolean isEmpty() {
        return HANDLERS.isEmpty();
    }

    /**
     * Offer a click to the handlers.
     *
     * <p>A handler that throws loses the click and is skipped; the built-in behaviour runs
     * instead. One broken addon should cost its own feature, not the ability to heal a pet.
     *
     * @return true if a handler claimed it
     */
    public static boolean handle(ServerPlayerEntity player, CombatEntity ally, ItemStack held) {
        if (HANDLERS.isEmpty() || player == null || ally == null) return false;
        for (AllyClickHandler handler : HANDLERS) {
            try {
                if (handler.onAllyClicked(player, ally, held)) return true;
            } catch (Throwable t) {
                CrafticsMod.LOGGER.error("Ally click handler threw; the click falls through", t);
            }
        }
        return false;
    }

    /** Forget every handler. For tests. */
    public static void clear() {
        HANDLERS.clear();
    }
}
