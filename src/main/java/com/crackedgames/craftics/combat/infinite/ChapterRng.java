package com.crackedgames.craftics.combat.infinite;

import java.util.Random;

/**
 * Derives every reproducible roll in an infinite run from the chapter seed.
 *
 * <p>A chapter is a window of time during which the whole server plays the SAME
 * infinite mode: the same biome order, the same arenas, the same enemy rosters, the
 * same loot. That is what makes the chapter leaderboard a comparison of skill rather
 * than of luck. When the chapter rotates, a fresh seed rolls and everything changes at
 * once for everybody.
 *
 * <p><b>Content only, never combat.</b> This seeds what a run PRESENTS to the player
 * before they act. It deliberately does not seed crit rolls, proc chances or AI target
 * choice. Combat randomness is consumed in an order set by player actions, so two
 * players sharing a seed desync on their first differing move and every roll after that
 * differs regardless. Seeding it would cost the whole codebase and buy nothing.
 *
 * <p><b>What must never enter the derivation:</b> wall-clock time, host UUID, party
 * composition, or arena origin coordinates. Any of those and two players stop seeing
 * the same run, which is the entire point. The inputs are the chapter seed plus the
 * player's POSITION IN THE RUN, nothing else.
 */
public final class ChapterRng {

    private ChapterRng() {}

    // Salts keep independent rolls at the same run position from colliding. Without
    // them the arena layout and the loot roll would derive from identical inputs and
    // therefore be the same number, correlating two things that should be independent.
    public static final int SALT_LEVEL = 1;
    public static final int SALT_ARENA = 2;
    public static final int SALT_LOOT = 3;
    public static final int SALT_BOSS = 4;
    public static final int SALT_BIOME = 5;
    public static final int SALT_EVENT = 6;
    public static final int SALT_TRADER = 7;
    /** Which between-level event fires, if any. Deliberately NOT SALT_EVENT: that one
     *  keys an event's OUTCOME, and sharing a salt would tie the pick to its own reward
     *  (e.g. the vault that spawns would always be the vault that pays best). */
    public static final int SALT_EVENT_PICK = 8;

    /** Fractional part of the golden ratio in 64 bits: the standard odd increment for
     *  this mixer, chosen so successive inputs land far apart in the output space. */
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    /**
     * The seed for one reproducible roll.
     *
     * @param chapterSeed the current chapter's seed
     * @param salt        which KIND of roll this is, from the SALT_ constants
     * @param inputs      the player's position in the run, most significant first
     *                    (typically biomes cleared, then level index within the biome)
     */
    public static long derive(long chapterSeed, int salt, long... inputs) {
        long h = mix64(chapterSeed + GOLDEN);
        h = mix64(h ^ (salt * GOLDEN));
        for (long value : inputs) {
            h = mix64(h ^ (value * GOLDEN));
        }
        return h;
    }

    /** A {@link Random} seeded by {@link #derive}. Callers get their own instance, so
     *  there is no shared stream to keep in step and no ordering coupling between the
     *  systems that use this. */
    public static Random random(long chapterSeed, int salt, long... inputs) {
        return new Random(derive(chapterSeed, salt, inputs));
    }

    /**
     * SplitMix64's finalizer. A plain XOR of the inputs (what the old nanoTime seeds
     * did) leaves neighbouring levels sharing most of their bits, which showed up
     * in-game as adjacent arenas looking alike. This avalanches every input bit across
     * the whole output.
     */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
