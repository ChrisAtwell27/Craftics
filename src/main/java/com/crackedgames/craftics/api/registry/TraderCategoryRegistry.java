package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.TraderCategory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of {@link TraderCategory} (villager trader "types") and the stock each one sells.
 *
 * <p>Iteration order is INSERTION order, deliberately. The Trading Hall seats booths by the
 * registry's order, so a hash-ordered registry would shuffle the whole hall's layout whenever
 * the JVM felt like it. Vanilla traders register first and therefore always take the same
 * booths; addon traders append after them.
 *
 * @since 0.2.10
 */
public final class TraderCategoryRegistry {

    /** Insertion-ordered: booth index depends on it. Synchronized rather than concurrent, since
     *  registration happens at mod init and reads dominate afterwards. */
    private static final Map<String, TraderCategory> REGISTRY =
        Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, TraderStockProvider> STOCK = new ConcurrentHashMap<>();
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private TraderCategoryRegistry() {}

    public static void register(TraderCategory c, TraderStockProvider stock) {
        register(c, stock, RegistrationSource.CODE);
    }

    public static void register(TraderCategory c, TraderStockProvider stock, RegistrationSource source) {
        REGISTRY.put(c.id(), c);
        if (stock != null) STOCK.put(c.id(), stock);
        if (source == RegistrationSource.DATAPACK) DATAPACK_KEYS.add(c.id());
        else DATAPACK_KEYS.remove(c.id());
    }

    @Nullable
    public static TraderCategory get(String id) { return REGISTRY.get(id); }

    @Nullable
    public static TraderStockProvider stockFor(String id) { return STOCK.get(id); }

    /** Every registered trader, in registration order. */
    public static List<TraderCategory> all() {
        synchronized (REGISTRY) {
            return new ArrayList<>(REGISTRY.values());
        }
    }

    /** How many traders exist - the number of booths the Trading Hall needs. */
    public static int count() { return REGISTRY.size(); }

    public static boolean exists(String id) { return REGISTRY.containsKey(id); }

    /**
     * Resolve a possibly-legacy id to a registered trader.
     *
     * <p>Saves written before 0.2.10 stored the bare enum name ({@code "WEAPONSMITH"}), because
     * trader types used to be a Java enum. Accept that form and map it onto the modern namespaced
     * id so nobody loses the merchants they have already met. Returns {@code null} for an id no
     * longer registered at all (e.g. an addon the player has since uninstalled), which callers
     * must treat as "skip", never as an error.
     */
    @Nullable
    public static TraderCategory resolveLegacy(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        TraderCategory direct = REGISTRY.get(rawId);
        if (direct != null) return direct;
        // Legacy: a bare, upper-cased enum name with no namespace.
        String local = rawId.toLowerCase(java.util.Locale.ROOT);
        synchronized (REGISTRY) {
            for (TraderCategory c : REGISTRY.values()) {
                if (c.localId().equals(local)) return c;
            }
        }
        return null;
    }

    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
            STOCK.remove(id);
        }
        DATAPACK_KEYS.clear();
    }

    /** Test-only: wipe everything. */
    public static void clearAllForTest() {
        REGISTRY.clear();
        STOCK.clear();
        DATAPACK_KEYS.clear();
    }
}
