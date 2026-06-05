package com.crackedgames.craftics.combat.ai.ally;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of ally entity types that can be ignited into a lit one-shot
 * state, with the per-type {@link IgnitableAlly} stat transform. Mirrors
 * {@link AllyArchetypes}/{@link AllyAbilities}: a compat module registers an
 * ignitable type at mod init (e.g. the coal golem), and {@code CombatManager} /
 * {@code ItemUseHandler} consult it instead of any mod-specific class. With
 * nothing registered no ally is ignitable.
 *
 * @since 0.3.0
 */
public final class IgnitableAllyRegistry {

    private IgnitableAllyRegistry() {}

    private static final ConcurrentHashMap<String, IgnitableAlly> REGISTERED = new ConcurrentHashMap<>();

    /** Register an ignitable ally type with its lit stat transform. Last registration for an id wins. */
    public static void register(String entityTypeId, IgnitableAlly transform) {
        if (entityTypeId != null && transform != null) REGISTERED.put(entityTypeId, transform);
    }

    /** The lit stat transform for an ally type, or {@code null} if it is not ignitable. */
    public static IgnitableAlly transformFor(String entityTypeId) {
        if (entityTypeId == null) return null;
        return REGISTERED.get(entityTypeId);
    }

    /** Whether this ally type can be ignited. */
    public static boolean isIgnitable(String entityTypeId) {
        return entityTypeId != null && REGISTERED.containsKey(entityTypeId);
    }

    /** Test-only: clear all runtime registrations so tests don't leak state. Not for production use. */
    public static void clearRegisteredForTest() {
        REGISTERED.clear();
    }
}
