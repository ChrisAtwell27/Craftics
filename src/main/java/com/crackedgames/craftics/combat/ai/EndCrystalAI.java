package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

/**
 * End Crystal — stationary explosive hazard.
 *
 * Behavior:
 *  - Cannot move.
 *  - On the turn after it takes any damage, it detonates (AoE explosion).
 *  - The explosion kills the crystal and damages everything in radius 2.
 */
public class EndCrystalAI implements EnemyAI {

    private static final int EXPLOSION_DAMAGE = 12;
    private static final int EXPLOSION_RADIUS = 2;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // If the crystal was hit since its last turn, it detonates
        if (self.wasDamagedSinceLastTurn()) {
            return new EnemyAction.Explode(EXPLOSION_DAMAGE, EXPLOSION_RADIUS);
        }
        return new EnemyAction.Idle();
    }
}
