package com.crackedgames.craftics.combat.biomeeffect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** effectId -> its biome effect. Mirrors MinibossRegistry. */
public final class BiomeEffectRegistry {
    private static final Map<String, BiomeEffect> BY_ID = new ConcurrentHashMap<>();

    private BiomeEffectRegistry() {}

    public static void register(BiomeEffect effect) {
        BY_ID.put(effect.id(), effect);
    }

    public static BiomeEffect get(String effectId) {
        return effectId == null ? null : BY_ID.get(effectId);
    }

    public static boolean has(String effectId) {
        return effectId != null && BY_ID.containsKey(effectId);
    }

    /** Test-only: reset all registered effects so one test's registrations can't leak into another's. */
    public static void clear() {
        BY_ID.clear();
    }
}
