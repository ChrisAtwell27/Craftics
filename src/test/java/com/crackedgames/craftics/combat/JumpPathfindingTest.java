package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The jump mechanic's cost model, which is the thing an off-by-one would quietly ruin.
 *
 * <p>The rule: a jump costs what the walk WOULD have cost with no gap in the way, plus one.
 * Clearing an N-tile gap means landing N+1 tiles away, so the price is N + 2.
 */
class JumpPathfindingTest {

    private static final int SIZE = 8;

    /** An 8x8 arena of plain floor, with tiles paintable per test. */
    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        return new GridArena(SIZE, SIZE, tiles, BlockPos.ORIGIN, 1, new GridPos(0, 0));
    }

    /**
     * Repaint a tile. Goes through the {@link GridTile} constructor rather than
     * {@code setType}, because {@code setType} resolves a default {@link net.minecraft.block.Block}
     * and these tests run without a Minecraft bootstrap.
     */
    private static void paint(GridArena a, TileType type, int x, int z) {
        a.setTile(new GridPos(x, z), new GridTile(type, null));
    }

    @Test
    void jumpCostIsGapPlusTwo() {
        // 1-tile gap: land 2 away. Walk would be 2, +1 for the jump = 3.
        assertEquals(3, Pathfinding.jumpCost(1));
        // 2-tile gap: land 3 away. Walk would be 3, +1 = 4. (The user's own example.)
        assertEquals(4, Pathfinding.jumpCost(2));
    }

    /**
     * The scenario from the spec: a void pit between the player and their destination.
     * Walking around costs 4; jumping costs 3. A* must take the jump.
     */
    @Test
    void jumpsAVoidPitWhenCheaperThanWalkingAround() {
        GridArena a = arena();
        // Wall of void across z=1, leaving a detour around x=4.
        for (int x = 0; x < 4; x++) paint(a, TileType.VOID, x, 1);

        GridPos from = new GridPos(0, 0);
        GridPos to = new GridPos(0, 2); // straight across the pit

        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(a, from, to, 10, false, false, false);
        assertFalse(p.isEmpty(), "must find a route across the pit");
        assertEquals(3, p.cost(), "1-tile gap => cost 3, not the 4-cost walk around");
        assertEquals(1, p.steps().size(), "the jump is a SINGLE step that lands past the pit");
        assertEquals(to, p.steps().get(0));
        assertEquals(1, p.jumpedOver().size());
        assertEquals(new GridPos(0, 1), p.jumpedOver().get(0), "the void tile was vaulted");
    }

    /** Cost must come from the Path, never from step count - that is the whole point. */
    @Test
    void pathSizeIsNotTheCost() {
        GridArena a = arena();
        paint(a, TileType.VOID, 0, 1);
        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(
            a, new GridPos(0, 0), new GridPos(0, 2), 10, false, false, false);
        assertNotEquals(p.steps().size(), p.cost(),
            "a jump is 1 step but costs 3 - charging size() would undercharge it");
    }

    /** Walking is still preferred when there is no reason to jump. */
    @Test
    void prefersWalkingWhenNothingIsInTheWay() {
        GridArena a = arena();
        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(
            a, new GridPos(0, 0), new GridPos(0, 2), 10, false, false, false);
        assertEquals(2, p.cost(), "open ground: 2 tiles = 2 speed, no jump");
        assertTrue(p.jumpedOver().isEmpty());
    }

    /** A 2-tile gap costs 4 - the user's second example. */
    @Test
    void clearsATwoTileGapForFour() {
        GridArena a = arena();
        paint(a, TileType.VOID, 0, 1);
        paint(a, TileType.VOID, 0, 2);
        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(
            a, new GridPos(0, 0), new GridPos(0, 3), 20, false, false, false);
        assertFalse(p.isEmpty());
        assertEquals(4, p.cost());
        assertEquals(2, p.jumpedOver().size());
    }

    /** Gaps wider than MAX_JUMP_GAP are not clearable - you must go around or not at all. */
    @Test
    void cannotClearAGapWiderThanTheLimit() {
        GridArena a = arena();
        // A full-width void wall 3 deep: too wide to jump, no way around.
        for (int x = 0; x < 8; x++) {
            paint(a, TileType.VOID, x, 1);
            paint(a, TileType.VOID, x, 2);
            paint(a, TileType.VOID, x, 3);
        }
        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(
            a, new GridPos(0, 0), new GridPos(0, 4), 20, false, false, false);
        assertTrue(p.isEmpty(), "a 3-tile gap exceeds MAX_JUMP_GAP and has no detour");
    }

    /** Obstacles stand ABOVE the floor - you would jump through them, so they are not gaps. */
    @Test
    void obstaclesAreNotJumpable() {
        GridArena a = arena();
        for (int x = 0; x < 8; x++) paint(a, TileType.OBSTACLE, x, 1);
        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(
            a, new GridPos(0, 0), new GridPos(0, 2), 20, false, false, false);
        assertTrue(p.isEmpty(), "an obstacle wall blocks entirely - it cannot be vaulted");
    }

    /** Powder snow is a concealed trap; hopping it for a flat cost would defuse it. */
    @Test
    void powderSnowIsNotJumpable() {
        GridArena a = arena();
        paint(a, TileType.POWDER_SNOW, 0, 1);
        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(
            a, new GridPos(0, 0), new GridPos(0, 2), 20, false, false, false);
        // Reachable only by WALKING through the snow (it is walkable), never by a 3-cost jump.
        assertEquals(2, p.cost(), "walks through the snow rather than vaulting it");
        assertTrue(p.jumpedOver().isEmpty());
    }

    /**
     * A jump must never TERMINATE on a gap tile. Here the tile past the pit is void
     * too, so the only legal landing is the tile beyond that - a 2-gap jump for 4.
     */
    @Test
    void neverLandsOnAGapTile() {
        GridArena a = arena();
        for (int x = 0; x < 8; x++) {
            paint(a, TileType.VOID, x, 1);
            paint(a, TileType.VOID, x, 2);
        }
        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(
            a, new GridPos(0, 0), new GridPos(0, 3), 20, false, false, false);
        assertFalse(p.isEmpty());
        for (GridPos step : p.steps()) {
            assertNotEquals(TileType.VOID, a.getTile(step).getType(),
                "no step may come to rest on void");
        }
        assertEquals(4, p.cost(), "cleared both void tiles in one 2-gap jump");
    }

    /** Reachability must agree with the pathfinder, or a green tile becomes a dead click. */
    @Test
    void reachabilityIncludesJumpOnlyTiles() {
        GridArena a = arena();
        for (int x = 0; x < 8; x++) paint(a, TileType.VOID, x, 1);
        var reachable = Pathfinding.getPlayerReachableTilesWithJumps(
            a, new GridPos(0, 0), 3, false, false);
        assertTrue(reachable.contains(new GridPos(0, 2)),
            "the far side of the pit is reachable in exactly 3 speed, so it must be highlighted");
    }

    /** With only 2 speed the 3-cost jump is unaffordable, so the far side must NOT light up. */
    @Test
    void reachabilityRespectsTheSpeedBudget() {
        GridArena a = arena();
        for (int x = 0; x < 8; x++) paint(a, TileType.VOID, x, 1);
        var reachable = Pathfinding.getPlayerReachableTilesWithJumps(
            a, new GridPos(0, 0), 2, false, false);
        assertFalse(reachable.contains(new GridPos(0, 2)),
            "2 speed cannot afford a 3-cost jump");
    }
}
