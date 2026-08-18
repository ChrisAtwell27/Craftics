package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.CustomActionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Handlers for addon-defined enemy actions, keyed by the id carried on
 * {@code EnemyAction.CustomAction}.
 *
 * <p>See {@link CustomActionHandler} for why custom actions are one extra member of a
 * sealed set rather than an unsealed interface.
 *
 * @since 0.3.9
 */
public final class CustomActionRegistry {

    private CustomActionRegistry() {}

    private static final Map<String, CustomActionHandler> HANDLERS = new HashMap<>();

    /** Register a handler. Re-registering an id replaces it, which is how an addon
     *  overrides another addon's action or a built-in. */
    public static void register(String actionId, CustomActionHandler handler) {
        if (actionId == null || actionId.isBlank() || handler == null) return;
        HANDLERS.put(actionId, handler);
    }

    public static CustomActionHandler getOrNull(String actionId) {
        return actionId == null ? null : HANDLERS.get(actionId);
    }

    public static boolean isRegistered(String actionId) {
        return actionId != null && HANDLERS.containsKey(actionId);
    }

    /**
     * Run the handler for {@code actionId}.
     *
     * <p>An unregistered id is logged once and treated as a passed turn. That is the
     * right failure: an addon can be uninstalled while a save still holds an AI that
     * names its actions, and a missing handler should cost that enemy its turn rather
     * than wedge the whole fight waiting for an action that will never resolve.
     *
     * @return true if a handler ran
     */
    public static boolean resolve(String actionId, CustomActionHandler.Context ctx) {
        CustomActionHandler h = getOrNull(actionId);
        if (h == null) {
            if (WARNED.add(actionId == null ? "<null>" : actionId)) {
                CrafticsMod.LOGGER.warn("No handler registered for custom action '{}' - "
                    + "the enemy passes its turn. Is the addon that defines it still installed?",
                    actionId);
            }
            return false;
        }
        try {
            h.resolve(ctx);
        } catch (Throwable t) {
            CrafticsMod.LOGGER.error("Custom action '{}' threw; the enemy passes its turn",
                actionId, t);
            return false;
        }
        return true;
    }

    /** Ids already warned about, so a missing handler logs once instead of every turn. */
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    /** Clear every registration. Test hook. */
    public static void clear() {
        HANDLERS.clear();
        WARNED.clear();
    }
}
