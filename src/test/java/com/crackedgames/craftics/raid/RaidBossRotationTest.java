package com.crackedgames.craftics.raid;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RaidBossRotationTest {

    private static List<RaidBossRotation.Candidate> three() {
        return List.of(
            new RaidBossRotation.Candidate("a", 10),
            new RaidBossRotation.Candidate("b", 10),
            new RaidBossRotation.Candidate("c", 10));
    }

    @Test
    void noCandidatesYieldsNoPick() {
        RaidBossRotation.Pick p = RaidBossRotation.pick(
            List.of(), new RaidBossState(), 100L, 7, new Random(1));
        assertNull(p.bossId());
        assertFalse(p.exclusionDropped());
    }

    @Test
    void excludesBossesUsedInsideTheWindow() {
        RaidBossState state = new RaidBossState();
        state.recordBoss(98L, "a", 30);
        state.recordBoss(99L, "b", 30);
        for (int seed = 0; seed < 25; seed++) {
            RaidBossRotation.Pick p = RaidBossRotation.pick(three(), state, 100L, 7, new Random(seed));
            assertEquals("c", p.bossId(), "seed " + seed);
            assertFalse(p.exclusionDropped());
        }
    }

    @Test
    void bossesOutsideTheWindowBecomeEligibleAgain() {
        RaidBossState state = new RaidBossState();
        state.recordBoss(90L, "a", 30);
        RaidBossRotation.Pick p = RaidBossRotation.pick(
            List.of(new RaidBossRotation.Candidate("a", 10)), state, 100L, 7, new Random(3));
        assertEquals("a", p.bossId());
        assertFalse(p.exclusionDropped());
    }

    @Test
    void exhaustedPoolDropsTheExclusionAndFlagsIt() {
        RaidBossState state = new RaidBossState();
        state.recordBoss(98L, "a", 30);
        state.recordBoss(99L, "b", 30);
        state.recordBoss(100L, "c", 30);
        RaidBossRotation.Pick p = RaidBossRotation.pick(three(), state, 100L, 7, new Random(4));
        assertNotNull(p.bossId());
        assertTrue(p.exclusionDropped());
    }

    @Test
    void zeroNoRepeatDaysNeverExcludes() {
        RaidBossState state = new RaidBossState();
        state.recordBoss(100L, "a", 30);
        RaidBossRotation.Pick p = RaidBossRotation.pick(
            List.of(new RaidBossRotation.Candidate("a", 10)), state, 100L, 0, new Random(5));
        assertEquals("a", p.bossId());
        assertFalse(p.exclusionDropped());
    }

    @Test
    void weightsBiasTheRoll() {
        List<RaidBossRotation.Candidate> weighted = List.of(
            new RaidBossRotation.Candidate("heavy", 90),
            new RaidBossRotation.Candidate("light", 10));
        Random rng = new Random(12345);
        int heavy = 0;
        for (int i = 0; i < 1000; i++) {
            if ("heavy".equals(RaidBossRotation.pick(weighted, new RaidBossState(), 1L, 7, rng).bossId())) {
                heavy++;
            }
        }
        assertTrue(heavy > 820 && heavy < 970, "heavy picked " + heavy + " times out of 1000");
    }

    @Test
    void nonPositiveWeightsAreTreatedAsOne() {
        List<RaidBossRotation.Candidate> weird = List.of(
            new RaidBossRotation.Candidate("zero", 0),
            new RaidBossRotation.Candidate("negative", -5));
        RaidBossRotation.Pick p = RaidBossRotation.pick(weird, new RaidBossState(), 1L, 7, new Random(7));
        assertTrue(p.bossId().equals("zero") || p.bossId().equals("negative"));
    }
}
