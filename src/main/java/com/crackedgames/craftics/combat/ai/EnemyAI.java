package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

public interface EnemyAI {
    EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos);
}
