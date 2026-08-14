package com.crackedgames.craftics.combat.infinite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChapterPlacementTest {

    @Test
    void firstPlaceScoresTwentyFive() {
        assertEquals(25, ChapterPlacement.pointsForPlace(1));
    }

    @Test
    void tenthPlaceScoresOne() {
        assertEquals(1, ChapterPlacement.pointsForPlace(10));
    }

    @Test
    void outsideTheTopTenScoresNothing() {
        assertEquals(0, ChapterPlacement.pointsForPlace(11));
        assertEquals(0, ChapterPlacement.pointsForPlace(500));
    }

    @Test
    void nonPositivePlacesScoreNothing() {
        // Defensive: a 0 or negative place means a caller mixed up 0- and 1-indexing.
        // Award nothing rather than throwing inside the rotation path.
        assertEquals(0, ChapterPlacement.pointsForPlace(0));
        assertEquals(0, ChapterPlacement.pointsForPlace(-1));
    }

    @Test
    void pointsDecreaseMonotonically() {
        for (int place = 1; place < ChapterPlacement.BANKED_PLACES; place++) {
            assertTrue(ChapterPlacement.pointsForPlace(place)
                       > ChapterPlacement.pointsForPlace(place + 1),
                "place " + place + " must outscore place " + (place + 1));
        }
    }

    @Test
    void winningBeatsTwoMidTableFinishes() {
        // The spread is deliberately steep at the top so a chapter win is worth
        // chasing instead of farming a safe mid-table finish twice over.
        assertTrue(ChapterPlacement.pointsForPlace(1) > ChapterPlacement.pointsForPlace(4) * 2);
    }

    @Test
    void ordinalsReadCorrectly() {
        assertEquals("1st", ChapterPlacement.ordinal(1));
        assertEquals("2nd", ChapterPlacement.ordinal(2));
        assertEquals("3rd", ChapterPlacement.ordinal(3));
        assertEquals("4th", ChapterPlacement.ordinal(4));
        assertEquals("11th", ChapterPlacement.ordinal(11));
        assertEquals("12th", ChapterPlacement.ordinal(12));
        assertEquals("13th", ChapterPlacement.ordinal(13));
        assertEquals("21st", ChapterPlacement.ordinal(21));
        assertEquals("22nd", ChapterPlacement.ordinal(22));
        assertEquals("23rd", ChapterPlacement.ordinal(23));
        assertEquals("101st", ChapterPlacement.ordinal(101));
        assertEquals("111th", ChapterPlacement.ordinal(111));
    }
}
