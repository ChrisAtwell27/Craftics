package com.crackedgames.craftics.raid;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure wall-clock arithmetic for the daily raid slots. The runtime ticker feeds
 * it the current server-local minute-of-day and epoch day; everything about when
 * to announce, when a slot was missed, and when to stay quiet is decided here so
 * it can be tested without a server.
 *
 * <p>A slot fires exactly once per day, and the ANNOUNCE action is what marks it
 * fired, so the whole announce/open/start sequence that follows lives purely in
 * memory. A restart between announce and start therefore loses that raid rather
 * than replaying it, which is deliberate: firing an hour late is worse than
 * skipping.
 */
public final class RaidBossScheduleMath {
    private RaidBossScheduleMath() {}

    public enum Action { NONE, ANNOUNCE, MISSED }

    /** "18:00, 6:30" to ["18:00", "06:30"]. Malformed entries are dropped. */
    public static List<String> parseSlots(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String raw : csv.split(",")) {
            String slot = raw.trim();
            int minute = minuteOfDay(slot);
            if (minute < 0) continue;
            String normalised = String.format("%02d:%02d", minute / 60, minute % 60);
            if (!out.contains(normalised)) out.add(normalised);
        }
        return out;
    }

    /** Minutes since midnight for "HH:mm", or -1 when the string is not a time. */
    public static int minuteOfDay(String slot) {
        if (slot == null) return -1;
        int colon = slot.indexOf(':');
        if (colon <= 0 || colon == slot.length() - 1) return -1;
        try {
            int hour = Integer.parseInt(slot.substring(0, colon).trim());
            int minute = Integer.parseInt(slot.substring(colon + 1).trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** When the advance warning goes out, clamped so a small-hours slot announces at midnight. */
    public static int announceMinute(int slotMinute, int leadMinutes) {
        return Math.max(0, slotMinute - Math.max(0, leadMinutes));
    }

    /**
     * What this slot should do right now.
     *
     * @param slotMinute        minute-of-day the raid starts
     * @param nowMinute         current server-local minute-of-day
     * @param leadMinutes       advance-warning length
     * @param joinWindowSeconds join window length
     * @param today             current epoch day
     * @param lastFiredDay      epoch day this slot last fired
     */
    public static Action evaluate(int slotMinute, int nowMinute, int leadMinutes,
                                  int joinWindowSeconds, long today, long lastFiredDay) {
        if (lastFiredDay >= today) return Action.NONE;
        int windowCloseMinute = slotMinute + Math.max(1, (joinWindowSeconds + 59) / 60);
        if (nowMinute >= windowCloseMinute) return Action.MISSED;
        if (nowMinute >= announceMinute(slotMinute, leadMinutes)) return Action.ANNOUNCE;
        return Action.NONE;
    }
}
