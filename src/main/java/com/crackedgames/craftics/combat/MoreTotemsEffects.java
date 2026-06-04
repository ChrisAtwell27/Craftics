package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Pure-logic helpers for MoreTotems combat effects, kept free of Minecraft runtime
 * types so they are JUnit-testable. {@code CombatManager} calls these.
 */
public final class MoreTotemsEffects {

    private MoreTotemsEffects() {}

    /**
     * Damage one enemy takes from the Explosive totem: half its max HP plus a flat
     * bonus equal to the arena's biome ordinal. Always at least 1.
     */
    public static int explosionDamage(int enemyMaxHp, int biomeOrdinal) {
        int dmg = (enemyMaxHp / 2) + Math.max(0, biomeOrdinal);
        return Math.max(1, dmg);
    }
}
