package com.crackedgames.craftics.compat.aether.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;
import java.util.Set;

/**
 * Sentry AI: a block of dungeon wall that turns out to be a proximity mine.
 * - DORMANT: does nothing at all until someone comes within {@link #WAKE_RANGE} tiles, or
 *   hits it. Once awake it stays awake
 * - HOP: closes on its target and never attacks on the way
 * - DETONATE: starts its turn with a victim (player or pet) on a neighbouring tile and it
 *   explodes, which kills it. Arriving next to someone is therefore one turn of warning:
 *   step away, or finish it first
 *
 * A dormant sentry is still counted as a threat. A room of them is not a room that can be
 * waited out, and it should not end itself as if it were full of sheep.
 */
public class SentryAI implements EnemyAI {

    /** How close anyone can come before a sentry wakes. */
    public static final int WAKE_RANGE = 3;
    /** The blast reaches the four tiles beside it. */
    public static final int BLAST_RADIUS = 1;

    /** aiMemory key: 1 once the sentry has woken. A one-way latch. */
    private static final String AWAKE = "sentry_awake";

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        List<GridPos> threats = AIUtils.threatPositions(arena, playerPos);
        if (!awake(self, threats)) return new EnemyAction.Idle();

        if (AIUtils.minThreatDistance(self.getGridPos(), threats) <= BLAST_RADIUS) {
            return new EnemyAction.Explode(self.getAttackPower(), BLAST_RADIUS);
        }

        List<GridPos> hop = approach(self, arena, playerPos);
        return hop.isEmpty() ? new EnemyAction.Idle() : new EnemyAction.Move(hop);
    }

    /** Run into, it goes off where it stands, asleep or awake: the same blast it would have walked over. */
    @Override
    public EnemyAction.Explode whenRammed(CombatEntity self) {
        return new EnemyAction.Explode(self.getAttackPower(), BLAST_RADIUS);
    }

    /**
     * Asleep, the ground to stay off is the ring that wakes it. Awake, the engine's own
     * reach-plus-movement estimate is the right picture.
     */
    @Override
    public Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        if (self.getAiMemory(AWAKE, 0) == 1) return null;
        return AetherAi.diamond(arena, self.getGridPos(), WAKE_RANGE);
    }

    private static boolean awake(CombatEntity self, List<GridPos> threats) {
        if (self.getAiMemory(AWAKE, 0) == 1) return true;
        boolean disturbed = self.wasDamagedSinceLastTurn()
            || self.getCurrentHp() < self.getMaxHp()
            || AIUtils.minThreatDistance(self.getGridPos(), threats) <= WAKE_RANGE;
        if (disturbed) self.setAiMemory(AWAKE, 1);
        return disturbed;
    }

    /** The hop toward the target: onto a tile beside it if one is in reach, else as near as it gets. */
    private static List<GridPos> approach(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int speed = self.getMoveSpeed();
        GridPos beside = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
        if (beside != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, beside, speed, self);
            if (!path.isEmpty()) return path;
        }
        return AetherAi.stepToward(self, arena, playerPos);
    }
}
