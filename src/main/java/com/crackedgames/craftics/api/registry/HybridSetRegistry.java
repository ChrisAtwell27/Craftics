package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of hybrid armor sets, keyed by the unordered material pair.
 *
 * <p>A hybrid entry activates when the player wears exactly two distinct armor
 * materials across all four slots. Craftics registers 15 built-in pairs at startup
 * via {@code VanillaHybridSets}; the Copper Age compat module adds 6 more. Addons
 * register their own through {@code CrafticsAPI.registerHybridSet}.
 *
 * @since 0.2.0
 */
public final class HybridSetRegistry {
    private static final Map<String, HybridSetEntry> REGISTRY = new ConcurrentHashMap<>();
    /** Pair keys whose current entry came from a JSON datapack — dropped on /reload. */
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private HybridSetRegistry() {}

    /** Canonical key for an unordered material pair: the two materials sorted, joined by '+'. */
    public static String pairKey(String matA, String matB) {
        return (matA.compareTo(matB) <= 0) ? matA + "+" + matB : matB + "+" + matA;
    }

    /** Register a hybrid set from code (survives {@code /reload}). Keyed by its material pair. */
    public static void register(HybridSetEntry entry) {
        register(entry, RegistrationSource.CODE);
    }

    /** Register a hybrid set, tagging whether it came from code or a datapack. */
    public static void register(HybridSetEntry entry, RegistrationSource source) {
        String key = pairKey(entry.materialA(), entry.materialB());
        REGISTRY.put(key, entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(key);
        } else {
            DATAPACK_KEYS.remove(key);
        }
    }

    /** Remove every hybrid set that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String key : DATAPACK_KEYS) {
            REGISTRY.remove(key);
        }
        DATAPACK_KEYS.clear();
    }

    /** The hybrid registered for an unordered material pair, or null. */
    public static HybridSetEntry get(String matA, String matB) {
        return REGISTRY.get(pairKey(matA, matB));
    }

    /**
     * Pure detection: the canonical pair key when {@code materials} holds 4 non-null
     * entries split exactly 2/2 between two distinct values, else {@code null}.
     *
     * <p>A null entry (empty armor slot), 4-of-one (a full set), 3/1 split, or
     * 3+ distinct materials all yield null. The 3/1 case is intentionally
     * excluded: hybrids are meant to reward a deliberate two-piece-each combo,
     * not a near-full set with a single odd piece.
     */
    public static String detectHybridPairKey(String[] materials) {
        if (materials == null || materials.length != 4) return null;
        for (String m : materials) {
            if (m == null) return null;
        }
        Set<String> distinct = new HashSet<>(Arrays.asList(materials));
        if (distinct.size() != 2) return null;
        Iterator<String> it = distinct.iterator();
        String a = it.next();
        String b = it.next();
        int countA = 0;
        for (String m : materials) if (a.equals(m)) countA++;
        // Require a 2/2 split; reject 3/1 (and by symmetry 1/3).
        if (countA != 2) return null;
        return pairKey(a, b);
    }

    /**
     * The registered hybrid for a player's four worn armor materials, or {@code null}
     * if the four materials do not form a valid two-material pair.
     *
     * @param fourMaterials the material keys for the four armor slots (none may be null)
     * @return the matching hybrid entry, or {@code null}
     */
    public static HybridSetEntry resolve(String[] fourMaterials) {
        String key = detectHybridPairKey(fourMaterials);
        return key == null ? null : REGISTRY.get(key);
    }
}
