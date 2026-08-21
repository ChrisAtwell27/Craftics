package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mid-fight save point codec and its two bounding rules.
 *
 * <p>Pure logic over strings and {@link CombatEntity}, so it runs without a Minecraft bootstrap.
 * The wiring (which turn captures, which entry path restores) is covered by the in-game checklist.
 */
class ResumeSnapshotTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

    private static ResumeSnapshot.Snapshot sample(long savedAt, String biome, int level, int turn) {
        var alice = new ResumeSnapshot.PlayerState(ALICE, 7.5f, 14, 2.5f, 3, 4,
            List.of(new ResumeSnapshot.EffectState("POISON", 3, 1)));
        var bob = new ResumeSnapshot.PlayerState(BOB, 20f, 20, 5f, 5, 4, List.of());
        // No identity: a vanilla zombie is its entity type and nothing more.
        var zombie = new ResumeSnapshot.EnemyState("minecraft:zombie", 6, 7, 4, 12, 3, 0, 1, 1, 1,
            "m", "", List.of(new ResumeSnapshot.EffectState("brn", 2, 0)), "", "");
        // With one, so the round trip covers the identity columns too.
        var wolf = new ResumeSnapshot.EnemyState("minecraft:wolf", 2, 2, 8, 8, 4, 0, 1, 1, 1,
            "am", ALICE.toString(), List.of(), "mymod:alpha_wolf", "Fang");
        return new ResumeSnapshot.Snapshot(savedAt, biome, level, turn,
            List.of(alice, bob), List.of(zombie, wolf));
    }

    @Test
    void aSnapshotSurvivesARoundTrip() {
        ResumeSnapshot.Snapshot original = sample(1_700_000_000_000L, "plains", 2, 5);
        ResumeSnapshot.Snapshot back = ResumeSnapshot.parse(ResumeSnapshot.serialize(original));
        assertEquals(original, back, "records compare field by field, so this covers every column");
    }

    @Test
    void anEmptyRosterStillParses() {
        // The last enemy dying leaves a snapshot with two empty sections; -1 on the section split
        // is what keeps that from reading as a truncated string.
        var snap = new ResumeSnapshot.Snapshot(1L, "cave", 0, 3, List.of(), List.of());
        assertEquals(snap, ResumeSnapshot.parse(ResumeSnapshot.serialize(snap)));
    }

    @Test
    void junkAndOldFormatsParseToNull() {
        assertNull(ResumeSnapshot.parse(null));
        assertNull(ResumeSnapshot.parse(""));
        assertNull(ResumeSnapshot.parse("not-a-snapshot"));
        assertNull(ResumeSnapshot.parse("v2,1,plains,0,1||"), "a future format is not guessed at");
    }

    @Test
    void storeKeepsOnlyTheLastTwo() {
        String store = "";
        for (int turn = 1; turn <= 5; turn++) {
            store = ResumeSnapshot.push(store, sample(1000L + turn, "plains", 2, turn));
        }
        List<ResumeSnapshot.Snapshot> kept = ResumeSnapshot.parseStore(store);
        assertEquals(ResumeSnapshot.KEEP, kept.size());
        assertEquals(4, kept.get(0).turnNumber(), "oldest kept is one turn back");
        assertEquals(5, kept.get(1).turnNumber(), "newest kept is the current turn");
    }

    @Test
    void pushDropsSnapshotsForOtherLevels() {
        // A snapshot for a level the run has moved past can never be resumed (the cursor is the
        // key), and keeping it would leave a stale fight waiting if a run revisited a level index.
        String store = ResumeSnapshot.push("", sample(1000L, "plains", 1, 9));
        store = ResumeSnapshot.push(store, sample(2000L, "plains", 2, 1));
        List<ResumeSnapshot.Snapshot> kept = ResumeSnapshot.parseStore(store);
        assertEquals(1, kept.size());
        assertEquals(2, kept.get(0).levelIndex());
    }

    @Test
    void newestForPicksTheLatestMatchingLevel() {
        String store = ResumeSnapshot.push("", sample(1000L, "plains", 2, 4));
        store = ResumeSnapshot.push(store, sample(2000L, "plains", 2, 5));
        ResumeSnapshot.Snapshot found = ResumeSnapshot.newestFor(store, "plains", 2, 2500L);
        assertNotNull(found);
        assertEquals(5, found.turnNumber(), "a rejoin resumes the turn it dropped on");
    }

    @Test
    void newestForRefusesAnotherLevelOrBiome() {
        String store = ResumeSnapshot.push("", sample(1000L, "plains", 2, 4));
        assertNull(ResumeSnapshot.newestFor(store, "plains", 3, 1500L));
        assertNull(ResumeSnapshot.newestFor(store, "cave", 2, 1500L));
    }

    @Test
    void aSnapshotOlderThanADayIsNotResumed() {
        // The brief: after 24 hours the player gets the level they were on rebuilt fresh, rather
        // than being dropped into the middle of a round they will not remember.
        long savedAt = 1_000_000L;
        String store = ResumeSnapshot.push("", sample(savedAt, "plains", 2, 4));
        long justInside = savedAt + ResumeSnapshot.MAX_AGE_MILLIS - 1;
        long justOutside = savedAt + ResumeSnapshot.MAX_AGE_MILLIS;
        assertNotNull(ResumeSnapshot.newestFor(store, "plains", 2, justInside));
        assertNull(ResumeSnapshot.newestFor(store, "plains", 2, justOutside));
    }

    @Test
    void identityColumnsSurviveARoundTrip() {
        ResumeSnapshot.Snapshot back = ResumeSnapshot.parse(
            ResumeSnapshot.serialize(sample(1L, "plains", 2, 5)));
        ResumeSnapshot.EnemyState wolf = back.enemies().get(1);
        assertEquals("mymod:alpha_wolf", wolf.aiKey());
        assertEquals("Fang", wolf.displayName());
        assertTrue(wolf.hasIdentity());
        assertFalse(back.enemies().get(0).hasIdentity(), "a plain zombie names no aiKey");
    }

    @Test
    void aRecordFromBeforeIdentityWasCapturedStillParses() {
        // Thirteen columns, the shape the previous build wrote. Appending rather than bumping
        // FORMAT is what keeps this readable: a version bump would reject every save point that
        // existed at update time, including the fight a player is standing in.
        String legacy = "v1,1700000000000,plains,2,5||minecraft:zombie,6,7,4,12,3,0,1,1,1,m,,";
        ResumeSnapshot.Snapshot back = ResumeSnapshot.parse(legacy);
        assertNotNull(back, "a 13-column record must still parse");
        assertEquals(1, back.enemies().size());
        ResumeSnapshot.EnemyState rec = back.enemies().get(0);
        assertEquals("minecraft:zombie", rec.typeId());
        assertEquals(12, rec.maxHp());
        assertEquals("", rec.aiKey(), "a missing column reads as empty, not null");
        assertEquals("", rec.displayName());
        assertFalse(rec.hasIdentity());
    }

    @Test
    void flagsDecodeIndependently() {
        var rec = new ResumeSnapshot.EnemyState("minecraft:wolf", 1, 1, 5, 5, 2, 0, 1, 1, 1,
            "atm", "", List.of(), "", "");
        assertTrue(rec.ally());
        assertTrue(rec.has('t'));
        assertTrue(rec.hasMob());
        assertFalse(rec.boss());
        assertFalse(rec.scenery());
    }

    @Test
    void mobBackingIsRecorded() {
        // The restore uses this to tell a respawnable mob from a block-backed prop it must leave
        // to the fresh build. A CombatEntity with no mob entity is the prop case.
        CombatEntity prop = new CombatEntity(-1, "craftics:grave", new GridPos(2, 2), 20, 1, 0, 1);
        prop.setScenery(true);
        prop.setInertObject(true);
        ResumeSnapshot.EnemyState rec = ResumeSnapshot.capture(prop);
        assertFalse(rec.hasMob(), "no mob entity means nothing to respawn");
        assertTrue(rec.scenery());
    }

    @Test
    void statusEffectsRoundTripThroughAnEntity() {
        CombatEntity from = new CombatEntity(1, "minecraft:zombie", new GridPos(3, 3), 20, 4, 1, 1);
        from.setPoisonTurns(3);
        from.setPoisonAmplifier(1);
        from.setWitherTurns(2);
        from.setBurningTurns(4);
        from.setSoakedTurns(2);
        from.setSlownessTurns(3);
        from.setSlownessPenalty(2);
        from.setBleedStacks(3);
        from.setEnraged(true);

        CombatEntity to = new CombatEntity(2, "minecraft:zombie", new GridPos(0, 0), 20, 4, 1, 1);
        ResumeSnapshot.applyStatus(to, ResumeSnapshot.captureStatus(from));

        assertEquals(3, to.getPoisonTurns());
        assertEquals(1, to.getPoisonAmplifier());
        assertEquals(2, to.getWitherTurns());
        assertEquals(4, to.getBurningTurns());
        assertEquals(2, to.getSoakedTurns());
        assertEquals(3, to.getSlownessTurns());
        assertEquals(2, to.getSlownessPenalty());
        assertEquals(3, to.getBleedStacks(), "bleed is stacks, carried in the amplifier slot");
        assertTrue(to.isEnraged());
    }

    @Test
    void anUnknownStatusCodeIsIgnoredRatherThanFatal() {
        CombatEntity e = new CombatEntity(1, "minecraft:zombie", new GridPos(1, 1), 10, 2, 0, 1);
        ResumeSnapshot.applyStatus(e, List.of(
            new ResumeSnapshot.EffectState("nope", 5, 2),
            new ResumeSnapshot.EffectState("poi", 3, 0)));
        assertEquals(3, e.getPoisonTurns(), "the effects it does know still land");
    }

    @Test
    void restoredHpIsExactAndNeverZero() {
        CombatEntity e = new CombatEntity(1, "minecraft:zombie", new GridPos(1, 1), 20, 2, 0, 1);
        e.restoreHp(7);
        assertEquals(7, e.getCurrentHp());
        assertTrue(e.isAlive(), "restoring never routes through the death path");
        e.restoreHp(0);
        assertEquals(1, e.getCurrentHp(), "a record only ever describes a live entity");
        e.restoreHp(9999);
        assertEquals(20, e.getCurrentHp(), "clamped to the entity's own maximum");
    }
}
