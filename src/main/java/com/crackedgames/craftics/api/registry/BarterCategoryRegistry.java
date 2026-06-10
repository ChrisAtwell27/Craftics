package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.barter.BarterCategory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Registry of {@link BarterCategory} (piglin barter "types"), keyed by id. */
public final class BarterCategoryRegistry {

    private static final Map<String, BarterCategory> REGISTRY = new ConcurrentHashMap<>();
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private BarterCategoryRegistry() {}

    public static void register(BarterCategory c) { register(c, RegistrationSource.CODE); }

    public static void register(BarterCategory c, RegistrationSource source) {
        REGISTRY.put(c.id(), c);
        if (source == RegistrationSource.DATAPACK) DATAPACK_KEYS.add(c.id());
        else DATAPACK_KEYS.remove(c.id());
    }

    @Nullable
    public static BarterCategory get(String id) { return REGISTRY.get(id); }

    public static List<BarterCategory> all() { return new ArrayList<>(REGISTRY.values()); }

    public static boolean exists(String id) { return REGISTRY.containsKey(id); }

    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) REGISTRY.remove(id);
        DATAPACK_KEYS.clear();
    }

    /** Test-only: wipe everything. */
    public static void clearAllForTest() { REGISTRY.clear(); DATAPACK_KEYS.clear(); }
}
