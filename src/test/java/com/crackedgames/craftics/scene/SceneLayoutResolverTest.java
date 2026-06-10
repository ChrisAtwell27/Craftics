package com.crackedgames.craftics.scene;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for scene layout resolution. No Minecraft world/item types are
 * touched, so this runs under the no-bootstrap JUnit harness.
 *
 * <p>A booth is two STAND corner markers forming a rectangle with exactly one
 * NPC marker inside it. The NPC marker carries the occupant and defines the NPC
 * pose; the rectangle is the clickable area.
 */
class SceneLayoutResolverTest {

    /** A booth's two corners with an NPC inside, plus a spawn. */
    private static List<RawMarker> oneBooth(String occupant) {
        return List.of(
            new RawMarker(RawMarker.Kind.SPAWN, 0, 64, 0, 0f, ""),
            new RawMarker(RawMarker.Kind.STAND, 2, 64, 2, 0f, ""),
            new RawMarker(RawMarker.Kind.STAND, 6, 64, 5, 0f, ""),
            new RawMarker(RawMarker.Kind.NPC, 4, 64, 3, 0f, occupant)
        );
    }

    @Test
    void spawnMarkerSetsSpawnPositionAndYaw() {
        SceneLayout layout = SceneLayoutResolver.resolve(List.of(
            new RawMarker(RawMarker.Kind.SPAWN, 10, 64, 20, 90f, "")
        ));
        assertTrue(layout.hasSpawn());
        assertEquals(10, layout.spawnX());
        assertEquals(64, layout.spawnY());
        assertEquals(20, layout.spawnZ());
        assertEquals(90f, layout.spawnYaw());
        assertTrue(layout.stands().isEmpty());
    }

    @Test
    void missingSpawnMarkerYieldsNoSpawn() {
        SceneLayout layout = SceneLayoutResolver.resolve(List.of(
            new RawMarker(RawMarker.Kind.STAND, 2, 64, 2, 0f, ""),
            new RawMarker(RawMarker.Kind.STAND, 6, 64, 5, 0f, ""),
            new RawMarker(RawMarker.Kind.NPC, 4, 64, 3, 0f, "craftics:weaponsmith")
        ));
        assertFalse(layout.hasSpawn());
        assertEquals(1, layout.stands().size());
    }

    @Test
    void boothRectangleSpansTheTwoCorners() {
        SceneLayout layout = SceneLayoutResolver.resolve(oneBooth("craftics:weaponsmith"));
        assertEquals(1, layout.stands().size());
        StandSlot s = layout.stands().get(0);
        assertEquals(2, s.minX());
        assertEquals(6, s.maxX());
        assertEquals(2, s.minZ());
        assertEquals(5, s.maxZ());
        // Clicking anywhere inside the rectangle counts as this booth.
        assertTrue(s.contains(2, 2));
        assertTrue(s.contains(6, 5));
        assertTrue(s.contains(4, 3));
        assertFalse(s.contains(1, 3));
        assertFalse(s.contains(7, 3));
    }

    @Test
    void occupantAndKindComeFromTheNpcMarker() {
        StandSlot dedicated = SceneLayoutResolver.resolve(oneBooth("craftics:weaponsmith")).stands().get(0);
        assertEquals("craftics:weaponsmith", dedicated.occupant());
        assertEquals(StandSlot.Kind.DEDICATED, dedicated.kind());

        StandSlot overflow = SceneLayoutResolver.resolve(oneBooth("villager:addon")).stands().get(0);
        assertEquals("villager:addon", overflow.occupant());
        assertEquals(StandSlot.Kind.OVERFLOW, overflow.kind());
    }

    @Test
    void npcPoseComesFromNpcMarkerAndPlayerStandsInFrontFacingIt() {
        // NPC at (4,64,3) facing south (yaw 0 -> +Z). Player is one tile along
        // the NPC's facing at (4,64,4), facing back north (yaw 180).
        StandSlot s = SceneLayoutResolver.resolve(oneBooth("craftics:weaponsmith")).stands().get(0);
        assertEquals(4, s.npcX());
        assertEquals(3, s.npcZ());
        assertEquals(0f, s.npcYaw());
        assertEquals(4, s.playerX());
        assertEquals(4, s.playerZ());
        assertEquals(180f, s.playerYaw());
    }

    @Test
    void twoBoothsEachPairByTheirOwnContainedNpc() {
        SceneLayout layout = SceneLayoutResolver.resolve(List.of(
            // Booth A around (0..4, 0..4), NPC inside at (2,2)
            new RawMarker(RawMarker.Kind.STAND, 0, 64, 0, 0f, ""),
            new RawMarker(RawMarker.Kind.STAND, 4, 64, 4, 0f, ""),
            new RawMarker(RawMarker.Kind.NPC, 2, 64, 2, 0f, "a"),
            // Booth B around (20..24, 0..4), NPC inside at (22,2)
            new RawMarker(RawMarker.Kind.STAND, 20, 64, 0, 0f, ""),
            new RawMarker(RawMarker.Kind.STAND, 24, 64, 4, 0f, ""),
            new RawMarker(RawMarker.Kind.NPC, 22, 64, 2, 0f, "b")
        ));
        assertEquals(2, layout.stands().size());
        StandSlot a = layout.stands().get(0); // minX 0 sorts first
        StandSlot b = layout.stands().get(1);
        assertEquals("a", a.occupant());
        assertTrue(a.contains(2, 2));
        assertFalse(a.contains(22, 2));
        assertEquals("b", b.occupant());
        assertTrue(b.contains(22, 2));
    }

    @Test
    void npcWithNoContainingCornerPairIsSkippedWithDiagnostic() {
        // Corners form a rectangle (0..4,0..4) but the NPC is outside it.
        SceneLayoutResolver.Result r = SceneLayoutResolver.resolveWithDiagnostics(List.of(
            new RawMarker(RawMarker.Kind.STAND, 0, 64, 0, 0f, ""),
            new RawMarker(RawMarker.Kind.STAND, 4, 64, 4, 0f, ""),
            new RawMarker(RawMarker.Kind.NPC, 10, 64, 10, 0f, "a")
        ));
        assertTrue(r.layout().stands().isEmpty());
        assertEquals(1, r.skippedNpcMarkers());
    }

    @Test
    void tightPairWinsWhenAnExtraCornerWouldFormALargerRectangle() {
        // Corners a(0,0) b(4,4) tightly bound the NPC at (2,2). A third stray
        // corner c(8,8) also forms a containing rectangle with a, but that
        // rectangle would enclose b, so only the a-b pair is the tight pair.
        SceneLayout layout = SceneLayoutResolver.resolve(List.of(
            new RawMarker(RawMarker.Kind.STAND, 0, 64, 0, 0f, ""),
            new RawMarker(RawMarker.Kind.STAND, 4, 64, 4, 0f, ""),
            new RawMarker(RawMarker.Kind.STAND, 8, 64, 8, 0f, ""), // stray corner of another booth
            new RawMarker(RawMarker.Kind.NPC, 2, 64, 2, 0f, "a")
        ));
        assertEquals(1, layout.stands().size());
        StandSlot s = layout.stands().get(0);
        assertEquals(0, s.minX());
        assertEquals(4, s.maxX());
        assertEquals(0, s.minZ());
        assertEquals(4, s.maxZ());
    }

    @Test
    void firstSpawnWinsWhenMultiplePresent() {
        SceneLayout layout = SceneLayoutResolver.resolve(List.of(
            new RawMarker(RawMarker.Kind.SPAWN, 1, 64, 1, 45f, ""),
            new RawMarker(RawMarker.Kind.SPAWN, 9, 64, 9, 180f, "")
        ));
        assertEquals(1, layout.spawnX());
        assertEquals(45f, layout.spawnYaw());
    }
}
