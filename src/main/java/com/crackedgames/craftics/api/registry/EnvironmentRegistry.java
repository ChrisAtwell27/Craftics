package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.EnvironmentDef;
import com.crackedgames.craftics.api.RegistrationSource;
import net.minecraft.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of arena environment themes, keyed by {@link EnvironmentDef#id()}.
 *
 * <p>Craftics registers its 13 built-in environments at startup ({@code VanillaEnvironments});
 * addons register their own from code via {@code CrafticsAPI.registerEnvironment} or from a
 * {@code craftics/environments/} datapack. A biome selects one by id with its
 * {@code "environment"} field.
 *
 * @since 0.2.0
 */
public final class EnvironmentRegistry {

    private static final Map<String, EnvironmentDef> REGISTRY = new ConcurrentHashMap<>();
    /** Ids whose current entry came from a JSON datapack — dropped on {@code /reload}. */
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    /** Plains-like fallback returned by {@link #get} for an unregistered id. */
    private static final EnvironmentDef DEFAULT = new EnvironmentDef(
        "plains", Blocks.GRASS_BLOCK, Blocks.OAK_FENCE, Blocks.LANTERN, "plains");

    private EnvironmentRegistry() {}

    /** Register an environment from code (survives {@code /reload}). */
    public static void register(EnvironmentDef def) {
        register(def, RegistrationSource.CODE);
    }

    /** Register an environment, tagging whether it came from code or a datapack. */
    public static void register(EnvironmentDef def, RegistrationSource source) {
        REGISTRY.put(def.id(), def);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(def.id());
        } else {
            DATAPACK_KEYS.remove(def.id());
        }
    }

    /**
     * The environment for {@code id}, or a plains-like default if none is registered —
     * never {@code null}, so arena/combat code can call it unconditionally.
     */
    public static EnvironmentDef get(String id) {
        EnvironmentDef def = REGISTRY.get(id);
        return def != null ? def : DEFAULT;
    }

    /** The environment for {@code id}, or {@code null} if none is registered. */
    @Nullable
    public static EnvironmentDef getOrNull(String id) {
        return REGISTRY.get(id);
    }

    /** Whether an environment is registered under {@code id}. */
    public static boolean isRegistered(String id) {
        return REGISTRY.containsKey(id);
    }

    /** Remove every environment that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
        }
        DATAPACK_KEYS.clear();
    }
}
