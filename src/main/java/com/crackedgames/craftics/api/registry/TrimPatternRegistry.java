package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.TrimEffects;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class TrimPatternRegistry {
    private TrimPatternRegistry() {}

    private static final Map<String, TrimPatternEntry> REGISTRY = new HashMap<>();

    public static void register(TrimPatternEntry entry) {
        REGISTRY.put(entry.patternId(), entry);
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
