package com.crackedgames.craftics.level;

/**
 * Which raised floors of a room can be walked on.
 *
 * <p>An arena's grid is flat, but a room need not be. A stair on the floor makes the block
 * it butts against an upper floor, and that has always been walkable. This carries the
 * same rule up a level at a time: a stair standing on a raised floor leads to the floor
 * above that one, which is walkable for as far as it runs. A wall nothing climbs to stays
 * a wall, however flat its top.
 *
 * <p>Every cell is one number. A positive {@code n} is the floor of tier {@code n}: one is
 * the ordinary raised floor, a block above the arena's own. A negative {@code -n} is a
 * flight of stairs climbing to tier {@code n}. Zero is anything else.
 *
 * <p>Kept free of Minecraft types so the rule stays unit-testable.
 */
public final class ArenaTiers {

    private ArenaTiers() {}

    /** The highest floor a room may stack and still be fought on. */
    public static final int MAX_TIER = 3;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * The upper floors and flights that something leads up to.
     *
     * @param have  what the room already walks on: its stairs and its first raised floor
     * @param could what each remaining wall would be if something did lead up to it, tier
     *              two and above
     * @return the cells of {@code could} that are reached, and zero everywhere else
     */
    public static int[][] reached(int[][] have, int[][] could) {
        int w = have.length;
        int h = w == 0 ? 0 : have[0].length;
        int[][] all = new int[w][h];
        for (int x = 0; x < w; x++) all[x] = have[x].clone();
        int[][] gained = new int[w][h];
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < h; z++) {
                    int cell = could[x][z];
                    if (Math.abs(cell) < 2 || all[x][z] != 0) continue;
                    if (!leadsTo(all, x, z, cell)) continue;
                    all[x][z] = cell;
                    gained[x][z] = cell;
                    changed = true;
                }
            }
        }
        return gained;
    }

    private static boolean leadsTo(int[][] all, int x, int z, int cell) {
        int tier = Math.abs(cell);
        for (int[] side : SIDES) {
            int nx = x + side[0], nz = z + side[1];
            if (nx < 0 || nx >= all.length || nz < 0 || nz >= all[0].length) continue;
            int beside = all[nx][nz];
            if (cell > 0) {
                // A floor: the flight that climbs to it, or more of the same floor.
                if (beside == -tier || beside == tier) return true;
            } else if (beside == tier - 1 || beside == -(tier - 1) || beside == tier) {
                // A flight: the floor it stands on, the flight below it, or the floor it serves.
                return true;
            }
        }
        return false;
    }
}
