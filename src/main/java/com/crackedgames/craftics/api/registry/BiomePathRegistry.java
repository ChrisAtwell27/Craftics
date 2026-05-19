package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.BiomePathEntry;
import com.crackedgames.craftics.api.RegistrationSource;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named biome progression paths, keyed by {@link BiomePathEntry#id()}.
 *
 * <p>Craftics seeds its built-in Overworld / Nether / End paths at startup
 * ({@code VanillaBiomePaths}); addons register their own from code via
 * {@code CrafticsAPI.registerBiomePath} or from a {@code craftics/paths/} datapack.
 *
 * @since 0.2.0
 */
public final class BiomePathRegistry {

    private static final Map<String, BiomePathEntry> REGISTRY = new ConcurrentHashMap<>();
    /** Ids whose current entry came from a JSON datapack — dropped on {@code /reload}. */
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private BiomePathRegistry() {}

    /** Register a biome path from code (survives {@code /reload}). */
    public static void register(BiomePathEntry entry) {
        register(entry, RegistrationSource.CODE);
    }

    /** Register a biome path, tagging whether it came from code or a datapack. */
    public static void register(BiomePathEntry entry, RegistrationSource source) {
        REGISTRY.put(entry.id(), entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(entry.id());
        } else {
            DATAPACK_KEYS.remove(entry.id());
        }
    }

    /** The path for {@code id}, or {@code null} if none is registered. */
    @Nullable
    public static BiomePathEntry getOrNull(String id) {
        return REGISTRY.get(id);
    }

    /** Whether a path is registered under {@code id}. */
    public static boolean isRegistered(String id) {
        return REGISTRY.containsKey(id);
    }

    /** Every registered path, as an unmodifiable view. */
    public static Collection<BiomePathEntry> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** Remove every path that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
        }
        DATAPACK_KEYS.clear();
    }
}
