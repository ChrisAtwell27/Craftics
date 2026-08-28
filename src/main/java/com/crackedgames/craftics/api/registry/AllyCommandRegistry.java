package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.AllyCommandHandler;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Handlers for the click that follows an ally selection - the order itself.
 *
 * <p>Asked in registration order, before the AP check and before Craftics' own walk-or-strike;
 * the first to claim the click wins and Craftics does nothing further with it.
 *
 * @see AllyCommandHandler
 * @since 0.4.5
 */
public final class AllyCommandRegistry {

    private AllyCommandRegistry() {}

    private static final List<AllyCommandHandler> HANDLERS = new ArrayList<>();

    /** Register a handler. Registering the same instance twice is a no-op. */
    public static void register(AllyCommandHandler handler) {
        if (handler == null || HANDLERS.contains(handler)) return;
        HANDLERS.add(handler);
    }

    /** Whether anything is listening, so the command path can skip the loop. */
    public static boolean isEmpty() {
        return HANDLERS.isEmpty();
    }

    /**
     * Offer a command to the handlers.
     *
     * <p>A handler that throws loses the click and is skipped; the built-in command runs
     * instead. One broken addon should cost its own feature, not the ability to order a pet
     * around.
     *
     * @return true if a handler claimed it
     */
    public static boolean handle(ServerPlayerEntity player, CombatEntity ally, GridPos tile,
                                 CombatEntity target) {
        if (HANDLERS.isEmpty() || player == null || ally == null) return false;
        for (AllyCommandHandler handler : HANDLERS) {
            try {
                if (handler.onAllyCommand(player, ally, tile, target)) return true;
            } catch (Throwable t) {
                CrafticsMod.LOGGER.error("Ally command handler threw; the command falls through", t);
            }
        }
        return false;
    }

    /** Forget every handler. For tests. */
    public static void clear() {
        HANDLERS.clear();
    }
}
