package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;

/**
 * Zombie Villager AI: the horde fighter with a desperate streak. Shares the
 * {@link UndeadHordeAI} march-and-bite loop, but below 50% HP the remnant of
 * the villager panics - +1 attack and +1 movement until it drops. A wounded
 * zombie villager closes distance faster than a fresh one.
 */
public class ZombieVillagerAI extends UndeadHordeAI {

    private static boolean desperate(CombatEntity self) {
        return self.getCurrentHp() < self.getMaxHp() / 2;
    }

    @Override
    protected int bonusDamage(CombatEntity self) {
        return desperate(self) ? 1 : 0;
    }

    @Override
    protected int effectiveSpeed(CombatEntity self) {
        return self.getMoveSpeed() + (desperate(self) ? 1 : 0);
    }
}
