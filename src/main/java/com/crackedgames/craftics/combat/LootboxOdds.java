package com.crackedgames.craftics.combat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Ported from the CrackedGamesLobbyPlugin's {@code WeightedRoll} and {@code Odds}: pick
 * something in proportion to its weight, and compute the exact same percentage for display -
 * the roll and the number a player is shown can never drift apart because they are the same
 * arithmetic.
 *
 * <p>Kept generic and free of any Minecraft type so it can be unit tested without a bootstrap.
 * {@link LootboxManager} uses this for the two-decimal odds it discloses per reward.
 */
public final class LootboxOdds {
    private LootboxOdds() {}

    public interface Weighted {
        int weight();
    }

    /**
     * Picks one entry in proportion to its weight. Empty only when every weight is zero or
     * negative (or the list is empty) - the caller must treat that as nothing winnable.
     */
    public static <T extends Weighted> Optional<T> pick(List<T> entries, Random random) {
        int total = 0;
        for (T e : entries) total += Math.max(0, e.weight());
        if (total <= 0) return Optional.empty();

        int roll = random.nextInt(total);
        for (T e : entries) {
            roll -= Math.max(0, e.weight());
            if (roll < 0) return Optional.of(e);
        }
        // Unreachable while the weights above are the weights summed, but returning the last
        // entry beats returning nothing if that ever stops being true.
        return Optional.of(entries.get(entries.size() - 1));
    }

    /** Percentage from 0 to 100. Zero when the entry or the pool has no weight. */
    public static double chanceOf(int weight, int totalWeight) {
        if (totalWeight <= 0 || weight <= 0) return 0.0;
        return (weight * 100.0) / totalWeight;
    }

    /** Always two decimals, so a column of odds lines up. */
    public static String format(double percentage) {
        return String.format(Locale.US, "%.2f%%", percentage);
    }

    public static String formatChance(int weight, int totalWeight) {
        return format(chanceOf(weight, totalWeight));
    }
}
