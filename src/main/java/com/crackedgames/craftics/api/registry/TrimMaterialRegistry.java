package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.TrimEffects;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class TrimMaterialRegistry {
    private TrimMaterialRegistry() {}

    private static final Map<String, TrimMaterialEntry> REGISTRY = new HashMap<>();

    public static void register(TrimMaterialEntry entry) {
        REGISTRY.put(entry.materialId(), entry);
    }

    public static TrimMaterialEntry get(String materialId) {
        return REGISTRY.get(materialId);
    }

    public static Map<String, TrimMaterialEntry> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    // Convenience getters used by TrimEffects

    public static TrimEffects.Bonus getMaterialBonus(String materialId) {
        TrimMaterialEntry entry = REGISTRY.get(materialId);
        return entry != null ? entry.stat() : null;
    }

    public static String getDescription(String materialId) {
        TrimMaterialEntry entry = REGISTRY.get(materialId);
        return entry != null ? entry.description() : "";
    }
}
