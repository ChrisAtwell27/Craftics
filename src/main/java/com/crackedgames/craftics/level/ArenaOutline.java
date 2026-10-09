package com.crackedgames.craftics.level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape a set of corner markers draws, and the floor inside it.
 *
 * <p>An arena with three or more {@code craftics:arena_corner} markers is whatever outline
 * they trace. Getting from a handful of loose points to that outline, and from the outline to
 * the tiles inside it, is all here.
 *
 * <p>Three things this has to get right, each of which a built room has got wrong:
 * <ul>
 *   <li><b>Markers nobody needed.</b> A room made of cells gets a marker at every cell corner,
 *       including the ones along a straight wall and the ones in the middle where four cells
 *       meet. Those are not corners of the outline, and a strict reading of "every marker is a
 *       corner" has no outline to give. {@link #trace} sets them aside.</li>
 *   <li><b>Edges that face the middle.</b> On an L or a T, some walls look inward at the
 *       room's own centre. {@link #filled} counts a tile on the outline as inside whichever
 *       way its edge faces.</li>
 *   <li><b>Shapes that are not square-cornered.</b> A diamond or an octagon has no rows and
 *       columns to pair up, and is taken in order round its centre, which is right for any
 *       shape that bulges outward everywhere.</li>
 * </ul>
 *
 * <p>Kept free of Minecraft types so the rule stays unit-testable.
 */
public final class ArenaOutline {

    private ArenaOutline() {}

    /** A marker's column: where it is on the ground, whatever its height. */
    public record Point(int x, int z) {}

    /**
     * An outline, in walking order.
     *
     * @param ring  the corners of the shape, each joined to the next and the last to the first
     * @param spare markers that turned out not to be corners: on a straight edge, or inside
     */
    public record Traced(List<Point> ring, List<Point> spare) {}

    /** More markers than this set aside and the outline is a guess, not a reading. */
    static final int MAX_SPARE = 4;

    /** Past this many markers the search for spares is not tried: it grows too fast. */
    static final int MAX_SEARCHED = 40;

    /**
     * Read an outline off its markers.
     *
     * <p>Square-cornered shapes are rebuilt exactly, every marker a corner. Failing that, the
     * fewest markers are set aside that leave a square-cornered shape with all of them still
     * on it or inside it. Failing that too, the markers are taken in order round their centre.
     */
    public static Traced trace(List<Point> markers) {
        List<Point> verts = distinct(markers);

        List<Point> exact = rectilinear(verts);
        if (exact != null) return new Traced(exact, List.of());

        Traced trimmed = withoutSpares(verts);
        if (trimmed != null) return trimmed;

        return new Traced(roundCentre(verts), List.of());
    }

    private static List<Point> distinct(List<Point> markers) {
        // Two markers stacked in one column are one corner.
        java.util.LinkedHashSet<Point> unique = new java.util.LinkedHashSet<>(markers);
        return new ArrayList<>(unique);
    }

    /**
     * The one square-cornered ring through every point, or null when there is none.
     *
     * <p>In such a ring each corner joins exactly one upright edge and one level edge, and
     * those are found by pairing the corners off down each column and along each row. Null
     * for an odd count anywhere, for pairings that close before every corner is used, and for
     * points that make two rings instead of one.
     */
    static List<Point> rectilinear(List<Point> verts) {
        int n = verts.size();
        if (n < 4 || (n & 1) != 0) return null;

        Map<Integer, List<Integer>> byX = new HashMap<>();
        Map<Integer, List<Integer>> byZ = new HashMap<>();
        for (int i = 0; i < n; i++) {
            byX.computeIfAbsent(verts.get(i).x(), k -> new ArrayList<>()).add(i);
            byZ.computeIfAbsent(verts.get(i).z(), k -> new ArrayList<>()).add(i);
        }

        int[] upright = new int[n];
        int[] level = new int[n];
        java.util.Arrays.fill(upright, -1);
        java.util.Arrays.fill(level, -1);
        for (List<Integer> column : byX.values()) {
            if ((column.size() & 1) != 0) return null;
            column.sort(java.util.Comparator.comparingInt(i -> verts.get(i).z()));
            for (int k = 0; k + 1 < column.size(); k += 2) {
                upright[column.get(k)] = column.get(k + 1);
                upright[column.get(k + 1)] = column.get(k);
            }
        }
        for (List<Integer> row : byZ.values()) {
            if ((row.size() & 1) != 0) return null;
            row.sort(java.util.Comparator.comparingInt(i -> verts.get(i).x()));
            for (int k = 0; k + 1 < row.size(); k += 2) {
                level[row.get(k)] = row.get(k + 1);
                level[row.get(k + 1)] = row.get(k);
            }
        }

        List<Point> ring = new ArrayList<>(n);
        boolean[] seen = new boolean[n];
        int at = 0;
        boolean goUpright = true;
        for (int step = 0; step < n; step++) {
            if (seen[at]) return null;
            seen[at] = true;
            ring.add(verts.get(at));
            at = goUpright ? upright[at] : level[at];
            if (at < 0) return null;
            goUpright = !goUpright;
        }
        return at == 0 ? ring : null;
    }

    /**
     * The outline left when the fewest markers are set aside, or null when no small number
     * of them will do.
     *
     * <p>A marker may only be set aside if it ends up on the outline or inside it. That is
     * what makes it spare, and it is also what keeps this from inventing shapes: on a diamond
     * or an octagon every marker sticks out, so nothing can be dropped and the caller falls
     * through to {@link #roundCentre}. Of several outlines that set aside the same number,
     * the one enclosing the most ground wins, the first found if they tie.
     */
    private static Traced withoutSpares(List<Point> verts) {
        int n = verts.size();
        if (n > MAX_SEARCHED) return null;
        for (int count = 1; count <= MAX_SPARE && n - count >= 4; count++) {
            // A square-cornered ring has an even number of corners.
            if (((n - count) & 1) != 0) continue;
            Traced best = null;
            long bestArea = -1;
            int[] pick = new int[count];
            for (int i = 0; i < count; i++) pick[i] = i;
            while (true) {
                Traced candidate = without(verts, pick);
                if (candidate != null) {
                    long area = twiceArea(candidate.ring());
                    if (area > bestArea) {
                        bestArea = area;
                        best = candidate;
                    }
                }
                if (!nextChoice(pick, n)) break;
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Step {@code pick} to the next way of choosing that many of {@code n}; false when done. */
    private static boolean nextChoice(int[] pick, int n) {
        int k = pick.length;
        int i = k - 1;
        while (i >= 0 && pick[i] == n - k + i) i--;
        if (i < 0) return false;
        pick[i]++;
        for (int j = i + 1; j < k; j++) pick[j] = pick[j - 1] + 1;
        return true;
    }

    private static Traced without(List<Point> verts, int[] pick) {
        List<Point> kept = new ArrayList<>(verts.size() - pick.length);
        List<Point> spare = new ArrayList<>(pick.length);
        int next = 0;
        for (int i = 0; i < verts.size(); i++) {
            if (next < pick.length && pick[next] == i) {
                spare.add(verts.get(i));
                next++;
            } else {
                kept.add(verts.get(i));
            }
        }
        List<Point> ring = rectilinear(kept);
        if (ring == null || crossesItself(ring)) return null;
        for (Point p : spare) {
            if (!contains(ring, p.x(), p.z())) return null;
        }
        return new Traced(ring, spare);
    }

    /** True when an upright edge of a square-cornered ring cuts through a level one. */
    private static boolean crossesItself(List<Point> ring) {
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            Point a = ring.get(i), b = ring.get((i + 1) % n);
            if (a.x() != b.x()) continue;
            int z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
            for (int j = 0; j < n; j++) {
                Point c = ring.get(j), d = ring.get((j + 1) % n);
                if (c.z() != d.z()) continue;
                int x0 = Math.min(c.x(), d.x()), x1 = Math.max(c.x(), d.x());
                if (x0 < a.x() && a.x() < x1 && z0 < c.z() && c.z() < z1) return true;
            }
        }
        return false;
    }

    private static long twiceArea(List<Point> ring) {
        long sum = 0;
        for (int i = 0; i < ring.size(); i++) {
            Point a = ring.get(i), b = ring.get((i + 1) % ring.size());
            sum += (long) a.x() * b.z() - (long) b.x() * a.z();
        }
        return Math.abs(sum);
    }

    /**
     * The markers in order round their centre. Right for any shape that bulges outward
     * everywhere; a shape with a dent in it can come out tangled, which is why the
     * square-cornered ones are never left to this.
     */
    private static List<Point> roundCentre(List<Point> verts) {
        double cx = 0, cz = 0;
        for (Point p : verts) { cx += p.x(); cz += p.z(); }
        final double centreX = cx / verts.size(), centreZ = cz / verts.size();
        List<Point> sorted = new ArrayList<>(verts);
        sorted.sort((a, b) -> Double.compare(
            Math.atan2(a.z() - centreZ, a.x() - centreX),
            Math.atan2(b.z() - centreZ, b.x() - centreX)));
        return sorted;
    }

    /** True when the tile is on the outline. */
    public static boolean onOutline(List<Point> ring, int x, int z) {
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            Point a = ring.get(i), b = ring.get((i + 1) % n);
            long cross = (long) (b.x() - a.x()) * (z - a.z()) - (long) (b.z() - a.z()) * (x - a.x());
            if (cross != 0) continue;
            if (x >= Math.min(a.x(), b.x()) && x <= Math.max(a.x(), b.x())
                && z >= Math.min(a.z(), b.z()) && z <= Math.max(a.z(), b.z())) {
                return true;
            }
        }
        return false;
    }

    /** True when the tile is on the outline or inside it. */
    public static boolean contains(List<Point> ring, int x, int z) {
        if (onOutline(ring, x, z)) return true;
        boolean inside = false;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point a = ring.get(i), b = ring.get(j);
            if ((a.z() > z) != (b.z() > z)
                && x < (double) (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x()) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Every tile on or inside the outline, over a grid that starts at {@code minX, minZ}.
     *
     * <p>A tile on the outline counts, on every edge alike. The earlier way of doing this
     * nudged the whole outline a hair away from its centre so that no edge fell exactly on a
     * row of tiles; an edge that faces the centre is nudged the wrong way by that, or not at
     * all, and its row of tiles dropped out, taking a strip of floor off the room beside it.
     */
    public static boolean[][] filled(List<Point> ring, int minX, int minZ, int width, int height) {
        boolean[][] filled = new boolean[width][height];
        for (int tx = 0; tx < width; tx++) {
            for (int tz = 0; tz < height; tz++) {
                filled[tx][tz] = contains(ring, minX + tx, minZ + tz);
            }
        }
        return filled;
    }

    /**
     * The floor: every filled tile with a filled tile on all four sides, so the outline
     * itself is a rim one tile wide that is never stood on.
     *
     * <p>An inward corner has filled tiles on all four sides and would be left as floor by
     * that rule alone, so the corners of the ring are taken off as well.
     */
    public static boolean[][] floor(boolean[][] filled, List<Point> ring, int minX, int minZ) {
        int width = filled.length;
        int height = width == 0 ? 0 : filled[0].length;
        boolean[][] floor = new boolean[width][height];
        for (int tx = 1; tx < width - 1; tx++) {
            for (int tz = 1; tz < height - 1; tz++) {
                floor[tx][tz] = filled[tx][tz]
                    && filled[tx - 1][tz] && filled[tx + 1][tz]
                    && filled[tx][tz - 1] && filled[tx][tz + 1];
            }
        }
        for (Point corner : ring) {
            int tx = corner.x() - minX, tz = corner.z() - minZ;
            if (tx >= 0 && tx < width && tz >= 0 && tz < height) floor[tx][tz] = false;
        }
        return floor;
    }
}
