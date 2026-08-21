package com.crackedgames.craftics.world;

/**
 * One number summarising a player's season: what they did in infinite mode, plus how far they
 * have taken their own island.
 *
 * <p>Pure and Minecraft-free so the arithmetic can be tested, in the same spirit as
 * {@code DodgeRoll} and {@code AccuracyRoll}. Everything it reads is already persisted per
 * player, so the score is DERIVED on read rather than stored - there is no season total in NBT
 * to drift out of step with the values it was computed from, and no new save field to migrate.
 *
 * <h2>Why these inputs and not the obvious ones</h2>
 *
 * <p>Every component is <b>monotonic</b>: it only ever goes up. That rules out two candidates
 * that look ideal and are traps.
 *
 * <ul>
 *   <li><b>Emeralds are excluded.</b> They are a balance, not an achievement - spending them is
 *       the entire point. A board that counted them would drop a player's rank every time they
 *       bought something, which reads as a bug and punishes playing the game.</li>
 *   <li><b>Progression stat points are excluded</b> for the same reason: respeccing would move
 *       a player's season rank without them accomplishing anything either way.</li>
 *   <li><b>{@code highestInfiniteScore} is excluded</b>, which is the subtle one. Chapter
 *       rotation zeroes it for every player on the server, so a season board built on it would
 *       reset the entire leaderboard to zero the moment a chapter turned over - the one thing a
 *       season score, which outlives chapters, must not do. {@code allTimeInfiniteScore} is the
 *       same measurement kept past the boundary, so it is used instead.</li>
 * </ul>
 *
 * <p><b>What {@code allTimeInfiniteScore} actually is:</b> a player's BEST SINGLE RUN, not a
 * running total - {@code InfiniteRunManager} raises it only when a run beats it. Ten runs of
 * fifty leave it at fifty. So it rewards one great run rather than sustained play, which is
 * precisely why {@code chapterPlacementPoints} and {@code chaptersPlaced} are in the score
 * beside it: those accrue every chapter and are what reward turning up repeatedly.
 *
 * <p>It is also copied onto every participant of a run, not just the host, so a player carried
 * by a strong party scores what that party scored. That is existing behaviour and the season
 * board inherits it rather than deciding it.
 *
 * <p>The two halves are kept separate all the way to the display so a board can show WHY someone
 * is where they are. A player who never touches infinite mode can still climb by taking their
 * island deep, and one who never leaves infinite mode can climb without an island - which is the
 * point of adding them together rather than ranking on either alone.
 */
public final class SeasonScore {

    private SeasonScore() {}

    /**
     * The raw per-player values a score is built from, all read straight off stored data.
     *
     * @param allTimeInfiniteScore  BEST single infinite run ever, not a running total. Survives
     *                              chapter rotation
     * @param chapterPlacementPoints points earned by placing on chapter boards
     * @param chaptersPlaced        how many chapters they finished ranked
     * @param highestBiomeUnlocked  campaign depth, starts at 1
     * @param biomesDiscovered      how many distinct biomes they have found
     * @param ngPlusLevel           how many times they have gone around again
     * @param raidDefeated          whether they have beaten a raid boss
     */
    public record Inputs(int allTimeInfiniteScore, int chapterPlacementPoints, int chaptersPlaced,
                         int highestBiomeUnlocked, int biomesDiscovered, int ngPlusLevel,
                         boolean raidDefeated) {}

    /**
     * What each input is worth.
     *
     * <p>Deliberately coarse round numbers. These are a balance knob rather than a formula, and
     * a server owner retuning them should be able to reason about the result without algebra.
     */
    public record Weights(int perInfinitePoint, int perPlacementPoint, int perChapterPlaced,
                          int perBiomeDepth, int perBiomeDiscovered, int perNgPlusLevel,
                          int raidBonus) {

        /**
         * A starting balance where a deep island and an active infinite-mode season are worth
         * roughly the same, so neither half alone dominates the board.
         */
        public static final Weights DEFAULT =
            new Weights(1, 10, 5, 100, 25, 250, 100);
    }

    /**
     * A scored player, split into its halves.
     *
     * <p>{@code long} because the components multiply stored ints by weights: a stored int near
     * its ceiling times a weight of 100 overflows an int, and nothing clamps what is stored.
     */
    public record Breakdown(long chapterScore, long islandScore) {
        public long total() {
            return chapterScore + islandScore;
        }
    }

    /** Score {@code in} under {@code weights}. */
    public static Breakdown score(Inputs in, Weights weights) {
        if (in == null) return new Breakdown(0, 0);
        Weights w = weights != null ? weights : Weights.DEFAULT;

        long chapter = (long) nonNegative(in.allTimeInfiniteScore()) * w.perInfinitePoint()
            + (long) nonNegative(in.chapterPlacementPoints()) * w.perPlacementPoint()
            + (long) nonNegative(in.chaptersPlaced()) * w.perChapterPlaced();

        // highestBiomeUnlocked starts at 1 for a player who has done nothing at all, so the
        // first biome is worth zero rather than handing out points for existing.
        long island = (long) Math.max(0, in.highestBiomeUnlocked() - 1) * w.perBiomeDepth()
            + (long) nonNegative(in.biomesDiscovered()) * w.perBiomeDiscovered()
            + (long) nonNegative(in.ngPlusLevel()) * w.perNgPlusLevel()
            + (in.raidDefeated() ? w.raidBonus() : 0);

        return new Breakdown(chapter, island);
    }

    /** Score under {@link Weights#DEFAULT}. */
    public static Breakdown score(Inputs in) {
        return score(in, Weights.DEFAULT);
    }

    /**
     * Entries in a comma-separated list, ignoring blanks.
     *
     * <p>{@code discoveredBiomes} is stored as one comma-joined string, and an empty string
     * splits to a single empty element - which would credit a player who has discovered nothing
     * with one discovery.
     */
    public static int countCsv(String csv) {
        if (csv == null || csv.isBlank()) return 0;
        int n = 0;
        for (String part : csv.split(",")) {
            if (!part.isBlank()) n++;
        }
        return n;
    }

    /** Clamp a stored value that should never have been negative. */
    private static int nonNegative(int value) {
        return Math.max(0, value);
    }
}
