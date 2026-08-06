package com.crackedgames.craftics.raid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted daily-raid bookkeeping, encoded as one string so it costs a single
 * field in CrafticsSavedData's two version-branched NBT paths instead of a
 * nested compound in each. Same trick PlayerProgression uses for its stats blob.
 *
 * <p>Wire format: {@code slots=<slot>><day>;...|history=<day>><bossId>;...}
 * The key/value separator is '>' precisely because slot keys ("18:00") contain
 * a colon.
 */
public final class RaidBossState {

    public record HistoryEntry(long day, String bossId) {}

    private final Map<String, Long> slotLastFiredDay = new LinkedHashMap<>();
    private final List<HistoryEntry> history = new ArrayList<>();

    public Map<String, Long> slotLastFiredDay() { return slotLastFiredDay; }
    public List<HistoryEntry> history() { return history; }

    /** Mark a configured time slot as having fired on {@code day} (epoch day). */
    public void markFired(String slot, long day) {
        slotLastFiredDay.put(slot, day);
    }

    /** Record today's boss and prune history older than {@code keepDays}. */
    public void recordBoss(long day, String bossId, int keepDays) {
        history.add(new HistoryEntry(day, bossId));
        long cutoff = day - Math.max(0, keepDays);
        history.removeIf(e -> e.day() < cutoff);
    }

    /** True when {@code bossId} appears in history within the last {@code days} days. */
    public boolean usedWithin(String bossId, long today, int days) {
        long cutoff = today - Math.max(0, days);
        for (HistoryEntry e : history) {
            if (e.bossId().equals(bossId) && e.day() > cutoff) return true;
        }
        return false;
    }

    public String serialize() {
        if (slotLastFiredDay.isEmpty() && history.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("slots=");
        boolean first = true;
        for (Map.Entry<String, Long> e : slotLastFiredDay.entrySet()) {
            if (!first) sb.append(';');
            sb.append(e.getKey()).append('>').append(e.getValue());
            first = false;
        }
        sb.append("|history=");
        first = true;
        for (HistoryEntry e : history) {
            if (!first) sb.append(';');
            sb.append(e.day()).append('>').append(e.bossId());
            first = false;
        }
        return sb.toString();
    }

    public static RaidBossState parse(String encoded) {
        RaidBossState state = new RaidBossState();
        if (encoded == null || encoded.isBlank()) return state;
        for (String section : encoded.split("\\|")) {
            int eq = section.indexOf('=');
            if (eq < 0) continue;
            String name = section.substring(0, eq);
            String body = section.substring(eq + 1);
            if (body.isBlank()) continue;
            for (String item : body.split(";")) {
                int sep = item.lastIndexOf('>');
                if (sep <= 0 || sep == item.length() - 1) continue;
                String left = item.substring(0, sep);
                String right = item.substring(sep + 1);
                try {
                    if ("slots".equals(name)) {
                        state.slotLastFiredDay.put(left, Long.parseLong(right));
                    } else if ("history".equals(name)) {
                        state.history.add(new HistoryEntry(Long.parseLong(left), right));
                    }
                } catch (NumberFormatException ignored) {
                    // A corrupt entry drops out; the rest of the state still loads.
                }
            }
        }
        return state;
    }
}
