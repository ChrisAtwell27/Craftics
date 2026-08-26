package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bleed must scale the same way on an enemy as it does on a player.
 *
 * <p>It didn't: {@link CombatEntity#computeBleedTickDamage} returned a flat {@code stacks}
 * while the player path ({@link CombatEffects#applyPerTurnEffects}) charged the full triangle.
 * The enemy method's own javadoc described the curve it failed to implement, so the two paths
 * had silently diverged - bleeding an enemy was far weaker than being bled, and no test held the
 * formulas together. This one does.
 */
class BleedScalingTest {

    /** The curve both sides are supposed to use: half the triangle, 1, 1, 3, 5, 7... */
    private static int halfTriangular(int stacks) {
        return Math.max(1, stacks * (stacks + 1) / 4);
    }

    @Test
    void enemyBleedIsHalfTriangular() {
        assertEquals(1, CombatEntity.computeBleedTickDamage(1));
        assertEquals(1, CombatEntity.computeBleedTickDamage(2));
        assertEquals(3, CombatEntity.computeBleedTickDamage(3));
        assertEquals(5, CombatEntity.computeBleedTickDamage(4));
        assertEquals(7, CombatEntity.computeBleedTickDamage(5));
    }

    @Test
    void noStacksDealNoDamage() {
        assertEquals(0, CombatEntity.computeBleedTickDamage(0));
        assertEquals(0, CombatEntity.computeBleedTickDamage(-1), "a negative stack count can't heal");
    }

    /**
     * The regression that started this: the enemy tick and the player tick must agree at every
     * stack count, so a balance change to one can't silently leave the other behind.
     */
    @Test
    void enemyAndPlayerBleedAgreeAtEveryStackCount() {
        for (int stacks = 1; stacks <= 5; stacks++) {
            CombatEffects fx = new CombatEffects();
            fx.addEffect(CombatEffects.EffectType.BLEEDING, 3, stacks - 1); // amplifier+1 = stacks

            // applyPerTurnEffects returns a signed HP delta; bleed is the only effect here.
            int playerDamage = -fx.applyPerTurnEffects(0);

            assertEquals(halfTriangular(stacks), CombatEntity.computeBleedTickDamage(stacks),
                "enemy bleed at " + stacks + " stacks");
            // Not raw equality any more: a player takes the mob's number through one
            // documented factor (EffectFormulas.forPlayer). The sides are still bound to each
            // other - change the mob curve and this moves with it - but a player's health bar
            // has to last a whole run, so their share is trimmed.
            assertEquals(EffectFormulas.forPlayer(CombatEntity.computeBleedTickDamage(stacks)),
                playerDamage,
                "player bleed must be the mob's bleed through the player factor, at "
                    + stacks + " stacks");
        }
    }
}
