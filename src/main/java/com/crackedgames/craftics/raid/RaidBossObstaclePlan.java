package com.crackedgames.craftics.raid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Decides WHERE a raid boss's authored obstacles go. Pure maths over plain integer cells:
 * no Minecraft types, so the clustering is unit-testable. Task 11 turns the resulting
 * placements into real tiles and blocks on a built arena.
 *
 * <p>{@link Cell} exists rather than reusing GridPos because GridPos imports BlockPos,
 * which would drag Minecraft onto the unit-test classpath.
 *
 * <p>Placement is scatter-with-clustering: each obstacle rolls a count inside its range,
 * then seeds that many blobs, each grown to {@code cluster} tiles by walking outward
 * through free neighbours. A blob that runs out of room stops early rather than
 * searching forever, and the whole plan is capped by the free set, so asking for more
 * obstacles than the arena has floor simply fills the floor.
 */
public final class RaidBossObstaclePlan {
    private RaidBossObstaclePlan() {}

    public record Cell(int x, int z) {}

    public record Placement(Cell cell, String tileType, String blockId) {}

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static List<Placement> plan(List<RaidBossObstacle> obstacles,
                                       Set<Cell> freeCells, Random rng) {
        List<Placement> out = new ArrayList<>();
        if (obstacles == null || obstacles.isEmpty() || freeCells == null || freeCells.isEmpty()) {
            return out;
        }

        // Sorted then shuffled: a HashSet's iteration order is not specified, so sorting
        // first is what makes a given seed reproduce the same plan.
        List<Cell> available = new ArrayList<>(freeCells);
        available.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z));
        java.util.Collections.shuffle(available, rng);

        Set<Cell> taken = new HashSet<>();
        int cursor = 0;

        for (RaidBossObstacle obstacle : obstacles) {
            int span = Math.max(1, obstacle.maxCount() - obstacle.minCount() + 1);
            int count = obstacle.minCount() + rng.nextInt(span);
            for (int i = 0; i < count; i++) {
                // Find the next untaken seed cell.
                while (cursor < available.size() && taken.contains(available.get(cursor))) cursor++;
                if (cursor >= available.size()) return out; // arena full
                Cell seed = available.get(cursor);
                growBlob(seed, obstacle, freeCells, taken, out);
            }
        }
        return out;
    }

    /** Claim the seed, then walk outward through free neighbours until the blob is full. */
    private static void growBlob(Cell seed, RaidBossObstacle obstacle, Set<Cell> freeCells,
                                 Set<Cell> taken, List<Placement> out) {
        List<Cell> frontier = new ArrayList<>();
        taken.add(seed);
        out.add(new Placement(seed, obstacle.tileType(), obstacle.blockId()));
        frontier.add(seed);

        int placed = 1;
        int target = Math.max(1, obstacle.cluster());
        while (placed < target && !frontier.isEmpty()) {
            Cell from = frontier.remove(0);
            for (int[] d : NEIGHBOURS) {
                if (placed >= target) break;
                Cell next = new Cell(from.x() + d[0], from.z() + d[1]);
                if (!freeCells.contains(next) || taken.contains(next)) continue;
                taken.add(next);
                out.add(new Placement(next, obstacle.tileType(), obstacle.blockId()));
                frontier.add(next);
                placed++;
            }
        }
    }
}
