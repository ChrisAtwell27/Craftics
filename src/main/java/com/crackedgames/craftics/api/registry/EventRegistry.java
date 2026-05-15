package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EventRegistry {
    private static final List<EventEntry> EVENTS = new ArrayList<>();
    /** Events loaded from JSON datapacks — dropped on /reload. */
    private static final Set<EventEntry> DATAPACK_EVENTS = new HashSet<>();

    private EventRegistry() {}

    /** Register an event from code (survives {@code /reload}). */
    public static void register(EventEntry entry) {
        register(entry, RegistrationSource.CODE);
    }

    /** Register an event, tagging whether it came from code or a datapack. */
    public static void register(EventEntry entry, RegistrationSource source) {
        EVENTS.add(entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_EVENTS.add(entry);
        }
    }

    /** Remove every event that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        EVENTS.removeAll(DATAPACK_EVENTS);
        DATAPACK_EVENTS.clear();
    }

    public static String roll(float eventRoll, int biomeOrdinal) {
        // Collect eligible events
        List<EventEntry> eligible = new ArrayList<>();
        float totalProb = 0;
        for (EventEntry e : EVENTS) {
            if (biomeOrdinal >= e.minBiomeOrdinal()) {
                eligible.add(e);
                totalProb += e.probability();
            }
        }

        // Overflow protection
        float scale = 1.0f;
        if (totalProb > 0.90f) {
            CrafticsMod.LOGGER.warn("Event probability overflow: total={}, scaling down", totalProb);
            scale = 0.90f / totalProb;
        }

        // Roll through cascade
        float cumulative = 0;
        for (EventEntry e : eligible) {
            cumulative += e.probability() * scale;
            if (eventRoll < cumulative) {
                return e.id();
            }
        }
        return "none";
    }

    @Nullable
    public static EventEntry getById(String id) {
        for (EventEntry e : EVENTS) {
            if (e.id().equals(id)) return e;
        }
        return null;
    }

    public static List<EventEntry> getAll() {
        return List.copyOf(EVENTS);
    }
}
