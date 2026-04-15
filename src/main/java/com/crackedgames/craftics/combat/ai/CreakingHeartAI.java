package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

/**
 * Creaking Heart AI: Stationary block entity that must be destroyed to kill
 * its linked Creaking. Never moves, never attacks — purely a target.
 */
public class CreakingHeartAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return new EnemyAction.Idle();
    }
}
