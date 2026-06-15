package com.crackedgames.craftics.combat.ai.ally;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of ally entity types that taunt - forcing enemies to target
 * them while alive. Mirrors {@link AllyArchetypes}/{@link AllyAbilities}: a compat
 * module registers a taunter type at mod init (e.g. the terracotta golem), and
 * {@code CombatManager} lazily flags any matching ally as taunting. With nothing
 * registered no ally taunts.
 *
 * @since 0.3.0
 */
public final class AllyTauntRegistry {

    private AllyTauntRegistry() {}

    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    /** Register an entity type as a taunter at runtime. */
    public static void register(String entityTypeId) {
        if (entityTypeId != null) REGISTERED.add(entityTypeId);
    }

    /** Whether this entity type is a registered taunter. */
    public static boolean isTaunter(String entityTypeId) {
        return entityTypeId != null && REGISTERED.contains(entityTypeId);
    }

    /** Test-only: clear all runtime registrations so tests don't leak state. Not for production use. */
    public static void clearRegisteredForTest() {
        REGISTERED.clear();
    }
}
