package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.TrimEffects;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    public static TrimPatternEntry get(String patternId) {
        return REGISTRY.get(patternId);
    }

    public static Map<String, TrimPatternEntry> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    // Convenience getters used by TrimEffects

    public static TrimEffects.Bonus getPerPieceBonus(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.perPieceStat() : null;
    }

    public static String getPerPieceDescription(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.perPieceDescription() : "";
    }

    public static TrimEffects.SetBonus getSetBonus(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.setBonus() : TrimEffects.SetBonus.NONE;
    }

    public static String getSetBonusName(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.setBonusName() : "";
    }

    public static String getSetBonusDescription(String patternId) {
        TrimPatternEntry entry = REGISTRY.get(patternId);
        return entry != null ? entry.setBonusDescription() : "";
    }
}
