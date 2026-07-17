package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Tidecaller's Tidal Wave geometry, extracted Minecraft-free so the sweep rules are
 * unit-testable without a bootstrap.
 *
 * <p>A wall of water spanning the FULL arena width, 3 tiles thick, that starts at one end
 * of the arena and marches {@link #STEP} tiles per turn until it exits the far side. It is
 * not aimed at the player and it does not follow them: it is a moving piece of terrain the
 * player has to get out of the way of, or get carried by. Anyone standing inside the band
 * when it advances is carried the same 3 tiles the wave moved.
 *
 * <p>This class owns only the int geometry: which tiles the band covers at step N, whether
 * the band has left the arena, and whether a given tile is inside it. The caller owns the
 * world (water blocks, forced movement, particles).
 *
 * <p>Coverage is deduped by construction - {@link #tilesAt} builds through a set, so a tile
 * appears exactly once no matter how the band overlaps itself at the arena edge. What the
 * telegraph paints and what the wave resolves are the same list from the same call, so the
 * two cannot drift apart.
 */
public final class SweepingWave {

    /** Tiles the wave advances per turn. Also the distance a caught player is carried. */
    public static final int STEP = 3;
    /** Thickness of the water band along the axis of travel. */
    public static final int THICKNESS = 3;

    /** True when the band travels along Z (the band spans the full X width). */
    private final boolean alongZ;
    /** Extent of the arena on the axis of travel. */
    private final int travelExtent;
    /** Extent of the arena on the axis the band spans. */
    private final int spanExtent;
    /** +1 when marching from index 0 upward, -1 when marching from the far end downward. */
    private final int direction;
    /** Leading edge position on the travel axis. Advances by STEP per turn. */
    private int front;

    private SweepingWave(boolean alongZ, int travelExtent, int spanExtent, int direction, int front) {
        this.alongZ = alongZ;
        this.travelExtent = travelExtent;
        this.spanExtent = spanExtent;
        this.direction = direction;
        this.front = front;
    }

    /**
     * Build the wave for an arena of the given size, given where the player stands.
     *
     * <p>Direction rule, chosen to be legible rather than clever:
     * <ul>
     *   <li><b>Axis</b>: the arena's LONG axis, so the wave gets the most tiles of runway
     *       and the player the most turns to read it. Ties (a square arena) go to Z.</li>
     *   <li><b>End</b>: the end FARTHER from the player. The wave always spawns across the
     *       arena from you and comes at you, which is the only reading that makes a
     *       full-width wall a threat you can plan around instead of a coin flip.</li>
     * </ul>
     * No randomness: the same arena and player position always produce the same sweep.
     */
    public static SweepingWave spawn(int width, int height, GridPos playerPos) {
        boolean alongZ = height >= width;
        int travelExtent = alongZ ? height : width;
        int spanExtent = alongZ ? width : height;
        int playerOnTravel = alongZ ? playerPos.z() : playerPos.x();

        // Start at the end farther from the player: the wave crosses the whole arena at them.
        int direction;
        int front;
        if (playerOnTravel * 2 >= travelExtent) {
            // Player sits in the far half, so spawn at index 0 and march upward.
            direction = 1;
            // The band trails BEHIND the leading edge, so a front of 0 puts the whole
            // 3-thick band on tiles 0..2 rather than hanging two tiles off the arena.
            front = THICKNESS - 1;
        } else {
            direction = -1;
            front = travelExtent - THICKNESS;
        }
        return new SweepingWave(alongZ, travelExtent, spanExtent, direction, front);
    }

    /** Exposed for tests and for the renderer: true when the band travels along Z. */
    public boolean isAlongZ() { return alongZ; }
    /** +1 or -1 along the travel axis. */
    public int getDirection() { return direction; }
    /** Current leading-edge index on the travel axis. */
    public int getFront() { return front; }

    /** Per-turn carry vector on X. Zero when the wave travels along Z. */
    public int carryDx() { return alongZ ? 0 : direction * STEP; }
    /** Per-turn carry vector on Z. Zero when the wave travels along X. */
    public int carryDz() { return alongZ ? direction * STEP : 0; }

    /**
     * The tiles the band covers right now: the full arena span, {@value #THICKNESS} thick,
     * trailing back from the leading edge. Clipped to the arena and deduped, so every tile
     * appears exactly once. This is both the paint list and the resolve list.
     */
    public List<GridPos> tiles() {
        Set<GridPos> covered = new LinkedHashSet<>();
        for (int t = 0; t < THICKNESS; t++) {
            int travel = front - direction * t;
            if (travel < 0 || travel >= travelExtent) continue;
            for (int s = 0; s < spanExtent; s++) {
                covered.add(alongZ ? new GridPos(s, travel) : new GridPos(travel, s));
            }
        }
        return new ArrayList<>(covered);
    }

    /** True when {@code pos} is inside the band as it stands now. */
    public boolean covers(GridPos pos) {
        int travel = alongZ ? pos.z() : pos.x();
        int span = alongZ ? pos.x() : pos.z();
        if (span < 0 || span >= spanExtent) return false;
        int behind = (travel - front) * -direction;
        return behind >= 0 && behind < THICKNESS;
    }

    /** Advance the band one turn ({@value #STEP} tiles). */
    public void advance() {
        front += direction * STEP;
    }

    /**
     * True once the entire band has left the arena, at which point the wave is gone.
     * Checked AFTER {@link #advance}: the trailing edge is what has to clear the far side,
     * so a band still half inside keeps flooding.
     */
    public boolean hasExited() {
        int trailing = front - direction * (THICKNESS - 1);
        return (direction > 0 && trailing >= travelExtent) || (direction < 0 && trailing < 0);
    }
}
