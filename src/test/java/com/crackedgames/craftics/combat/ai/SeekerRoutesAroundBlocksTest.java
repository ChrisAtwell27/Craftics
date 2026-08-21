package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that a homing projectile goes around an obstacle instead of stopping in front of it.
 *
 * <p>The reported symptom was Grave Skulls in the Revenant fight sitting still forever once a
 * block was between them and the player. That is a permanent stall rather than a hesitation:
 * a seeker re-aims from scratch each turn, so if the obstacle does not move, neither does it.
 *
 * <p>The guaranteed case is the axis-aligned one. A seeker on the player's own row has a
 * zero-length "other axis", so the sidestep candidate is skipped as empty and one block leaves
 * it with no legal move at all.
 */
class SeekerRoutesAroundBlocksTest {

    private static final int SIZE = 9;

    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        return new GridArena(SIZE, SIZE, tiles, BlockPos.ORIGIN, 1, new GridPos(0, 0));
    }

    private static void block(GridArena a, int x, int z) {
        a.setTile(new GridPos(x, z), new GridTile(TileType.OBSTACLE, null));
    }

    /** A 1 HP seeker, speed overridden so it can act without a Minecraft bootstrap. */
    private static CombatEntity seekerAt(int x, int z) {
        return new CombatEntity(1, "minecraft:wither_skull", new GridPos(x, z),
            /* maxHp */ 1, /* attack */ 3, /* defense */ 0, /* range */ 1,
            /* sizeOverride */ 1, /* speedOverride */ 2);
    }

    private static java.util.List<GridPos> stepsOf(EnemyAction action) {
        if (action instanceof EnemyAction.ProjectileMove pm) return pm.path();
        return java.util.List.of();
    }

    // ── The stall ─────────────────────────────────────────────────────────

    @Test
    void anAxisAlignedSeekerWalledOffStillMoves() {
        // Seeker at (2,4), player at (6,4), a block dead between them. Both toward-player
        // candidates are unusable - one blocked, one zero-length - which is exactly where it
        // used to give up and hover for the rest of the fight.
        GridArena a = arena();
        block(a, 3, 4);
        CombatEntity seeker = seekerAt(2, 4);

        EnemyAction action = new SeekingProjectileAI().decideAction(seeker, a, new GridPos(6, 4));
        assertFalse(stepsOf(action).isEmpty(), "a blocked seeker must go around, not stop");
        assertFalse(action instanceof EnemyAction.Idle, "idling here is the bug");
    }

    @Test
    void itDoesNotStepIntoTheObstacle() {
        GridArena a = arena();
        block(a, 3, 4);
        CombatEntity seeker = seekerAt(2, 4);

        for (GridPos step : stepsOf(new SeekingProjectileAI().decideAction(seeker, a, new GridPos(6, 4)))) {
            assertNotEquals(new GridPos(3, 4), step, "stepped onto a solid tile");
            assertTrue(a.getTile(step).isWalkable(), "stepped onto an unwalkable tile: " + step);
        }
    }

    @Test
    void aSeekerBoxedInOnEverySideStillHoldsStill() {
        // The one case where standing still is right: there is genuinely nowhere to go. It
        // must not throw, and must not invent a move through a wall.
        GridArena a = arena();
        block(a, 3, 4);
        block(a, 1, 4);
        block(a, 2, 3);
        block(a, 2, 5);
        CombatEntity seeker = seekerAt(2, 4);

        EnemyAction action = new SeekingProjectileAI().decideAction(seeker, a, new GridPos(6, 4));
        assertTrue(stepsOf(action).isEmpty());
    }

    // ── Still a seeker ────────────────────────────────────────────────────

    @Test
    void anUnobstructedSeekerStillFliesStraightAtThePlayer() {
        // The detour must not have made it wander when it has a clear run.
        GridArena a = arena();
        CombatEntity seeker = seekerAt(2, 4);

        java.util.List<GridPos> steps =
            stepsOf(new SeekingProjectileAI().decideAction(seeker, a, new GridPos(6, 4)));
        assertEquals(java.util.List.of(new GridPos(3, 4), new GridPos(4, 4)), steps);
    }

    @Test
    void itImpactsWhenItReachesThePlayer() {
        GridArena a = arena();
        CombatEntity seeker = seekerAt(4, 4);

        EnemyAction action = new SeekingProjectileAI().decideAction(seeker, a, new GridPos(5, 4));
        assertInstanceOf(EnemyAction.ProjectileMove.class, action);
        assertTrue(((EnemyAction.ProjectileMove) action).impacts(), "landing on the player must detonate");
    }

    @Test
    void itClosesTheDistanceOverSuccessiveTurnsRatherThanOscillating() {
        // The reason the detour direction is remembered. Without it, one lateral step puts the
        // seeker where the toward-player candidate points straight back into the tile it just
        // left, and it rocks between two squares forever - a different stall wearing motion.
        GridArena a = arena();
        // A short wall it has to get around, with a gap well off to one side.
        for (int z = 0; z <= 5; z++) block(a, 3, z);
        GridPos player = new GridPos(6, 4);

        CombatEntity seeker = seekerAt(2, 4);
        int startDistance = seeker.getGridPos().manhattanDistance(player);
        java.util.Set<GridPos> visited = new java.util.HashSet<>();
        SeekingProjectileAI ai = new SeekingProjectileAI();

        for (int turn = 0; turn < 8; turn++) {
            EnemyAction action = ai.decideAction(seeker, a, player);
            java.util.List<GridPos> steps = stepsOf(action);
            if (steps.isEmpty()) break;
            GridPos end = steps.get(steps.size() - 1);
            seeker.setGridPos(end);
            visited.add(end);
            if (end.equals(player)) break;
        }

        assertTrue(visited.size() > 2,
            "seeker only ever occupied " + visited.size() + " tiles - it is oscillating");
        assertTrue(seeker.getGridPos().manhattanDistance(player) < startDistance
                || seeker.getGridPos().x() > 3,
            "seeker made no progress past the wall");
    }
}
