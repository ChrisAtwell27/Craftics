package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Blaze Tower AI: the immobile multi-blaze stack. Speed 0 so it cannot move;
 * range 3. Every turn it fires three fireballs in sequence at the player
 * (assuming they are in range). When the tower drops to a single blaze on the
 * final layer the normal {@link BlazeAI} takes over via the registered AI for
 * {@code minecraft:blaze}, so this AI is only used for the multi-tier layers.
 */
public class BlazeTowerAI implements EnemyAI {

    private static final int SHOTS_PER_TURN = 3;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();
        if (dist > range) {
            return new EnemyAction.Idle();
        }
        int perShotDamage = Math.max(1, self.getAttackPower());
        List<EnemyAction> shots = new ArrayList<>(SHOTS_PER_TURN);
        for (int i = 0; i < SHOTS_PER_TURN; i++) {
            shots.add(new EnemyAction.RangedAttack(perShotDamage, "fire"));
        }
        return new EnemyAction.CompositeAction(shots);
    }
}
