package com.crackedgames.craftics.level;

/**
 * Which height of a marked-out room is its floor.
 *
 * <p>A corner marker says where the floor is, give or take: it may sit in the floor, or be
 * hidden a block or two beneath it. Looking only at the marker's own column cannot tell
 * those apart from a third case, a marker laid in the floor with a wall standing on it,
 * because all three are a marker with solid blocks on top. A walled room read that way came
 * out with its floor at the top of its walls: the party stood on the battlements and the
 * room below them was a pit.
 *
 * <p>So the room is asked instead. The floor is the height most of the inside of the outline
 * can be stood on. The markers only settle a tie.
 *
 * <p>Kept free of Minecraft types so the rule stays unit-testable.
 */
public final class ArenaFloorLevel {

    private ArenaFloorLevel() {}

    /** How far above itself a marker can be pointing: buried this deep and no deeper. */
    public static final int MAX_BURIED = 2;

    /**
     * @param fromMarkers the height the markers' own columns point to
     * @param levels      every height worth considering
     * @param standable   for each of {@code levels}, how many tiles inside the outline have
     *                    ground there and room above it
     * @return the height with the most tiles to stand on; {@code fromMarkers} when nothing
     *         beats it
     */
    public static int pick(int fromMarkers, int[] levels, int[] standable) {
        int best = fromMarkers;
        int bestCount = 0;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == fromMarkers) bestCount = standable[i];
        }
        for (int i = 0; i < levels.length; i++) {
            if (standable[i] > bestCount) {
                bestCount = standable[i];
                best = levels[i];
            }
        }
        return best;
    }
}
