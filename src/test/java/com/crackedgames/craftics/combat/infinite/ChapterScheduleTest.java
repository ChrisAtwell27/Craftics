package com.crackedgames.craftics.combat.infinite;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ChapterScheduleTest {

    private static final String UTC = "UTC";

    private static long millisAt(String iso, String zone) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(iso), ZoneId.of(zone))
            .toInstant().toEpochMilli();
    }

    @Test
    void emptyRuleNeverRotates() {
        assertEquals(0L, ChapterSchedule.nextAfter("", UTC, millisAt("2026-08-14T12:00", UTC)));
        assertEquals(0L, ChapterSchedule.nextAfter(null, UTC, millisAt("2026-08-14T12:00", UTC)));
    }

    @Test
    void unparseableRuleNeverRotates() {
        assertEquals(0L, ChapterSchedule.nextAfter("hourly:03", UTC,
            millisAt("2026-08-14T12:00", UTC)));
        assertEquals(0L, ChapterSchedule.nextAfter("weekly:FUNDAY:04:00", UTC,
            millisAt("2026-08-14T12:00", UTC)));
    }

    @Test
    void dailyPicksTodayWhenTimeIsStillAhead() {
        // 2026-08-14 is a Friday. 02:00 now, rule fires at 04:00 -> today.
        long next = ChapterSchedule.nextAfter(ChapterSchedule.daily(4, 0), UTC,
            millisAt("2026-08-14T02:00", UTC));
        assertEquals(millisAt("2026-08-14T04:00", UTC), next);
    }

    @Test
    void dailyRollsToTomorrowWhenTimeHasPassed() {
        long next = ChapterSchedule.nextAfter(ChapterSchedule.daily(4, 0), UTC,
            millisAt("2026-08-14T06:00", UTC));
        assertEquals(millisAt("2026-08-15T04:00", UTC), next);
    }

    @Test
    void dailyAtExactBoundaryRollsForward() {
        // Strictly after, never equal, or a rotation at exactly the boundary would
        // recompute to the same instant and fire again on the next tick forever.
        long next = ChapterSchedule.nextAfter(ChapterSchedule.daily(4, 0), UTC,
            millisAt("2026-08-14T04:00", UTC));
        assertEquals(millisAt("2026-08-15T04:00", UTC), next);
    }

    @Test
    void weeklyFindsTheNextMatchingDay() {
        // Friday 2026-08-14 02:00, rule is Monday 04:00 -> Monday 2026-08-17.
        long next = ChapterSchedule.nextAfter(
            ChapterSchedule.weekly(DayOfWeek.MONDAY, 4, 0), UTC,
            millisAt("2026-08-14T02:00", UTC));
        assertEquals(millisAt("2026-08-17T04:00", UTC), next);
    }

    @Test
    void weeklyOnTodayBeforeTheHourStaysToday() {
        // Friday 02:00, rule is Friday 04:00 -> the same day.
        long next = ChapterSchedule.nextAfter(
            ChapterSchedule.weekly(DayOfWeek.FRIDAY, 4, 0), UTC,
            millisAt("2026-08-14T02:00", UTC));
        assertEquals(millisAt("2026-08-14T04:00", UTC), next);
    }

    @Test
    void weeklyOnTodayAfterTheHourGoesAWeekOut() {
        long next = ChapterSchedule.nextAfter(
            ChapterSchedule.weekly(DayOfWeek.FRIDAY, 4, 0), UTC,
            millisAt("2026-08-14T06:00", UTC));
        assertEquals(millisAt("2026-08-21T04:00", UTC), next);
    }

    @Test
    void monthlyPicksTheDayThisMonthWhenStillAhead() {
        long next = ChapterSchedule.nextAfter(ChapterSchedule.monthly(20, 4, 0), UTC,
            millisAt("2026-08-14T06:00", UTC));
        assertEquals(millisAt("2026-08-20T04:00", UTC), next);
    }

    @Test
    void monthlyRollsToNextMonthWhenPassed() {
        long next = ChapterSchedule.nextAfter(ChapterSchedule.monthly(1, 4, 0), UTC,
            millisAt("2026-08-14T06:00", UTC));
        assertEquals(millisAt("2026-09-01T04:00", UTC), next);
    }

    @Test
    void monthlyDay31ClampsToShortMonths() {
        // From mid-September, "the 31st" must land on Sept 30, not silently skip
        // September or throw on an invalid date.
        long next = ChapterSchedule.nextAfter(ChapterSchedule.monthly(31, 4, 0), UTC,
            millisAt("2026-09-15T06:00", UTC));
        assertEquals(millisAt("2026-09-30T04:00", UTC), next);
    }

    @Test
    void monthlyDay31ClampsInFebruary() {
        long next = ChapterSchedule.nextAfter(ChapterSchedule.monthly(31, 4, 0), UTC,
            millisAt("2027-02-05T06:00", UTC));
        assertEquals(millisAt("2027-02-28T04:00", UTC), next);
    }

    @Test
    void springForwardGapStillProducesAnInstant() {
        // US Eastern skips 02:00-03:00 on 2026-03-08. A 02:30 daily rule has no
        // 02:30 that day; java.time shifts it forward rather than failing. Assert
        // only that we get a sane instant in the future, since the exact shifted
        // time is a java.time policy detail.
        long from = millisAt("2026-03-08T01:00", "UTC");
        long next = ChapterSchedule.nextAfter(
            ChapterSchedule.daily(2, 30), "America/New_York", from);
        assertTrue(next > from, "spring-forward gap must still yield a future instant");
    }

    @Test
    void countdownFormatsCoarsely() {
        assertEquals("3d 14h 22m", ChapterSchedule.formatCountdown(
            (3L * 24 * 60 + 14L * 60 + 22) * 60_000L));
        assertEquals("14h 22m", ChapterSchedule.formatCountdown((14L * 60 + 22) * 60_000L));
        assertEquals("22m", ChapterSchedule.formatCountdown(22L * 60_000L));
        assertEquals("under a minute", ChapterSchedule.formatCountdown(30_000L));
        assertEquals("any moment", ChapterSchedule.formatCountdown(0L));
        assertEquals("any moment", ChapterSchedule.formatCountdown(-5000L));
    }

    @Test
    void describeIsHumanReadable() {
        assertEquals("every Monday at 04:00 (UTC)",
            ChapterSchedule.describe(ChapterSchedule.weekly(DayOfWeek.MONDAY, 4, 0), UTC));
        assertEquals("every day at 04:00 (UTC)",
            ChapterSchedule.describe(ChapterSchedule.daily(4, 0), UTC));
        assertEquals("day 1 of every month at 04:00 (UTC)",
            ChapterSchedule.describe(ChapterSchedule.monthly(1, 4, 0), UTC));
        assertEquals("manual only", ChapterSchedule.describe("", UTC));
    }
}
