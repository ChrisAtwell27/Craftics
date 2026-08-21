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
    void placesFollowTheScoresWhenNobodyTies() {
        assertArrayEquals(new int[]{1, 2, 3, 4},
            ChapterPlacement.placesFor(new int[]{900, 500, 120, 3}));
    }

    @Test
    void tiedScoresShareThePlaceAndTheNextScoreSkips() {
        // Standard competition ranking. Two players on 100 are both 1st, and the player
        // on 90 is 3rd - there was no 2nd place to take.
        assertArrayEquals(new int[]{1, 1, 3},
            ChapterPlacement.placesFor(new int[]{100, 100, 90}));
        assertArrayEquals(new int[]{1, 2, 2, 2, 5},
            ChapterPlacement.placesFor(new int[]{100, 90, 90, 90, 10}));
    }

    @Test
    void tiedPlayersEarnIdenticalPoints() {
        // The whole point: banked points are permanent, so two equal scores must not be
        // separated by whatever order the standings list happened to be built in.
        int[] places = ChapterPlacement.placesFor(new int[]{50, 50});
        assertEquals(ChapterPlacement.pointsForPlace(places[0]),
                     ChapterPlacement.pointsForPlace(places[1]));
        assertEquals(25, ChapterPlacement.pointsForPlace(places[0]));
    }

    @Test
    void aWholeFieldTiedIsAllFirstPlace() {
        assertArrayEquals(new int[]{1, 1, 1, 1},
            ChapterPlacement.placesFor(new int[]{7, 7, 7, 7}));
    }

    @Test
    void aTieAtTheCutoffPushesLaterPlayersOutOfTheBankedRange() {
        // Ten players tied on 5 are all 1st; the eleventh is 11th and banks nothing.
        int[] scores = new int[11];
        for (int i = 0; i < 10; i++) scores[i] = 5;
        scores[10] = 4;
        int[] places = ChapterPlacement.placesFor(scores);
        assertEquals(1, places[9]);
        assertEquals(11, places[10]);
        assertEquals(0, ChapterPlacement.pointsForPlace(places[10]));
    }

    @Test
    void anEmptyFieldPlacesNobody() {
        assertEquals(0, ChapterPlacement.placesFor(new int[0]).length);
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
