package com.crackedgames.craftics.combat.ai.ally;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of per-type {@link AllyKillHook}s for modded allies. Mirrors
 * {@link AllyArchetypes}/{@link AllyAbilities}: a compat module registers a hook
 * keyed by the killer ally's entity type at mod init, and {@code CombatManager}
 * dispatches it when an ally of that type lands a kill. With nothing registered
 * the core does nothing special on an ally kill.
 *
 * @since 0.3.0
 */
public final class AllyKillRegistry {

    private AllyKillRegistry() {}

    private static final ConcurrentHashMap<String, AllyKillHook> REGISTERED = new ConcurrentHashMap<>();

    /** Register an on-kill hook keyed by the killer ally's entity type. Last registration for an id wins. */
    public static void register(String killerEntityTypeId, AllyKillHook hook) {
        if (killerEntityTypeId != null && hook != null) REGISTERED.put(killerEntityTypeId, hook);
    }

    /** The kill hook for a given killer ally type, or {@code null} if none registered. */
    public static AllyKillHook hookFor(String killerEntityTypeId) {
        if (killerEntityTypeId == null) return null;
        return REGISTERED.get(killerEntityTypeId);
    }

    /** Test-only: clear all runtime registrations so tests don't leak state. Not for production use. */
    public static void clearRegisteredForTest() {
        REGISTERED.clear();
    }
}
