package com.crackedgames.craftics.level;

import java.util.ArrayList;
import java.util.List;

/**
 * Preset polygon shapes for the {@code /craftics build_arena} dev command.
 * Each preset returns a list of corner offsets {@code (dx, dz)} relative to a
 * center tile; the command applies those offsets at the caster's position and
 * drops {@code ArenaCornerBlock} markers at each one. Sizes are quoted in
 * "radius" - the half-width from the center to the polygon's outer edge - so
 * the playable interior is roughly {@code 2 × radius + 1} tiles across.
 *
 * <p>All corner lists are returned in a sensible walk order (corners adjacent
 * along the outline are adjacent in the list), but {@code ArenaBuilder} also
 * sorts by angle around the centroid at load time so out-of-order corners
 * still resolve correctly for convex shapes.
 */
public final class ArenaShapes {

    private ArenaShapes() {}

    /** A (dx, dz) offset from the polygon center, in tile units. */
    public record Offset(int dx, int dz) {}

    /** All preset names in the order they appear in command suggestions. */
    public static final String[] PRESET_NAMES = {
        "square", "diamond", "octagon", "hexagon", "plus", "cross",
        "l_shape", "t_shape"
    };

    /** Resolve a preset name to its corner offsets at the given radius.
     *  Returns {@code null} for unknown presets. */
    public static List<Offset> get(String name, int radius) {
        int r = Math.max(1, radius);
        return switch (name) {
            case "square"   -> square(r);
            case "diamond"  -> diamond(r);
            case "octagon"  -> octagon(r);
            case "hexagon"  -> hexagon(r);
            case "plus"     -> plus(r);
            case "cross"    -> plus(r); // alias
            case "l_shape", "l" -> lShape(r);
            case "t_shape", "t" -> tShape(r);
            default -> null;
        };
    }

    /** Standard 4-corner axis-aligned square. */
    private static List<Offset> square(int r) {
        List<Offset> out = new ArrayList<>(4);
        out.add(new Offset(-r, -r));
        out.add(new Offset( r, -r));
        out.add(new Offset( r,  r));
        out.add(new Offset(-r,  r));
        return out;
    }

    /** 4-point diamond (rotated square). Sits on its corners. */
    private static List<Offset> diamond(int r) {
        List<Offset> out = new ArrayList<>(4);
        out.add(new Offset( 0, -r));
        out.add(new Offset( r,  0));
        out.add(new Offset( 0,  r));
        out.add(new Offset(-r,  0));
        return out;
    }

    /** Regular octagon - square with chamfered corners. The "chamfer cut"
     *  is roughly {@code r/3} which keeps the shape readable at small radii. */
    private static List<Offset> octagon(int r) {
        int c = Math.max(1, r / 3);
        List<Offset> out = new ArrayList<>(8);
        // Clockwise from the top
        out.add(new Offset(-(r - c), -r));
        out.add(new Offset( (r - c), -r));
        out.add(new Offset( r,       -(r - c)));
        out.add(new Offset( r,        (r - c)));
        out.add(new Offset( (r - c),  r));
        out.add(new Offset(-(r - c),  r));
        out.add(new Offset(-r,        (r - c)));
        out.add(new Offset(-r,       -(r - c)));
        return out;
    }

    /** Regular hexagon, point-up orientation. Approximated on the integer grid;
     *  the side-lengths won't be perfectly equal at small radii but the shape
     *  reads as a hexagon. */
    private static List<Offset> hexagon(int r) {
        int half = Math.max(1, r / 2);
        List<Offset> out = new ArrayList<>(6);
        out.add(new Offset( 0,       -r));
        out.add(new Offset( r,       -half));
        out.add(new Offset( r,        half));
        out.add(new Offset( 0,        r));
        out.add(new Offset(-r,        half));
        out.add(new Offset(-r,       -half));
        return out;
    }

    /** Plus / cross sign. {@code r} is the outer radius; the arm width is
     *  {@code 2 × max(1, r/3) + 1}. 12 corners total. */
    private static List<Offset> plus(int r) {
        int arm = Math.max(1, r / 3);
        List<Offset> out = new ArrayList<>(12);
        // Clockwise from top-left of top arm
        out.add(new Offset(-arm, -r));
        out.add(new Offset( arm, -r));
        out.add(new Offset( arm, -arm));
        out.add(new Offset( r,   -arm));
        out.add(new Offset( r,    arm));
        out.add(new Offset( arm,  arm));
        out.add(new Offset( arm,  r));
        out.add(new Offset(-arm,  r));
        out.add(new Offset(-arm,  arm));
        out.add(new Offset(-r,    arm));
        out.add(new Offset(-r,   -arm));
        out.add(new Offset(-arm, -arm));
        return out;
    }

    /** L-shape - square minus the NE quadrant. 6 corners. */
    private static List<Offset> lShape(int r) {
        List<Offset> out = new ArrayList<>(6);
        out.add(new Offset(-r, -r));
        out.add(new Offset( 0, -r));
        out.add(new Offset( 0,  0));
        out.add(new Offset( r,  0));
        out.add(new Offset( r,  r));
        out.add(new Offset(-r,  r));
        return out;
    }

    /** T-shape - top horizontal bar with a vertical stem. 8 corners. */
    private static List<Offset> tShape(int r) {
        int stem = Math.max(1, r / 3);
        List<Offset> out = new ArrayList<>(8);
        out.add(new Offset(-r,   -r));
        out.add(new Offset( r,   -r));
        out.add(new Offset( r,   -r + Math.max(1, r / 2)));
        out.add(new Offset( stem,-r + Math.max(1, r / 2)));
        out.add(new Offset( stem, r));
        out.add(new Offset(-stem, r));
        out.add(new Offset(-stem,-r + Math.max(1, r / 2)));
        out.add(new Offset(-r,   -r + Math.max(1, r / 2)));
        return out;
    }
}
