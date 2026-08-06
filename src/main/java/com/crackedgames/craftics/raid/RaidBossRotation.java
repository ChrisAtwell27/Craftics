package com.crackedgames.craftics.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picks the day's raid boss: weighted random over everything that has not run
 * inside the no-repeat window.
 *
 * <p>When the window swallows the whole pool (fewer defined bosses than the
 * configured days) the exclusion is dropped for that roll and the caller is told
 * via {@link Pick#exclusionDropped()} so it can log the mismatch. Refusing to
 * pick would mean a silent day with no raid, which is worse than a repeat.
 */
public final class RaidBossRotation {
    private RaidBossRotation() {}

    public record Candidate(String id, int weight) {}

    public record Pick(String bossId, boolean exclusionDropped) {}

    public static Pick pick(List<Candidate> candidates, RaidBossState state,
                            long today, int noRepeatDays, Random rng) {
        if (candidates == null || candidates.isEmpty()) return new Pick(null, false);

        List<Candidate> eligible = new ArrayList<>();
        for (Candidate c : candidates) {
            if (!state.usedWithin(c.id(), today, noRepeatDays)) eligible.add(c);
        }
        boolean dropped = false;
        if (eligible.isEmpty()) {
            eligible = new ArrayList<>(candidates);
            dropped = true;
        }
        return new Pick(weightedPick(eligible, rng), dropped);
    }

    private static String weightedPick(List<Candidate> pool, Random rng) {
        int total = 0;
        for (Candidate c : pool) total += Math.max(1, c.weight());
        int roll = rng.nextInt(total);
        int cumulative = 0;
        for (Candidate c : pool) {
            cumulative += Math.max(1, c.weight());
            if (roll < cumulative) return c.id();
        }
        return pool.get(pool.size() - 1).id();
    }
}
