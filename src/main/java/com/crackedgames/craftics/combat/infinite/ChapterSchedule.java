package com.crackedgames.craftics.combat.infinite;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * When the chapter rotates, expressed as a recurring wall-clock rule.
 *
 * <p>Three shapes only - {@code weekly}, {@code daily}, {@code monthly} - rather than
 * cron. They cover recurring-by-date-and-time, they read unambiguously when echoed back
 * in chat, and there is no expression syntax that can silently parse to something the
 * operator did not mean.
 *
 * <p>Serialized form is a colon-delimited string stored on the save:
 * <pre>
 *   weekly:MONDAY:04:00
 *   daily:04:00
 *   monthly:1:04:00
 *   (empty)               manual rotation only
 * </pre>
 *
 * <p>All arithmetic goes through {@link ZonedDateTime}, so daylight-saving transitions
 * resolve by java.time's own rules rather than by hand-rolled offset maths.
 */
public final class ChapterSchedule {

    private ChapterSchedule() {}

    /** No schedule: the chapter rotates only when an operator says so. */
    public static final String MANUAL = "";

    public static String weekly(DayOfWeek day, int hour, int minute) {
        return "weekly:" + day.name() + ":" + pad(hour) + ":" + pad(minute);
    }

    public static String daily(int hour, int minute) {
        return "daily:" + pad(hour) + ":" + pad(minute);
    }

    public static String monthly(int dayOfMonth, int hour, int minute) {
        return "monthly:" + dayOfMonth + ":" + pad(hour) + ":" + pad(minute);
    }

    /**
     * The next instant this rule fires, strictly after {@code fromEpochMillis}.
     *
     * <p>Strictly after matters: rotation sets {@code nextRotationAt} by calling this
     * with the moment it just rotated. If the comparison were inclusive the result
     * would be that same instant and the chapter would rotate on every tick forever.
     *
     * @return epoch millis, or {@code 0L} when the rule is absent or malformed. Callers
     *         treat 0 as "never rotates on its own".
     */
    public static long nextAfter(String rule, String zoneId, long fromEpochMillis) {
        if (rule == null || rule.isEmpty()) return 0L;
        ZoneId zone = resolveZone(zoneId);
        ZonedDateTime from = java.time.Instant.ofEpochMilli(fromEpochMillis).atZone(zone);
        String[] parts = rule.split(":");
        try {
            switch (parts[0]) {
                case "daily" -> {
                    if (parts.length != 3) return 0L;
                    LocalTime at = time(parts[1], parts[2]);
                    ZonedDateTime candidate = from.toLocalDate().atTime(at).atZone(zone);
                    if (!candidate.isAfter(from)) {
                        candidate = from.toLocalDate().plusDays(1).atTime(at).atZone(zone);
                    }
                    return candidate.toInstant().toEpochMilli();
                }
                case "weekly" -> {
                    if (parts.length != 4) return 0L;
                    DayOfWeek day = DayOfWeek.valueOf(parts[1]);
                    LocalTime at = time(parts[2], parts[3]);
                    ZonedDateTime candidate = from.toLocalDate().atTime(at).atZone(zone);
                    if (from.getDayOfWeek() != day || !candidate.isAfter(from)) {
                        candidate = from.toLocalDate()
                            .with(TemporalAdjusters.next(day)).atTime(at).atZone(zone);
                    }
                    return candidate.toInstant().toEpochMilli();
                }
                case "monthly" -> {
                    if (parts.length != 4) return 0L;
                    int wanted = Integer.parseInt(parts[1]);
                    if (wanted < 1 || wanted > 31) return 0L;
                    LocalTime at = time(parts[2], parts[3]);
                    ZonedDateTime candidate = onDayOf(from.toLocalDate(), wanted, at, zone);
                    if (!candidate.isAfter(from)) {
                        candidate = onDayOf(
                            from.toLocalDate().plusMonths(1).withDayOfMonth(1), wanted, at, zone);
                    }
                    return candidate.toInstant().toEpochMilli();
                }
                default -> {
                    return 0L;
                }
            }
        } catch (RuntimeException e) {
            // A malformed rule must never take the server down or wedge rotation.
            // Returning 0 degrades to manual-only, which an operator can see and fix.
            return 0L;
        }
    }

    /** Human-readable rule, for {@code /craftics chapter info} and the board. */
    public static String describe(String rule, String zoneId) {
        if (rule == null || rule.isEmpty()) return "manual only";
        String[] parts = rule.split(":");
        String zone = resolveZone(zoneId).getId();
        try {
            return switch (parts[0]) {
                case "daily" -> "every day at " + parts[1] + ":" + parts[2] + " (" + zone + ")";
                case "weekly" -> "every " + titleCase(parts[1]) + " at "
                    + parts[2] + ":" + parts[3] + " (" + zone + ")";
                case "monthly" -> "day " + parts[1] + " of every month at "
                    + parts[2] + ":" + parts[3] + " (" + zone + ")";
                default -> "manual only";
            };
        } catch (RuntimeException e) {
            return "manual only";
        }
    }

    /** Coarse countdown for the board: "3d 14h 22m". Never shows seconds, because the
     *  board only refreshes every 5 seconds and a stale seconds field looks broken. */
    public static String formatCountdown(long remainingMillis) {
        if (remainingMillis <= 0) return "any moment";
        long minutes = remainingMillis / 60_000L;
        if (minutes < 1) return "under a minute";
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        long mins = minutes % 60;
        if (days > 0) return days + "d " + hours + "h " + mins + "m";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    /** The requested zone, falling back to the JVM default when it is unknown. */
    public static ZoneId resolveZone(String zoneId) {
        if (zoneId == null || zoneId.isEmpty()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(zoneId);
        } catch (RuntimeException e) {
            return ZoneId.systemDefault();
        }
    }

    /**
     * {@code date}'s month, on day {@code wanted}, clamped to the last day of that month.
     * A "the 31st" rule must land on Feb 28 rather than throw or skip February entirely.
     */
    private static ZonedDateTime onDayOf(LocalDate date, int wanted, LocalTime at, ZoneId zone) {
        int day = Math.min(wanted, date.lengthOfMonth());
        return date.withDayOfMonth(day).atTime(at).atZone(zone);
    }

    private static LocalTime time(String hour, String minute) {
        return LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute));
    }

    private static String pad(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static String titleCase(String word) {
        return word.charAt(0) + word.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
