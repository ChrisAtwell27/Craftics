package com.crackedgames.craftics.raid;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RaidBossObstaclePlanTest {

    /** A w x h block of free cells. */
    private static Set<RaidBossObstaclePlan.Cell> grid(int w, int h) {
        Set<RaidBossObstaclePlan.Cell> cells = new HashSet<>();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) cells.add(new RaidBossObstaclePlan.Cell(x, z));
        }
        return cells;
    }

    @Test
    void noObstaclesPlansNothing() {
        assertTrue(RaidBossObstaclePlan.plan(List.of(), grid(10, 10), new Random(1)).isEmpty());
        assertTrue(RaidBossObstaclePlan.plan(null, grid(10, 10), new Random(1)).isEmpty());
    }

    @Test
    void noFreeCellsPlansNothing() {
        List<RaidBossObstacle> obstacles =
            List.of(new RaidBossObstacle("LAVA", "minecraft:lava", 5, 5, 1));
        assertTrue(RaidBossObstaclePlan.plan(obstacles, Set.of(), new Random(1)).isEmpty());
    }

    @Test
    void aFixedCountPlacesExactlyThatMany() {
        List<RaidBossObstacle> obstacles =
            List.of(new RaidBossObstacle("OBSTACLE", "minecraft:cactus", 7, 7, 1));
        List<RaidBossObstaclePlan.Placement> placements =
            RaidBossObstaclePlan.plan(obstacles, grid(10, 10), new Random(1));
        assertEquals(7, placements.size());
        assertTrue(placements.stream().allMatch(p -> p.tileType().equals("OBSTACLE")));
        assertTrue(placements.stream().allMatch(p -> p.blockId().equals("minecraft:cactus")));
    }

    @Test
    void aRangedCountStaysInsideItsRangeAcrossManySeeds() {
        List<RaidBossObstacle> obstacles =
            List.of(new RaidBossObstacle("WATER", "", 3, 6, 1));
        for (int seed = 0; seed < 40; seed++) {
            int n = RaidBossObstaclePlan.plan(obstacles, grid(12, 12), new Random(seed)).size();
            assertTrue(n >= 3 && n <= 6, "seed " + seed + " produced " + n);
        }
    }

    @Test
    void everyPlacementLandsOnAFreeCellAndNeverRepeats() {
        List<RaidBossObstacle> obstacles = List.of(
            new RaidBossObstacle("LAVA", "minecraft:lava", 4, 4, 3),
            new RaidBossObstacle("ICE", "minecraft:blue_ice", 6, 6, 2));
        Set<RaidBossObstaclePlan.Cell> free = grid(12, 12);
        List<RaidBossObstaclePlan.Placement> placements =
            RaidBossObstaclePlan.plan(obstacles, free, new Random(7));
        Set<RaidBossObstaclePlan.Cell> seen = new HashSet<>();
        for (RaidBossObstaclePlan.Placement p : placements) {
            assertTrue(free.contains(p.cell()), "placed outside the free set: " + p.cell());
            assertTrue(seen.add(p.cell()), "placed twice on " + p.cell());
        }
    }

    @Test
    void clusteringGrowsContiguousBlobs() {
        // One placement, cluster 5, so all five cells must form one connected blob.
        List<RaidBossObstacle> obstacles =
            List.of(new RaidBossObstacle("LAVA", "minecraft:lava", 1, 1, 5));
        List<RaidBossObstaclePlan.Placement> placements =
            RaidBossObstaclePlan.plan(obstacles, grid(12, 12), new Random(3));
        assertEquals(5, placements.size());

        Set<RaidBossObstaclePlan.Cell> blob = new HashSet<>();
        for (RaidBossObstaclePlan.Placement p : placements) blob.add(p.cell());

        // Flood fill from any member; every cell must be reachable through orthogonal steps.
        List<RaidBossObstaclePlan.Cell> queue = new ArrayList<>();
        Set<RaidBossObstaclePlan.Cell> reached = new HashSet<>();
        RaidBossObstaclePlan.Cell start = blob.iterator().next();
        queue.add(start);
        reached.add(start);
        while (!queue.isEmpty()) {
            RaidBossObstaclePlan.Cell c = queue.remove(queue.size() - 1);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                RaidBossObstaclePlan.Cell n =
                    new RaidBossObstaclePlan.Cell(c.x() + d[0], c.z() + d[1]);
                if (blob.contains(n) && reached.add(n)) queue.add(n);
            }
        }
        assertEquals(blob.size(), reached.size(), "cluster is not contiguous");
    }

    @Test
    void demandBeyondTheFreeSpaceIsTruncatedRatherThanLooping() {
        // 9 free cells, 50 requested: the planner must stop, not spin.
        List<RaidBossObstacle> obstacles =
            List.of(new RaidBossObstacle("OBSTACLE", "minecraft:stone", 50, 50, 1));
        List<RaidBossObstaclePlan.Placement> placements =
            RaidBossObstaclePlan.plan(obstacles, grid(3, 3), new Random(1));
        assertEquals(9, placements.size());
    }

    @Test
    void planningIsDeterministicForAGivenSeed() {
        List<RaidBossObstacle> obstacles =
            List.of(new RaidBossObstacle("LAVA", "minecraft:lava", 3, 6, 3));
        assertEquals(
            RaidBossObstaclePlan.plan(obstacles, grid(10, 10), new Random(42)),
            RaidBossObstaclePlan.plan(obstacles, grid(10, 10), new Random(42)));
    }
}
