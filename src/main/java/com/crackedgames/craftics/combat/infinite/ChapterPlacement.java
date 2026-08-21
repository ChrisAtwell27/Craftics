package com.crackedgames.craftics.combat.infinite;

/**
 * What finishing a chapter in the top ten is worth on the permanent Top Players board.
 *
 * <p>Championship points rather than a medal table or an average. A medal table ranks
 * nine second places below a single win, which punishes exactly the consistency the
 * board is meant to reward; an average placement makes one early win unbeatable
 * forever. A points total is one sortable number that rewards showing up and placing
 * well, chapter after chapter.
 *
 * <p>The spread is steep at the top on purpose: winning a chapter must be worth
 * chasing, not something a player can out-farm with safe mid-table finishes.
 */
public final class ChapterPlacement {

    private ChapterPlacement() {}

    /** How deep the chapter board banks. Below this, a finish is worth nothing. */
    public static final int BANKED_PLACES = 10;

    /** Points by place, index 0 = 1st. */
    private static final int[] POINTS = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};

    /**
     * Career points for a chapter finish.
     *
     * @param place 1-indexed final position on the chapter board
     * @return points, or 0 outside the top {@link #BANKED_PLACES}. Out-of-range places
     *         return 0 rather than throwing: this runs inside chapter rotation, and an
     *         off-by-one in a caller must not leave the server mid-rotation.
     */
    public static int pointsForPlace(int place) {
        if (place < 1 || place > BANKED_PLACES) return 0;
        return POINTS[place - 1];
    }

    /**
     * Places for scores already sorted best-first, by standard competition ranking:
     * equal scores share the higher place, and the next distinct score skips the places
     * they used up, so {@code 100, 100, 90} places as {@code 1, 1, 3}.
     *
     * <p>Ranking by list index instead would split a tie by whatever order the caller's
     * list happened to be in, and a chapter placement is banked permanently - there is no
     * later chapter that undoes it.
     *
     * @param descendingScores chapter scores, highest first
     * @return one 1-indexed place per score, same length and order
     */
    public static int[] placesFor(int[] descendingScores) {
        int[] places = new int[descendingScores.length];
        for (int i = 0; i < descendingScores.length; i++) {
            places[i] = i > 0 && descendingScores[i] == descendingScores[i - 1]
                ? places[i - 1]
                : i + 1;
        }
        return places;
    }

    /** "1st", "2nd", "3rd", "11th". For chat and the personal-best line. */
    public static String ordinal(int place) {
        int lastTwo = place % 100;
        // 11, 12 and 13 are the exceptions: "11th", not "11st".
        if (lastTwo >= 11 && lastTwo <= 13) return place + "th";
        return switch (place % 10) {
            case 1 -> place + "st";
            case 2 -> place + "nd";
            case 3 -> place + "rd";
            default -> place + "th";
        };
    }
}
