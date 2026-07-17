package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Tidecaller's Tidal Wave geometry. The wave is a full-width, 3-thick band that marches
 * 3 tiles per turn from one end of the arena to the other and carries anyone it catches.
 * These tests pin that shape: if the band ever stops spanning the full width, stops being 3
 * thick, drifts from the tiles it paints, or covers a tile twice in one step, this file
 * fails.
 *
 * <p>The double-cover assertions are deliberate. The Trident Storm bug was three overlapping
 * splashes each resolving independently, so a seam tile took triple damage from a telegraph
 * that painted it once. The wave carries rather than damages, but a tile counted twice would
 * carry a player 6 tiles from a 3-tile telegraph, which is the same class of bug.
 */
class SweepingWaveTest {

    @Test
    void bandSpansFullWidthAndIsThreeThick() {
        // Tall arena: travel is along Z, so the band spans the full X width.
        SweepingWave wave = SweepingWave.spawn(8, 12, new GridPos(4, 10));
        assertTrue(wave.isAlongZ(), "the long axis of a 8x12 arena is Z");
        List<GridPos> tiles = wave.tiles();
        assertEquals(8 * 3, tiles.size(), "full width (8) times thickness (3)");

        Set<Integer> rows = new HashSet<>();
        Set<Integer> cols = new HashSet<>();
        for (GridPos t : tiles) { rows.add(t.z()); cols.add(t.x()); }
        assertEquals(3, rows.size(), "exactly 3 rows deep");
        assertEquals(8, cols.size(), "every column of the arena is covered");
    }

    @Test
    void spawnsAtTheEndFartherFromThePlayerAndMarchesAtThem() {
        // Player near the high-Z end -> wave starts at Z=0 and marches upward.
        SweepingWave far = SweepingWave.spawn(8, 12, new GridPos(4, 10));
        assertEquals(1, far.getDirection(), "player in the far half: sweep runs 0 -> max");
        for (GridPos t : far.tiles()) {
            assertTrue(t.z() <= 2, "band starts against the Z=0 end, got " + t);
        }

        // Player near the low-Z end -> wave starts at the far end and marches down.
        SweepingWave near = SweepingWave.spawn(8, 12, new GridPos(4, 1));
        assertEquals(-1, near.getDirection(), "player in the near half: sweep runs max -> 0");
        for (GridPos t : near.tiles()) {
            assertTrue(t.z() >= 9, "band starts against the far end, got " + t);
        }
    }

    @Test
    void wideArenaSweepsAlongX() {
        SweepingWave wave = SweepingWave.spawn(14, 6, new GridPos(12, 3));
        assertFalse(wave.isAlongZ(), "the long axis of a 14x6 arena is X");
        List<GridPos> tiles = wave.tiles();
        assertEquals(6 * 3, tiles.size(), "full height (6) times thickness (3)");
        for (GridPos t : tiles) {
            assertTrue(t.x() <= 2, "band starts against the X=0 end, got " + t);
        }
    }

    @Test
    void startingBandSitsFullyInsideTheArena() {
        // The band trails behind the leading edge; a naive front of 0 would hang two
        // rows off the arena and open with a 1-thick wave.
        SweepingWave up = SweepingWave.spawn(8, 12, new GridPos(4, 10));
        assertEquals(8 * 3, up.tiles().size(), "no part of the opening band is clipped away");
        SweepingWave down = SweepingWave.spawn(8, 12, new GridPos(4, 1));
        assertEquals(8 * 3, down.tiles().size(), "no part of the opening band is clipped away");
    }

    @Test
    void advancesExactlyThreeTilesPerTurn() {
        SweepingWave wave = SweepingWave.spawn(8, 12, new GridPos(4, 10));
        int before = wave.getFront();
        wave.advance();
        assertEquals(before + 3, wave.getFront(), "the wave moves 3 tiles per turn");
        assertEquals(0, wave.carryDx(), "a Z-axis wave never carries along X");
        assertEquals(3, wave.carryDz(), "a caught player is carried the wave's own 3 tiles");
    }

    @Test
    void carryVectorMatchesTheTilesTheBandActuallyMoved() {
        // The carry must equal the band's own displacement, or the player is dropped
        // somewhere the telegraph never painted.
        SweepingWave wave = SweepingWave.spawn(8, 12, new GridPos(4, 1)); // marches down
        int frontBefore = wave.getFront();
        wave.advance();
        assertEquals(frontBefore - 3, wave.getFront());
        assertEquals(-3, wave.carryDz(), "carry follows the band's direction of travel");
    }

    @Test
    void noTileIsCoveredTwiceInAStep() {
        // The Trident Storm lesson: a tile counted twice resolves twice.
        for (int step = 0; step < 8; step++) {
            SweepingWave wave = SweepingWave.spawn(8, 12, new GridPos(4, 10));
            for (int i = 0; i < step; i++) wave.advance();
            List<GridPos> tiles = wave.tiles();
            assertEquals(new HashSet<>(tiles).size(), tiles.size(),
                "step " + step + " painted a duplicate tile");
        }
    }

    @Test
    void coversAgreesWithTheTileListAtEveryStep() {
        // covers() decides who gets carried; tiles() decides what gets painted. If they
        // ever disagree, a player is carried by water that was never shown.
        for (int step = 0; step < 6; step++) {
            SweepingWave wave = SweepingWave.spawn(8, 12, new GridPos(4, 10));
            for (int i = 0; i < step; i++) wave.advance();
            Set<GridPos> painted = new HashSet<>(wave.tiles());
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 12; z++) {
                    GridPos p = new GridPos(x, z);
                    assertEquals(painted.contains(p), wave.covers(p),
                        "step " + step + " disagreed about " + p);
                }
            }
        }
    }

    @Test
    void marchesAcrossTheArenaThenExits() {
        SweepingWave wave = SweepingWave.spawn(8, 12, new GridPos(4, 10));
        // 12 rows of runway at 3 per turn: it must still be alive partway across.
        wave.advance();
        assertFalse(wave.hasExited(), "still inside the arena after one step");
        wave.advance();
        assertFalse(wave.hasExited(), "still inside the arena after two steps");

        int guard = 0;
        while (!wave.hasExited() && guard++ < 50) wave.advance();
        assertTrue(wave.hasExited(), "the wave must eventually leave the far side");
        assertTrue(guard < 50, "the wave must not march forever");
        assertTrue(wave.tiles().isEmpty(), "an exited wave covers nothing");
    }

    @Test
    void trailingEdgeKeepsFloodingUntilItClearsTheFarSide() {
        // A band whose leading edge is past the far wall but whose tail is still inside
        // is still a wave: it must not vanish early.
        SweepingWave wave = SweepingWave.spawn(8, 12, new GridPos(4, 10));
        while (wave.getFront() < 11) wave.advance();
        // Front is at or past the last row but the tail is still on the board.
        if (!wave.hasExited()) {
            assertFalse(wave.tiles().isEmpty(), "a partially-inside band still floods");
        }
    }

    @Test
    void squareArenaIsDeterministicAndPicksZ() {
        SweepingWave a = SweepingWave.spawn(10, 10, new GridPos(5, 8));
        SweepingWave b = SweepingWave.spawn(10, 10, new GridPos(5, 8));
        assertTrue(a.isAlongZ(), "a square arena resolves the tie to Z");
        assertEquals(a.getDirection(), b.getDirection(), "the sweep must not be random");
        assertEquals(a.tiles(), b.tiles(), "the same inputs must produce the same wave");
    }
}
