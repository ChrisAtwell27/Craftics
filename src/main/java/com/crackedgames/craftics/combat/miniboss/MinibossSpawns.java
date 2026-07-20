package com.crackedgames.craftics.combat.miniboss;

import com.crackedgames.craftics.core.GridPos;

import java.util.List;
import java.util.Random;

/**
 * Shared spawn-tile pickers for miniboss mechanics. Every mechanic scatters its opening
 * enemies (and some scatter per-round adds) across the arena while avoiding the player start
 * and tiles already taken; a few bias the roll toward a point (the elite near center). This
 * is the one copy of that logic - each mechanic used to carry its own {@code findOpenSpawn}
 * plus {@code tooClose}/{@code clamp} helpers.
 *
 * <p>Pure grid math: no Minecraft types, no arena/world access, so it needs no bootstrap to
 * unit-test. Callers pass the arena's width/height and the set of already-used tiles (which
 * they seed with the player-start tile {@code (width/2, 0)} so nothing spawns on top of the
 * player).
 */
public final class MinibossSpawns {

    private MinibossSpawns() {}

    /** Manhattan gap below which two tiles are "too close" (avoids stacking spawns). */
    private static final int MIN_GAP = 2;

    /**
     * A random open tile in the spawn-safe rectangle (x in {@code [1, width-2]}, z in
     * {@code [2, height-3]}) that is not within {@link #MIN_GAP} of any {@code used} tile,
     * or {@code null} after 40 failed attempts. The z floor of 2 keeps spawns off the
     * player's front row.
     */
    public static GridPos findOpen(int width, int height, List<GridPos> used, Random rng) {
        for (int attempts = 0; attempts < 40; attempts++) {
            int x = 1 + rng.nextInt(Math.max(1, width - 2));
            int z = 2 + rng.nextInt(Math.max(1, height - 3));
            GridPos pos = new GridPos(x, z);
            if (!tooClose(pos, used)) return pos;
        }
        return null;
    }

    /**
     * Like {@link #findOpen} but first tries 20 rolls clustered within a 5x5 box around
     * {@code (biasX, biasZ)} (used to drop the elite near arena center), then falls back to
     * the full-arena scatter. {@code null} only if both passes fail.
     */
    public static GridPos findOpenBiased(int width, int height, List<GridPos> used, Random rng,
                                         int biasX, int biasZ) {
        for (int attempts = 0; attempts < 20; attempts++) {
            int x = clamp(biasX + rng.nextInt(5) - 2, 1, Math.max(1, width - 2));
            int z = clamp(biasZ + rng.nextInt(5) - 2, 2, Math.max(2, height - 2));
            GridPos pos = new GridPos(x, z);
            if (!tooClose(pos, used)) return pos;
        }
        return findOpen(width, height, used, rng);
    }

    /** True if {@code pos} is within {@link #MIN_GAP} Manhattan of any tile in {@code used}. */
    public static boolean tooClose(GridPos pos, List<GridPos> used) {
        for (GridPos u : used) {
            if (Math.abs(u.x() - pos.x()) + Math.abs(u.z() - pos.z()) < MIN_GAP) return true;
        }
        return false;
    }

    private static int clamp(int v, int lo, int hi) {
        if (hi < lo) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
