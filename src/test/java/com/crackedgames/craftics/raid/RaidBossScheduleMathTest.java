package com.crackedgames.craftics.raid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RaidBossScheduleMathTest {

    @Test
    void parsesAndNormalisesSlots() {
        assertEquals(List.of("18:00", "06:30"),
            RaidBossScheduleMath.parseSlots(" 18:00 , 6:30 "));
    }

    @Test
    void parseSlotsDropsMalformedEntriesAndDuplicates() {
        assertEquals(List.of("18:00"),
            RaidBossScheduleMath.parseSlots("18:00,25:00,notatime,18:00,12:99"));
        assertTrue(RaidBossScheduleMath.parseSlots("").isEmpty());
        assertTrue(RaidBossScheduleMath.parseSlots(null).isEmpty());
    }

    @Test
    void minuteOfDayConverts() {
        assertEquals(0, RaidBossScheduleMath.minuteOfDay("00:00"));
        assertEquals(1080, RaidBossScheduleMath.minuteOfDay("18:00"));
        assertEquals(1439, RaidBossScheduleMath.minuteOfDay("23:59"));
        assertEquals(-1, RaidBossScheduleMath.minuteOfDay("nope"));
    }

    @Test
    void announceMinuteClampsAtMidnight() {
        assertEquals(1020, RaidBossScheduleMath.announceMinute(1080, 60));
        assertEquals(0, RaidBossScheduleMath.announceMinute(30, 60));
    }

    @Test
    void nothingHappensBeforeTheAnnounceWindow() {
        assertEquals(RaidBossScheduleMath.Action.NONE,
            RaidBossScheduleMath.evaluate(1080, 1019, 60, 300, 100L, 99L));
    }

    @Test
    void announcesAtTheLeadBoundaryAndThroughTheWindow() {
        assertEquals(RaidBossScheduleMath.Action.ANNOUNCE,
            RaidBossScheduleMath.evaluate(1080, 1020, 60, 300, 100L, 99L));
        assertEquals(RaidBossScheduleMath.Action.ANNOUNCE,
            RaidBossScheduleMath.evaluate(1080, 1079, 60, 300, 100L, 99L));
        assertEquals(RaidBossScheduleMath.Action.ANNOUNCE,
            RaidBossScheduleMath.evaluate(1080, 1084, 60, 300, 100L, 99L));
    }

    @Test
    void pastTheJoinWindowTheSlotIsMissed() {
        // 18:00 slot, 300s window closes at 18:05, so 18:05 onward is missed.
        assertEquals(RaidBossScheduleMath.Action.MISSED,
            RaidBossScheduleMath.evaluate(1080, 1085, 60, 300, 100L, 99L));
        assertEquals(RaidBossScheduleMath.Action.MISSED,
            RaidBossScheduleMath.evaluate(1080, 1439, 60, 300, 100L, 99L));
    }

    @Test
    void aSlotAlreadyFiredTodayDoesNothing() {
        assertEquals(RaidBossScheduleMath.Action.NONE,
            RaidBossScheduleMath.evaluate(1080, 1020, 60, 300, 100L, 100L));
        assertEquals(RaidBossScheduleMath.Action.NONE,
            RaidBossScheduleMath.evaluate(1080, 1200, 60, 300, 100L, 100L));
    }

    @Test
    void aSlotFiredOnALaterDayDoesNothing() {
        assertEquals(RaidBossScheduleMath.Action.NONE,
            RaidBossScheduleMath.evaluate(1080, 1020, 60, 300, 100L, 101L));
    }

    @Test
    void aJoinWindowShorterThanAMinuteStillClosesTheNextMinute() {
        assertEquals(RaidBossScheduleMath.Action.MISSED,
            RaidBossScheduleMath.evaluate(1080, 1081, 60, 30, 100L, 99L));
    }
}
