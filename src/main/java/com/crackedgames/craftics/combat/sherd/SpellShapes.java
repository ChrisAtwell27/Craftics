package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The shapes a spell's staging is drawn in.
 *
 * <p>Distances are counted the way the spells themselves count them: in steps, not diagonals.
 * A sherd that hits "everything within 2 tiles" hits a diamond, so the wave that shows it has
 * to be a diamond too, or the animation washes over enemies the spell never touched.
 *
 * <p>Pure geometry. Whether a tile is inside the arena, or is floor at all, is for whoever
 * draws on it to decide.
 */
public final class SpellShapes {

    private SpellShapes() {}

    /** Every tile exactly {@code steps} away from {@code centre}. The centre itself for 0. */
    public static List<GridPos> ring(GridPos centre, int steps) {
        List<GridPos> tiles = new ArrayList<>();
        if (steps <= 0) {
            tiles.add(centre);
            return tiles;
        }
        for (int dx = -steps; dx <= steps; dx++) {
            int dz = steps - Math.abs(dx);
            tiles.add(new GridPos(centre.x() + dx, centre.z() + dz));
            if (dz != 0) tiles.add(new GridPos(centre.x() + dx, centre.z() - dz));
        }
        return tiles;
    }

    /** Every tile within {@code steps} of {@code centre}, the centre included, nearest first. */
    public static List<GridPos> disc(GridPos centre, int steps) {
        List<GridPos> tiles = new ArrayList<>();
        for (int d = 0; d <= Math.max(0, steps); d++) tiles.addAll(ring(centre, d));
        return tiles;
    }

    /**
     * The tiles a straight line crosses going from {@code from} to {@code to}, in order,
     * without either end. Empty when the two are the same tile or side by side.
     */
    public static List<GridPos> between(GridPos from, GridPos to) {
        List<GridPos> tiles = new ArrayList<>();
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        for (int i = 1; i < steps; i++) {
            GridPos tile = new GridPos(
                from.x() + (int) Math.round(dx * (double) i / steps),
                from.z() + (int) Math.round(dz * (double) i / steps));
            if (tiles.isEmpty() || !tiles.get(tiles.size() - 1).equals(tile)) tiles.add(tile);
        }
        return tiles;
    }
}
