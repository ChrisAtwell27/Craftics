package com.crackedgames.craftics.compat.aether.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Valkyrie AI: a dungeon guard that will not start the fight, and is hard to get away from
 * once you have.
 * - NEUTRAL: wanders the room and ignores everyone until it is hit (see {@link MoaAI})
 * - LUNGE: with the target 2 to 4 tiles away down a clear straight line, it crosses the
 *   whole gap in one action and strikes, harder for every tile of run-up past the first
 * - BLINK: when it can neither walk nor lunge into reach, it teleports to a tile beside the
 *   target instead. It does not strike on that turn, and it cannot do it again for
 *   {@link #BLINK_COOLDOWN} turns
 * - Otherwise it walks in and hits like any other melee mob
 *
 * The blink state lives in the entity's AI memory, so one instance serves every valkyrie on
 * every spawn path without any of it leaking between them.
 */
public class ValkyrieAI extends MoaAI {

    /** Nearest and furthest the target can be for a lunge. Closer than this is just a swing. */
    public static final int LUNGE_MIN = 2;
    public static final int LUNGE_MAX = 4;
    /** How far away the target can be and still be blinked to. */
    public static final int BLINK_RANGE = 8;
    /** Turns that must pass after a blink before the next one. */
    public static final int BLINK_COOLDOWN = 3;

    private static final String COOLDOWN = "valkyrie_blink_cooldown";
    private static final String PENDING_X = "valkyrie_blink_x";
    private static final String PENDING_Z = "valkyrie_blink_z";
    /** No blink waiting to be booked. Grid coordinates are never negative. */
    private static final int NONE = -1;

    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    protected EnemyAction fight(CombatEntity self, GridArena arena, GridPos playerPos) {
        int cooldown = tickBlink(self);

        if (self.minDistanceTo(playerPos) <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        EnemyAction lunge = lunge(self, arena, playerPos);
        if (lunge != null) return lunge;

        // Reaching the target on foot this turn beats blinking next to it and waiting.
        EnemyAction onFoot = super.fight(self, arena, playerPos);
        if (onFoot instanceof EnemyAction.MoveAndAttack) return onFoot;

        if (cooldown == 0 && self.minDistanceTo(playerPos) <= BLINK_RANGE) {
            GridPos landing = blinkTile(self, arena, playerPos);
            if (landing != null) {
                self.setAiMemory(PENDING_X, landing.x());
                self.setAiMemory(PENDING_Z, landing.z());
                return new EnemyAction.Teleport(landing);
            }
        }
        return onFoot;
    }

    /**
     * Run the blink cooldown down by a turn and say what it was on the way in.
     *
     * <p>A blink is only booked once the valkyrie is found standing on the tile it picked,
     * not when it picks it. The radar and the intent preview both ask an AI what it would do
     * without doing it, and a cooldown started at the asking would be spent by a preview and
     * leave the real turn with nothing.
     */
    private static int tickBlink(CombatEntity self) {
        GridPos me = self.getGridPos();
        if (self.getAiMemory(PENDING_X, NONE) == me.x() && self.getAiMemory(PENDING_Z, NONE) == me.z()) {
            self.setAiMemory(PENDING_X, NONE);
            self.setAiMemory(PENDING_Z, NONE);
            // This turn is the first of the cooldown, so one fewer is left to store.
            self.setAiMemory(COOLDOWN, BLINK_COOLDOWN - 1);
            return BLINK_COOLDOWN;
        }
        int cooldown = self.getAiMemory(COOLDOWN, 0);
        if (cooldown > 0) self.setAiMemory(COOLDOWN, cooldown - 1);
        return cooldown;
    }

    /** The run and the strike, or null when the target is not down a clear line at lunge range. */
    private static EnemyAction lunge(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos me = self.getGridPos();
        int dist = me.manhattanDistance(playerPos);
        if (dist < LUNGE_MIN || !AIUtils.hasCardinalLOS(arena, me, playerPos, LUNGE_MAX)) return null;

        int dx = Integer.signum(playerPos.x() - me.x());
        int dz = Integer.signum(playerPos.z() - me.z());
        List<GridPos> run = new ArrayList<>();
        for (int i = 1; i < dist; i++) {
            GridPos step = new GridPos(me.x() + dx * i, me.z() + dz * i);
            // The line check above only knows about enemies; a second player or a patch of
            // fire in the lane stops the lunge just the same.
            if (arena.isOccupied(step) || AIUtils.isHazardTile(arena, step)) return null;
            run.add(step);
        }
        return new EnemyAction.MoveAndAttack(run, lungeDamage(self.getAttackPower(), run.size()));
    }

    /** A lunge hits for one more per tile of run-up after the first. */
    public static int lungeDamage(int attackPower, int tilesCovered) {
        return attackPower + Math.max(0, tilesCovered - 1);
    }

    /** The free, safe tile beside the target that is nearest the valkyrie, or null. */
    private static GridPos blinkTile(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos me = self.getGridPos();
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int[] dir : CARDINALS) {
            GridPos tile = new GridPos(playerPos.x() + dir[0], playerPos.z() + dir[1]);
            if (!arena.isInBounds(tile) || arena.isOccupied(tile)) continue;
            var ground = arena.getTile(tile);
            if (ground == null || !ground.isWalkable() || ground.isWater()) continue;
            if (AIUtils.isHazardTile(arena, tile)) continue;
            int dist = me.manhattanDistance(tile);
            if (dist < bestDist) {
                bestDist = dist;
                best = tile;
            }
        }
        return best;
    }
}
