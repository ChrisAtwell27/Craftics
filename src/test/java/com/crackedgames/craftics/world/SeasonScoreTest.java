package com.crackedgames.craftics.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the season score arithmetic. */
class SeasonScoreTest {

    private static SeasonScore.Inputs inputs(int allTime, int placement, int chapters,
                                             int depth, int discovered, int ngPlus, boolean raid) {
        return new SeasonScore.Inputs(allTime, placement, chapters, depth, discovered, ngPlus, raid);
    }

    @Test
    void aFreshPlayerScoresZero() {
        // highestBiomeUnlocked starts at 1, so a player who has done nothing must still be 0 -
        // otherwise everyone who has ever logged in appears on the board.
        SeasonScore.Breakdown b = SeasonScore.score(inputs(0, 0, 0, 1, 0, 0, false));
        assertEquals(0, b.chapterScore());
        assertEquals(0, b.islandScore());
        assertEquals(0, b.total());
    }

    @Test
    void halvesAreScoredSeparatelyAndSum() {
        SeasonScore.Breakdown b = SeasonScore.score(inputs(500, 20, 4, 5, 6, 1, true));
        //                       500*1        20*10        4*5
        assertEquals(500 + 200 + 20, b.chapterScore());
        //                       (5-1)*100    6*25        1*250      raid
        assertEquals(400 + 150 + 250 + 100, b.islandScore());
        assertEquals(b.chapterScore() + b.islandScore(), b.total());
    }

    @Test
    void eitherHalfAloneStillScores() {
        // The point of adding them: an infinite-only player and an island-only player both rank.
        assertTrue(SeasonScore.score(inputs(900, 0, 0, 1, 0, 0, false)).total() > 0);
        assertTrue(SeasonScore.score(inputs(0, 0, 0, 8, 8, 0, false)).total() > 0);
    }

    @Test
    void negativeStoredValuesCannotProduceANegativeScore() {
        // A corrupt or hand-edited save must not let someone rank below zero, or sort above
        // everyone by wrapping expectations about ordering.
        SeasonScore.Breakdown b = SeasonScore.score(inputs(-50, -5, -2, -3, -4, -1, false));
        assertEquals(0, b.chapterScore());
        assertEquals(0, b.islandScore());
    }

    @Test
    void aLargeCareerDoesNotOverflow() {
        // Nothing clamps the stored ints, so weights of 10 and 100 overflow int arithmetic
        // well before the stored values themselves run out of room.
        SeasonScore.Breakdown b = SeasonScore.score(
            inputs(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 0, 0, false));
        assertTrue(b.chapterScore() > Integer.MAX_VALUE,
            "components must be widened to long before multiplying");
    }

    @Test
    void weightsAreHonoured() {
        SeasonScore.Weights doubled = new SeasonScore.Weights(2, 20, 10, 200, 50, 500, 200);
        SeasonScore.Inputs in = inputs(10, 1, 1, 3, 2, 1, true);
        assertEquals(SeasonScore.score(in).total() * 2, SeasonScore.score(in, doubled).total());
    }

    @Test
    void nullWeightsFallBackToTheDefault() {
        SeasonScore.Inputs in = inputs(10, 1, 1, 3, 2, 1, true);
        assertEquals(SeasonScore.score(in).total(), SeasonScore.score(in, null).total());
    }

    @Test
    void countCsvIgnoresBlanksAndEmptyStrings() {
        // "" splits to one empty element, which would credit an undiscovered player with one.
        assertEquals(0, SeasonScore.countCsv(""));
        assertEquals(0, SeasonScore.countCsv(null));
        assertEquals(0, SeasonScore.countCsv("   "));
        assertEquals(1, SeasonScore.countCsv("plains"));
        assertEquals(3, SeasonScore.countCsv("plains,cave,nether"));
        assertEquals(2, SeasonScore.countCsv("plains,,cave"));
        assertEquals(2, SeasonScore.countCsv("plains,cave,"));
    }
}
