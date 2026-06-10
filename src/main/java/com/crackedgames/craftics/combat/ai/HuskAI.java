package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;

/**
 * Husk AI: Desert zombie with hunger drain mechanic.
 * - HUNGER DRAIN: each hit reduces player AP by 1 for 2 turns (via hit effect,
 *   applied by CombatManager's applyEnemyHitEffect) and bites for +1 damage
 * - Desperation: +1 speed when below 50% HP
 * - Joins the {@link UndeadHordeAI} horde bonus (+1 attack per adjacent undead),
 *   which the old standalone implementation never did
 */
public class HuskAI extends UndeadHordeAI {

    @Override
    protected int bonusDamage(CombatEntity self) {
        return 1; // hunger bite
    }

    @Override
    protected int effectiveSpeed(CombatEntity self) {
        int speed = self.getMoveSpeed();
        if (self.getCurrentHp() < self.getMaxHp() / 2) {
            speed += 1; // desperation
        }
        return speed;
    }
}
