package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
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
    /** Entity types whose current entry came from a JSON datapack — dropped on /reload. */
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private AllyRegistry() {}

    /** Register an ally definition from code (survives {@code /reload}). */
    public static void register(AllyEntry entry) {
        register(entry, RegistrationSource.CODE);
    }

    /** Register an ally definition, tagging whether it came from code or a datapack. */
    public static void register(AllyEntry entry, RegistrationSource source) {
        REGISTRY.put(entry.entityTypeId(), entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(entry.entityTypeId());
        } else {
            DATAPACK_KEYS.remove(entry.entityTypeId());
        }
    }

    /** Remove every ally definition that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
        }
        DATAPACK_KEYS.clear();
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
