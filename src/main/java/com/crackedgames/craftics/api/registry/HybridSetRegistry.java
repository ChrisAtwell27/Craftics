package com.crackedgames.craftics.api.registry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of hybrid armor sets, keyed by the unordered material pair. Mirrors
 * {@link ArmorSetRegistry}. Code-registerable; see {@code VanillaHybridSets} and
 * the Copper Age compat module for the built-in 21 entries.
 */
public final class HybridSetRegistry {
    // Code-registered only — unlike ArmorSetRegistry there is no datapack/reload
    // support in this version (hybrid sets are registered from code).
    private static final Map<String, HybridSetEntry> REGISTRY = new ConcurrentHashMap<>();

    private HybridSetRegistry() {}

    /** Canonical key for an unordered material pair: the two materials sorted, joined by '+'. */
    public static String pairKey(String matA, String matB) {
        return (matA.compareTo(matB) <= 0) ? matA + "+" + matB : matB + "+" + matA;
    }

    /** Register a hybrid set. Keyed by its (already-normalized) material pair. */
    public static void register(HybridSetEntry entry) {
        REGISTRY.put(pairKey(entry.materialA(), entry.materialB()), entry);
    }

    /** The hybrid registered for an unordered material pair, or null. */
    public static HybridSetEntry get(String matA, String matB) {
        return REGISTRY.get(pairKey(matA, matB));
    }

    /**
     * Pure detection: the canonical pair key when {@code materials} holds 4 non-null
     * entries spanning exactly 2 distinct values, else {@code null}. A null entry
     * (empty armor slot), 4-of-one (a full set), or 3+ distinct materials all yield null.
     */
    public static String detectHybridPairKey(String[] materials) {
        if (materials == null || materials.length != 4) return null;
        for (String m : materials) {
            if (m == null) return null;
        }
        Set<String> distinct = new HashSet<>(Arrays.asList(materials));
        if (distinct.size() != 2) return null;
        // The two distinct materials, in arbitrary HashSet order — fine, pairKey normalizes.
        Iterator<String> it = distinct.iterator();
        return pairKey(it.next(), it.next());
    }

    /** The registered hybrid for a player's four worn armor materials, or null. */
    public static HybridSetEntry resolve(String[] fourMaterials) {
        String key = detectHybridPairKey(fourMaterials);
        return key == null ? null : REGISTRY.get(key);
    }
}
