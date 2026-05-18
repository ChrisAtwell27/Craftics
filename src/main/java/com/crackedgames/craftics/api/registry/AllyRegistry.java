package com.crackedgames.craftics.api.registry;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of combat allies, keyed by {@link AllyEntry#entityTypeId()}.
 *
 * <p>{@code HubPetCollector} consults this registry to decide which hub mobs can
 * be recruited into combat and how. Craftics registers its built-in pets via
 * {@code VanillaAllies}; addons register their own through
 * {@code CrafticsAPI.registerAlly}.
 *
 * @since 0.2.0
 */
public final class AllyRegistry {

    private static final Map<String, AllyEntry> REGISTRY = new ConcurrentHashMap<>();

    private AllyRegistry() {}

    /** Register an ally definition, replacing any existing entry for the same entity type. */
    public static void register(AllyEntry entry) {
        REGISTRY.put(entry.entityTypeId(), entry);
    }

    /** The entry for {@code entityTypeId}, or {@code null} if that mob is not a registered ally. */
    @Nullable
    public static AllyEntry getOrNull(String entityTypeId) {
        return REGISTRY.get(entityTypeId);
    }

    /** Whether {@code entityTypeId} is a registered combat ally. */
    public static boolean isRegistered(String entityTypeId) {
        return REGISTRY.containsKey(entityTypeId);
    }

    /**
     * Every registered ally definition, as an unmodifiable view. For a stable
     * snapshot, copy the result into a new collection.
     */
    public static Collection<AllyEntry> getAll() {
        return java.util.Collections.unmodifiableCollection(REGISTRY.values());
    }
}
