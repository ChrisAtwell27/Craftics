package com.crackedgames.craftics.combat.ai.ally;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of per-type {@link AllyDeathHook}s for modded allies. Mirrors
 * {@link AllyArchetypes}/{@link AllyAbilities}: a compat module registers a hook
 * for an entity type at mod init, and {@code CombatManager} dispatches it when a
 * living ally of that type dies. With nothing registered the core does nothing
 * on-death beyond its vanilla pipeline.
 *
 * @since 0.3.0
 */
public final class AllyDeathRegistry {

    private AllyDeathRegistry() {}

    private static final ConcurrentHashMap<String, AllyDeathHook> REGISTERED = new ConcurrentHashMap<>();

    /** Register an on-death hook for an entity type at runtime. Last registration for an id wins. */
    public static void register(String entityTypeId, AllyDeathHook hook) {
        if (entityTypeId != null && hook != null) REGISTERED.put(entityTypeId, hook);
    }

    /** The death hook for a given ally type, or {@code null} if none registered. */
    public static AllyDeathHook hookFor(String entityTypeId) {
        if (entityTypeId == null) return null;
        return REGISTERED.get(entityTypeId);
    }

    /** Test-only: clear all runtime registrations so tests don't leak state. Not for production use. */
    public static void clearRegisteredForTest() {
        REGISTERED.clear();
    }
}
