package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.TrimEffects;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry of armor trim material combat bonuses, keyed by {@link TrimMaterialEntry#materialId()}.
 *
 * <p>Craftics registers its 11 built-in materials at startup via {@code VanillaContent};
 * addons register their own through {@code CrafticsAPI.registerTrimMaterial}.
 *
 * @since 0.2.0
 */
public final class TrimMaterialRegistry {
    private TrimMaterialRegistry() {}

    private static final Map<String, TrimMaterialEntry> REGISTRY = new HashMap<>();
    /** Material IDs whose current entry came from a JSON datapack - dropped on /reload. */
    private static final Set<String> DATAPACK_KEYS = new HashSet<>();

    /** Register a trim material from code (survives {@code /reload}). */
    public static void register(TrimMaterialEntry entry) {
        register(entry, RegistrationSource.CODE);
    }

    /** Register a trim material, tagging whether it came from code or a datapack. */
    public static void register(TrimMaterialEntry entry, RegistrationSource source) {
        REGISTRY.put(entry.materialId(), entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(entry.materialId());
        } else {
            DATAPACK_KEYS.remove(entry.materialId());
        }
    }

    /** Remove every trim material entry that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
        }
        DATAPACK_KEYS.clear();
    }

    /** The entry for {@code materialId}, or {@code null} if none is registered. */
    public static TrimMaterialEntry get(String materialId) {
        return REGISTRY.get(materialId);
    }

    /** Every registered trim material entry, as an unmodifiable view. */
    public static Map<String, TrimMaterialEntry> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    // Convenience getters used by TrimEffects

    /** The stat bonus type for {@code materialId}, or {@code null} if not registered. */
    public static TrimEffects.Bonus getMaterialBonus(String materialId) {
        TrimMaterialEntry entry = REGISTRY.get(materialId);
        return entry != null ? entry.stat() : null;
    }

    /** Tooltip description for {@code materialId}, or an empty string if not registered. */
    public static String getDescription(String materialId) {
        TrimMaterialEntry entry = REGISTRY.get(materialId);
        return entry != null ? entry.description() : "";
    }
}
