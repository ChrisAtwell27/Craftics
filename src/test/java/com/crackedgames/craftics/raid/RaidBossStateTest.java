package com.crackedgames.craftics.raid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaidBossStateTest {

    @Test
    void emptyStringParsesToEmptyState() {
        RaidBossState s = RaidBossState.parse("");
        assertTrue(s.slotLastFiredDay().isEmpty());
        assertTrue(s.history().isEmpty());
        assertEquals("", s.serialize());
    }

    @Test
    void roundTripsSlotsAndHistory() {
        RaidBossState s = new RaidBossState();
        s.markFired("18:00", 20310L);
        s.markFired("06:30", 20311L);
        s.recordBoss(20305L, "ashen_tyrant", 7);
        s.recordBoss(20306L, "frost_maw", 7);

        RaidBossState back = RaidBossState.parse(s.serialize());
        assertEquals(20310L, back.slotLastFiredDay().get("18:00"));
        assertEquals(20311L, back.slotLastFiredDay().get("06:30"));
        assertEquals(2, back.history().size());
        assertEquals("ashen_tyrant", back.history().get(0).bossId());
        assertEquals(20305L, back.history().get(0).day());
    }

    @Test
    void slotKeysKeepTheirColon() {
        RaidBossState s = new RaidBossState();
        s.markFired("18:00", 5L);
        assertTrue(s.serialize().contains("18:00"));
        assertEquals(5L, RaidBossState.parse(s.serialize()).slotLastFiredDay().get("18:00"));
    }

    @Test
    void recordBossPrunesBeyondTheKeepWindow() {
        RaidBossState s = new RaidBossState();
        s.recordBoss(100L, "old_one", 7);
        s.recordBoss(120L, "new_one", 7);
        assertEquals(1, s.history().size());
        assertEquals("new_one", s.history().get(0).bossId());
    }

    @Test
    void usedWithinRespectsTheWindow() {
        RaidBossState s = new RaidBossState();
        s.recordBoss(100L, "ashen_tyrant", 30);
        assertTrue(s.usedWithin("ashen_tyrant", 103L, 7));
        assertFalse(s.usedWithin("ashen_tyrant", 108L, 7));
        assertFalse(s.usedWithin("other", 101L, 7));
    }

    @Test
    void malformedInputIsIgnoredRatherThanThrowing() {
        RaidBossState s = RaidBossState.parse("slots=garbage|history=alsogarbage|nonsense");
        assertTrue(s.slotLastFiredDay().isEmpty());
        assertTrue(s.history().isEmpty());
    }
}
