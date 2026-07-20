package com.crackedgames.craftics.combat.miniboss;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** biomeId -> its miniboss mechanic. Mirrors AIRegistry. */
public final class MinibossRegistry {
    private static final Map<String, MinibossMechanic> BY_BIOME = new ConcurrentHashMap<>();

    private MinibossRegistry() {}

    public static void register(MinibossMechanic m) {
        BY_BIOME.put(m.biomeId(), m);
    }

    public static MinibossMechanic get(String biomeId) {
        return biomeId == null ? null : BY_BIOME.get(biomeId);
    }

    public static boolean has(String biomeId) {
        return biomeId != null && BY_BIOME.containsKey(biomeId);
    }

    /** Test-only: reset all registered mechanics so one test's registrations can't leak into another's. */
    public static void clear() {
        BY_BIOME.clear();
    }
}
