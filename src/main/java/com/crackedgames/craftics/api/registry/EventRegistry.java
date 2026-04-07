package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class EventRegistry {
    private static final List<EventEntry> EVENTS = new ArrayList<>();

    private EventRegistry() {}

    public static void register(EventEntry entry) {
        EVENTS.add(entry);
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
