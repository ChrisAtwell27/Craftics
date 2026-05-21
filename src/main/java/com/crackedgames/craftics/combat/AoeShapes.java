package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure geometry for attack area-of-effect footprints. Each method returns the
 * set of grid tiles an attack covers, given the attacker (player) tile and the
 * clicked target tile. No Minecraft / arena dependencies, so this is fully
 * unit-testable and shared by the damage handlers (which then filter by
 * occupancy / arena bounds).
 *
 * <p>Directional shapes (line, chop, cone, thrust) derive their facing from the
 * player -> target vector, snapped to the nearest cardinal direction. The
 * caller is responsible for clipping to arena bounds and checking walkability;
 * these methods may return tiles outside the arena.
 *
 * <p>Geometry only — damage rules (full / half / falloff) stay in the weapon
 * handlers. Each shape has a paired use in {@code VanillaWeapons} /
 * {@code CombatManager}; keep them in sync.
 */
public final class AoeShapes {

    private AoeShapes() {}

    /** Cardinal unit step from {@code from} toward {@code to}, snapped to one
     *  axis (the dominant one). Returns {0,0} only when from == to. */
    public static int[] cardinalDir(GridPos from, GridPos to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (dx == 0 && dz == 0) return new int[]{0, 0};
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[]{Integer.signum(dx), 0};
        }
        return new int[]{0, Integer.signum(dz)};
    }

    /** 3x3 square centered on {@code center} (mace slam, brain coral splash). */
    public static List<GridPos> slam3x3(GridPos center) {
        List<GridPos> out = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.add(new GridPos(center.x() + dx, center.z() + dz));
            }
        }
        return out;
    }

    /** Plus / cross: the center tile plus the 4 cardinal neighbors (tube coral). */
    public static List<GridPos> plus(GridPos center) {
        List<GridPos> out = new ArrayList<>(5);
        out.add(center);
        out.add(new GridPos(center.x() + 1, center.z()));
        out.add(new GridPos(center.x() - 1, center.z()));
        out.add(new GridPos(center.x(), center.z() + 1));
        out.add(new GridPos(center.x(), center.z() - 1));
        return out;
    }

    /**
     * Sweeping-Edge whirlwind. The shape scales with enchant level:
     * <ul>
     *   <li>Lv1 = 3-wide chop: the target tile plus the two tiles perpendicular
     *       to the attack direction.</li>
     *   <li>Lv2 = 5-wide arc: the chop plus the two front diagonals (one row
     *       deeper toward the target's far side).</li>
     *   <li>Lv3 = full 8-tile ring around the PLAYER (360 spin).</li>
     * </ul>
     */
    public static List<GridPos> sweepingEdge(GridPos player, GridPos target, int level) {
        if (level >= 3) {
            // Full ring around the player (8 surrounding tiles, excludes player).
            List<GridPos> out = new ArrayList<>(8);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    out.add(new GridPos(player.x() + dx, player.z() + dz));
                }
            }
            return out;
        }

        int[] dir = cardinalDir(player, target);
        int dx = dir[0];
        int dz = dir[1];
        // Perpendicular axis to the attack direction.
        int perpX = dz;
        int perpZ = dx;

        Set<GridPos> out = new LinkedHashSet<>();
        out.add(target);
        out.add(new GridPos(target.x() + perpX, target.z() + perpZ));
        out.add(new GridPos(target.x() - perpX, target.z() - perpZ));

        if (level >= 2) {
            // Widen to 5 across: the two outer perpendicular tiles.
            out.add(new GridPos(target.x() + 2 * perpX, target.z() + 2 * perpZ));
            out.add(new GridPos(target.x() - 2 * perpX, target.z() - 2 * perpZ));
        }
        return new ArrayList<>(out);
    }

    /**
     * Cone in the attack direction, widening by one tile each row out to
     * {@code depth}. Anchored on the player so row 1 is the target row. Used by
     * Fire Aspect and Fire coral.
     */
    public static List<GridPos> cone(GridPos player, GridPos target, int depth) {
        int[] dir = cardinalDir(player, target);
        int dx = dir[0];
        int dz = dir[1];
        if (dx == 0 && dz == 0) return List.of(target);
        int perpX = dz;
        int perpZ = dx;

        List<GridPos> out = new ArrayList<>();
        for (int d = 1; d <= depth; d++) {
            int cx = player.x() + dx * d;
            int cz = player.z() + dz * d;
            for (int w = -d; w <= d; w++) {
                out.add(new GridPos(cx + perpX * w, cz + perpZ * w));
            }
        }
        return out;
    }

    /**
     * Wider cone: like {@link #cone} but each row also reaches one tile further
     * out to the sides AND adds the two flanking tiles beside the player's
     * first row, so it visibly fans onto the sides. Used by Fire Aspect when
     * paired with Sweeping Edge II.
     */
    public static List<GridPos> coneWide(GridPos player, GridPos target, int depth) {
        int[] dir = cardinalDir(player, target);
        int dx = dir[0];
        int dz = dir[1];
        if (dx == 0 && dz == 0) return List.of(target);
        int perpX = dz;
        int perpZ = dx;

        Set<GridPos> out = new LinkedHashSet<>();
        for (int d = 1; d <= depth; d++) {
            int cx = player.x() + dx * d;
            int cz = player.z() + dz * d;
            // Each row is 1 wider on each side than the standard cone.
            int half = d + 1;
            for (int w = -half; w <= half; w++) {
                out.add(new GridPos(cx + perpX * w, cz + perpZ * w));
            }
        }
        // Flank the player's own sides so the fire wraps a bit onto the sides.
        out.add(new GridPos(player.x() + perpX, player.z() + perpZ));
        out.add(new GridPos(player.x() - perpX, player.z() - perpZ));
        return new ArrayList<>(out);
    }

    /**
     * The perimeter ring of tiles at exactly Chebyshev {@code radius} from
     * {@code center} (a hollow square outline). radius 1 = the 8 surrounding
     * tiles; radius 2 = the 16-tile outline two tiles out. Used by the Fire
     * Aspect + Sweeping Edge III outer fire ring.
     */
    public static List<GridPos> ring(GridPos center, int radius) {
        List<GridPos> out = new ArrayList<>();
        if (radius <= 0) { out.add(center); return out; }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                    out.add(new GridPos(center.x() + dx, center.z() + dz));
                }
            }
        }
        return out;
    }

    /**
     * Fire Aspect burn footprint, scaled by Sweeping Edge level when both
     * enchants are on the sword (per design):
     * <ul>
     *   <li>no/level-1 sweep: forward {@link #cone} (depth = fireAspect + 1).</li>
     *   <li>sweep II: {@link #coneWide} so the fire fans onto the sides too.</li>
     *   <li>sweep III: the SE3 ring around the player burns, PLUS an outer
     *       fire ring two tiles out ({@code ring(player, 2)}).</li>
     * </ul>
     */
    public static List<GridPos> fireAspectShape(GridPos player, GridPos target,
                                                int fireAspect, int sweepLevel) {
        int depth = Math.max(1, fireAspect + 1);
        if (sweepLevel >= 3) {
            Set<GridPos> out = new LinkedHashSet<>(ring(player, 1));
            out.addAll(ring(player, 2));
            return new ArrayList<>(out);
        }
        if (sweepLevel == 2) {
            return coneWide(player, target, depth);
        }
        return cone(player, target, depth);
    }

    /**
     * Straight line from the tile just past the target, continuing in the
     * attack direction for {@code length} tiles (crossbow pierce, knockback
     * shockwave, bubble coral push). Does not include the target tile itself.
     */
    public static List<GridPos> lineBehind(GridPos player, GridPos target, int length) {
        int[] dir = cardinalDir(player, target);
        int dx = dir[0];
        int dz = dir[1];
        List<GridPos> out = new ArrayList<>(length);
        if (dx == 0 && dz == 0) return out;
        GridPos cur = target;
        for (int i = 0; i < length; i++) {
            cur = new GridPos(cur.x() + dx, cur.z() + dz);
            out.add(cur);
        }
        return out;
    }

    /**
     * Line outward from the player through the target and onward for
     * {@code length} tiles total (including the target). Used by bubble coral.
     */
    public static List<GridPos> lineOutward(GridPos player, GridPos target, int length) {
        int[] dir = cardinalDir(player, target);
        int dx = dir[0];
        int dz = dir[1];
        List<GridPos> out = new ArrayList<>(length);
        if (dx == 0 && dz == 0) { out.add(target); return out; }
        GridPos cur = target;
        out.add(cur);
        for (int i = 1; i < length; i++) {
            cur = new GridPos(cur.x() + dx, cur.z() + dz);
            out.add(cur);
        }
        return out;
    }

    /** Single target plus the one tile directly behind it (horn coral pierce). */
    public static List<GridPos> pierceBehind(GridPos player, GridPos target) {
        List<GridPos> out = new ArrayList<>(2);
        out.add(target);
        int[] dir = cardinalDir(player, target);
        if (dir[0] != 0 || dir[1] != 0) {
            out.add(new GridPos(target.x() + dir[0], target.z() + dir[1]));
        }
        return out;
    }

    /**
     * Resolve a shape's tiles into the distinct live, non-ally enemies standing
     * on them, excluding {@code exclude} (usually the primary target, already
     * damaged separately). Each entity is returned once even if it occupies
     * multiple tiles. Tiles outside the arena are skipped.
     */
    public static List<CombatEntity> enemiesOn(GridArena arena, List<GridPos> tiles, CombatEntity exclude) {
        List<CombatEntity> out = new ArrayList<>();
        Set<CombatEntity> seen = new LinkedHashSet<>();
        for (GridPos tile : tiles) {
            if (!arena.isInBounds(tile)) continue;
            CombatEntity occ = arena.getOccupant(tile);
            if (occ == null || !occ.isAlive() || occ.isAlly()) continue;
            if (occ == exclude) continue;
            if (seen.add(occ)) out.add(occ);
        }
        return out;
    }
}
