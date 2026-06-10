package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.barter.BarterEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry of {@link BarterEntry} reward entries, grouped by category id at query time. */
public final class BarterRegistry {

    private static final List<BarterEntry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final List<BarterEntry> DATAPACK_ENTRIES = new CopyOnWriteArrayList<>();

    private BarterRegistry() {}

    public static void register(BarterEntry e) { register(e, RegistrationSource.CODE); }

    public static void register(BarterEntry e, RegistrationSource source) {
        ENTRIES.add(e);
        if (source == RegistrationSource.DATAPACK) DATAPACK_ENTRIES.add(e);
    }

    /** Entries for {@code categoryId} whose {@code minBiomeTier <= tier}, in registration order. */
    public static List<BarterEntry> forCategory(String categoryId, int tier) {
        List<BarterEntry> out = new ArrayList<>();
        for (BarterEntry e : ENTRIES) {
            if (e.categoryId().equals(categoryId) && tier >= e.minBiomeTier()) out.add(e);
        }
        return out;
    }

    public static void clearDatapackEntries() {
        ENTRIES.removeAll(DATAPACK_ENTRIES);
        DATAPACK_ENTRIES.clear();
    }

    /** Test-only: wipe everything. */
    public static void clearAllForTest() { ENTRIES.clear(); DATAPACK_ENTRIES.clear(); }
}
