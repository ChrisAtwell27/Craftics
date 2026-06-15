package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.CustomEffectDef;
import com.crackedgames.craftics.api.RegistrationSource;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of custom (addon-registered) combat status effects.
 *
 * <p>Custom effects ride alongside Craftics' fixed 23-value built-in effect enum -
 * they are keyed by string id and ticked on combatants each round. Apply them via
 * {@code UsableItemContext.applyCustomEffect(...)} or {@code CombatEntity.applyCustomEffect(...)}.
 *
 * @since 0.2.0
 */
public final class CombatEffectRegistry {

    private static final Map<String, CustomEffectDef> REGISTRY = new ConcurrentHashMap<>();
    /** Effect ids whose current definition came from a JSON datapack - dropped on /reload. */
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private CombatEffectRegistry() {}

    /** Register a custom effect from code (survives {@code /reload}). */
    public static void register(CustomEffectDef def) {
        register(def, RegistrationSource.CODE);
    }

    /** Register a custom effect, tagging whether it came from code or a datapack. */
    public static void register(CustomEffectDef def, RegistrationSource source) {
        REGISTRY.put(def.id(), def);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(def.id());
        } else {
            DATAPACK_KEYS.remove(def.id());
        }
    }

    /** The definition for {@code id}, or {@code null} if no such effect is registered. */
    @Nullable
    public static CustomEffectDef get(String id) {
        return REGISTRY.get(id);
    }

    /** Whether a custom effect with {@code id} is registered. */
    public static boolean isRegistered(String id) {
        return REGISTRY.containsKey(id);
    }

    /** Remove every custom effect that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
        }
        DATAPACK_KEYS.clear();
    }
}
