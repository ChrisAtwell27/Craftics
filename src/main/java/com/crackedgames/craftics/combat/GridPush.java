package com.crackedgames.craftics.combat;

/**
 * The one definition of what happens when something is shoved across the grid.
 *
 * <p>Craftics pushes things constantly - a mace, a sweep, a wind burst, a breeze, a Crater slam,
 * an addon ability, a player displaced off a tile something else wants. Every one of those used to
 * walk the path itself, and the rules they walked it by drifted apart: most treated the arena
 * boundary as a wall, one killed anything crossing it, and a Punch bow turned that difference into
 * a free one-shot from range. Nothing structural made them agree, so the only way to find out they
 * disagreed was to be exploited.
 *
 * <h2>What this owns</h2>
 *
 * <p>Only the traversal: how far the thing gets, and what stopped it. It moves nothing and damages
 * nothing. Callers keep their own consequences - Crater's slam damage, the cactus scratch, a
 * hazard's effect, the message - because those genuinely differ, while "how far does it go" never
 * should have.
 *
 * <h2>Why the Grid interface</h2>
 *
 * <p>{@code GridArena} reaches into Minecraft's block registry through {@code GridTile}, so a test
 * cannot construct one. Classifying each tile behind a tiny interface leaves the rule itself as
 * plain arithmetic over an enum, which a test can drive exhaustively - and this rule is one nobody
 * could check before, because it lived in six copies inside a 38,000-line class.
 */
public final class GridPush {

    private GridPush() {}

    /** What a single tile does to something being pushed onto it. */
    public enum Cell {
        /** Past the edge of the arena. Stops a push; never kills - the board simply ends. */
        OUT_OF_BOUNDS,
        /** Another combatant's footprint. */
        ENTITY,
        /** A player is standing here. */
        PLAYER,
        /** Solid scenery. */
        HARD_OBSTACLE,
        /** Scenery that hurts but does not stop you - a cactus. */
        SOFT_OBSTACLE,
        /** Void, water, lava, powder snow, a sunken pit: entered, not bounced off. */
        HAZARD,
        /** Terrain that is simply not walkable. */
        IMPASSABLE,
        /** Ordinary ground. */
        OPEN
    }

    /** Why the push ended. */
    public enum Stop {
        /** Travelled the full distance asked for. */
        COMPLETED,
        BOUNDARY,
        ENTITY,
        PLAYER,
        OBSTACLE,
        IMPASSABLE,
        /** Hazard-immune, so a hazard tile acted as a wall and it stopped in front. */
        HAZARD_EDGE,
        /** Moved onto a hazard and stopped there. */
        HAZARD_ENTERED
    }

    /** The arena, reduced to the one question this rule asks of it. */
    public interface Grid {
        Cell cellAt(int x, int z);
    }

    /**
     * @param x              where it ended up
     * @param z              where it ended up
     * @param moved          tiles actually travelled, 0 if it never left the start
     * @param stop           why it stopped
     * @param enteredHazard  it is now standing on a hazard tile
     * @param brushedCactus  a cactus was in the footprint on the way, whether or not it stopped
     */
    public record Result(int x, int z, int moved, Stop stop,
                         boolean enteredHazard, boolean brushedCactus) {
        public boolean blocked() { return stop != Stop.COMPLETED; }
    }

    /**
     * Walk a push one tile at a time and report where it ends.
     *
     * <p>The footprint is checked in FULL at every candidate, not just its corner. A 2x2 spider
     * pushed by its top-left tile alone would land sharing three tiles with whatever was already
     * there.
     *
     * @param sizeX        footprint width, 1 for an ordinary mob
     * @param sizeZ        footprint depth
     * @param hazardImmune a boss that treats hazards as walls rather than falling in. Passed in
     *                     rather than folded into the grid, because it is a property of the thing
     *                     being pushed and part of the rule, not a fact about the tile.
     */
    public static Result resolve(Grid grid, int startX, int startZ, int sizeX, int sizeZ,
                                 int dx, int dz, int tiles, boolean hazardImmune) {
        int landX = startX;
        int landZ = startZ;
        int moved = 0;
        boolean cactus = false;

        for (int i = 1; i <= tiles; i++) {
            int candX = startX + dx * i;
            int candZ = startZ + dz * i;

            Stop wall = null;
            boolean hazardHere = false;
            boolean cactusHere = false;

            scan:
            for (int fx = 0; fx < Math.max(1, sizeX); fx++) {
                for (int fz = 0; fz < Math.max(1, sizeZ); fz++) {
                    switch (grid.cellAt(candX + fx, candZ + fz)) {
                        case OUT_OF_BOUNDS -> { wall = Stop.BOUNDARY; break scan; }
                        case ENTITY        -> { wall = Stop.ENTITY; break scan; }
                        case PLAYER        -> { wall = Stop.PLAYER; break scan; }
                        case HARD_OBSTACLE -> { wall = Stop.OBSTACLE; break scan; }
                        case IMPASSABLE    -> { wall = Stop.IMPASSABLE; break scan; }
                        // Neither of these stops the scan: a cactus is brushed past, and a hazard
                        // is stepped INTO. Both can sit alongside a wall in the same footprint,
                        // and the wall still wins.
                        case SOFT_OBSTACLE -> cactusHere = true;
                        case HAZARD -> {
                            if (hazardImmune) { wall = Stop.HAZARD_EDGE; break scan; }
                            hazardHere = true;
                        }
                        case OPEN -> { }
                    }
                }
            }

            // A cactus counts even on the step that was blocked - the original rule scratches you
            // for reaching it, not for landing on it.
            if (cactusHere) cactus = true;
            if (wall != null) return new Result(landX, landZ, moved, wall, false, cactus);

            landX = candX;
            landZ = candZ;
            moved = i;
            if (hazardHere) {
                return new Result(landX, landZ, moved, Stop.HAZARD_ENTERED, true, cactus);
            }
        }
        return new Result(landX, landZ, moved, Stop.COMPLETED, false, cactus);
    }
}
