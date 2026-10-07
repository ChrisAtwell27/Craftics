package com.crackedgames.craftics.compat.aether.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.ZombieAI;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.Set;

/**
 * Moa AI: a big bird that minds its own business until something hurts it.
 * - CALM: wanders or stands, and is no threat to anyone
 * - PROVOKED: one hit and it is in the fight for good - walks in and pecks
 *
 * The neutral half is shared with {@link ValkyrieAI}, which fights very differently once
 * it has a reason to.
 */
public class MoaAI implements EnemyAI {

    /** Walk in, hit on arrival: the engine's plain melee turn, borrowed rather than rewritten. */
    private static final EnemyAI MELEE = new ZombieAI();

    /** Neutral: only a threat once it has been hit. */
    @Override
    public boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return AetherAi.provoked(self);
    }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        if (!AetherAi.provoked(self)) return AetherAi.loiter(self, arena);
        return fight(self, arena, playerPos);
    }

    /** The turn of a creature that has been given a reason. */
    protected EnemyAction fight(CombatEntity self, GridArena arena, GridPos playerPos) {
        return MELEE.decideAction(self, arena, playerPos);
    }

    /** A calm creature threatens no tiles, so it paints none. */
    @Override
    public Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        return AetherAi.provoked(self) ? null : Set.of();
    }
}
