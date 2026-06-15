package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of reusable enemy templates, keyed by {@link EnemyEntry#id()}.
 *
 * <p>Biome JSON references an entry by id ({@code "enemy": "<id>"}); the biome
 * loader resolves it to the entry's stats, appearance, and AI. Addons register
 * entries from code via {@code CrafticsAPI.registerEnemy}; datapacks register
 * them through {@code EnemyJsonLoader}.
 *
 * @since 0.2.0
 */
public final class EnemyRegistry {

    private static final Map<String, EnemyEntry> REGISTRY = new ConcurrentHashMap<>();
    /** Ids whose current entry came from a JSON datapack - dropped on {@code /reload}. */
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private EnemyRegistry() {}

    /** Register an enemy template from code (survives {@code /reload}). */
    public static void register(EnemyEntry entry) {
        register(entry, RegistrationSource.CODE);
    }

    /** Register an enemy template, tagging whether it came from code or a datapack. */
    public static void register(EnemyEntry entry, RegistrationSource source) {
        REGISTRY.put(entry.id(), entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(entry.id());
        } else {
            DATAPACK_KEYS.remove(entry.id());
        }
    }

    /** The entry for {@code id}, or {@code null} if no enemy is registered under it. */
    @Nullable
    public static EnemyEntry getOrNull(String id) {
        return REGISTRY.get(id);
    }

    /** Whether an enemy template is registered under {@code id}. */
    public static boolean isRegistered(String id) {
        return REGISTRY.containsKey(id);
    }

    /** Remove every enemy template that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
        }
        DATAPACK_KEYS.clear();
    }
}
