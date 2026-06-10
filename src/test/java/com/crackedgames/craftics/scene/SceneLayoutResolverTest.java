package com.crackedgames.craftics.scene;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for scene layout resolution. No Minecraft world/item types are
 * touched, so this runs under the no-bootstrap JUnit harness.
 */
class SceneLayoutResolverTest {

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
            new RawMarker(RawMarker.Kind.STAND, 1, 64, 1, 0f, "craftics:weaponsmith")
        ));
        assertFalse(layout.hasSpawn());
        assertEquals(1, layout.stands().size());
    }

    @Test
    void dedicatedAndOverflowStandsAreClassified() {
        SceneLayout layout = SceneLayoutResolver.resolve(List.of(
            new RawMarker(RawMarker.Kind.SPAWN, 0, 64, 0, 0f, ""),
            new RawMarker(RawMarker.Kind.STAND, 2, 64, 2, 0f, "craftics:weaponsmith"),
            new RawMarker(RawMarker.Kind.STAND, 4, 64, 2, 0f, "villager:addon")
        ));
        assertEquals(2, layout.stands().size());
        StandSlot dedicated = layout.stands().get(0); // (2,2) sorts before (4,2)
        StandSlot overflow = layout.stands().get(1);
        assertEquals(StandSlot.Kind.DEDICATED, dedicated.kind());
        assertEquals("craftics:weaponsmith", dedicated.occupant());
        assertEquals(StandSlot.Kind.OVERFLOW, overflow.kind());
        assertEquals("villager:addon", overflow.occupant());
    }

    @Test
    void standsAreSortedDeterministicallyByXThenZThenY() {
        SceneLayout layout = SceneLayoutResolver.resolve(List.of(
            new RawMarker(RawMarker.Kind.STAND, 5, 64, 1, 0f, "c"),
            new RawMarker(RawMarker.Kind.STAND, 1, 64, 9, 0f, "a"),
            new RawMarker(RawMarker.Kind.STAND, 1, 64, 2, 0f, "b")
        ));
        assertEquals("b", layout.stands().get(0).occupant()); // x=1,z=2
        assertEquals("a", layout.stands().get(1).occupant()); // x=1,z=9
        assertEquals("c", layout.stands().get(2).occupant()); // x=5
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
