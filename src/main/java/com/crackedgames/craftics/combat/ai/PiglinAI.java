package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Piglin AI: behavior depends on the weapon held in the main hand.
 *
 * - Bow / Crossbow → ranged kiter (delegates to {@link PillagerAI})
 * - Anything else  → melee rusher (delegates to {@link ZombieAI})
 *
 * Baby piglins inherit the generic +2 speed bonus from the spawn loop's
 * {@code mob.isBaby()} check, so they move faster than adults with no
 * AI changes required here.
 */
public class PiglinAI implements EnemyAI {

    // Delegates kept as singletons — both underlying AIs are stateless.
    private static final PillagerAI RANGED = new PillagerAI();
    private static final ZombieAI MELEE = new ZombieAI();

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return pickDelegate(self).decideAction(self, arena, playerPos);
    }

    private EnemyAI pickDelegate(CombatEntity self) {
        MobEntity mob = self.getMobEntity();
        if (mob == null) return MELEE;
        ItemStack mainHand = mob.getMainHandStack();
        if (mainHand.isEmpty()) return MELEE;
        Item weapon = mainHand.getItem();
        if (weapon == Items.BOW || weapon == Items.CROSSBOW) return RANGED;
        return MELEE;
    }
}
