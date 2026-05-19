package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.TrimEffects;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry of armor trim pattern combat bonuses, keyed by {@link TrimPatternEntry#patternId()}.
 *
 * <p>Craftics registers its 18 built-in patterns at startup via {@code VanillaContent};
 * addons register their own through {@code CrafticsAPI.registerTrimPattern}.
 *
 * @since 0.2.0
 */
public final class TrimPatternRegistry {
    private TrimPatternRegistry() {}

    private static final Map<String, TrimPatternEntry> REGISTRY = new HashMap<>();
    /** Pattern IDs whose current entry came from a JSON datapack — dropped on /reload. */
    private static final Set<String> DATAPACK_KEYS = new HashSet<>();

    /** Register a trim pattern from code (survives {@code /reload}). */
    public static void register(TrimPatternEntry entry) {
        register(entry, RegistrationSource.CODE);
    }

    /** Register a trim pattern, tagging whether it came from code or a datapack. */
    public static void register(TrimPatternEntry entry, RegistrationSource source) {
        REGISTRY.put(entry.patternId(), entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(entry.patternId());
        } else {
            DATAPACK_KEYS.remove(entry.patternId());
        }
    }

    /** Remove every trim pattern entry that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
        }
        DATAPACK_KEYS.clear();
    }

    /** The entry for {@code patternId}, or {@code null} if none is registered. */
    public static TrimPatternEntry get(String patternId) {
        return REGISTRY.get(patternId);
    }

    /** Every registered trim pattern entry, as an unmodifiable view. */
    public static Map<String, TrimPatternEntry> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    // Convenience getters used by TrimEffects

    /** The per-piece stat bonus type for {@code patternId}, or {@code null} if not registered. */
    public static TrimEffects.Bonus getPerPieceBonus(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.perPieceStat() : null;
    }

    /** Short per-piece bonus description for {@code patternId}, or an empty string if not registered. */
    public static String getPerPieceDescription(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.perPieceDescription() : "";
    }

    /** The four-piece set bonus mechanic for {@code patternId}, or {@code NONE} if not registered. */
    public static TrimEffects.SetBonus getSetBonus(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.setBonus() : TrimEffects.SetBonus.NONE;
    }

    /** Display name of the set bonus for {@code patternId}, or an empty string if not registered. */
    public static String getSetBonusName(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.setBonusName() : "";
    }

    /** One-line mechanic description of the set bonus for {@code patternId}, or an empty string if not registered. */
    public static String getSetBonusDescription(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.setBonusDescription() : "";
    }
}
