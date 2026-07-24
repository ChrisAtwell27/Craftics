package com.crackedgames.craftics.compat.deeperanddarker;

import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.ArmorClassTable;
import com.crackedgames.craftics.combat.DamageType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Warden armor set's identity: <b>Echo</b>.
 *
 * <p>Two halves that cut against each other, and only while all four pieces are worn:
 * <ul>
 *   <li><b>Permanent Darkness.</b> The wearer is always shrouded in combat, so the
 *       per-player Darkness fog-of-war applies to them forever - enemies more than
 *       {@code DARKNESS_REVEAL_RADIUS} tiles away are invisible on their client and
 *       drop out of their threat overlay. This is a real cost, not flavour: you fight
 *       the whole campaign half-blind.</li>
 *   <li><b>{@value #DOMINANT_AFFINITY_BONUS} affinity to whatever you carry most.</b>
 *       The set reads the player's inventory and boosts the damage type held by the
 *       most registered weapons. It is recomputed on every damage calculation, so
 *       swapping gear mid-fight moves the bonus with no re-equip needed.</li>
 * </ul>
 *
 * <p>The armor's own per-piece affinity is {@link DamageType#PHYSICAL} (registered in
 * {@link DeeperAndDarkerCompat}), deliberately the most generic lane - the set's real
 * specialisation is the dynamic bonus, so the static half shouldn't pre-commit it to
 * one build.
 *
 * <p>Both halves hang off the single seam each: the bonus off
 * {@link DamageType#getTotalBonus}, the Darkness off the player's turn start in
 * {@code CombatManager}. Nothing here needs Deeper and Darker to be installed to be
 * safe - {@link #isFullSet} simply never matches when the items don't exist.
 */
public final class WardenSetEffects {

    /** Armor-set key, derived from the item path {@code warden_helmet} etc. */
    public static final String SET_KEY = "warden";

    /** Whole affinity points added to the wearer's most-carried damage type. */
    public static final int DOMINANT_AFFINITY_BONUS = 2;

    /**
     * Turns of Darkness refreshed at the wearer's turn start. Longer than one turn so
     * the shroud does not blink off during the enemy phase between refreshes.
     */
    public static final int DARKNESS_TURNS = 3;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private WardenSetEffects() {}

    /** True when all four Warden pieces are worn. */
    public static boolean isFullSet(PlayerEntity player) {
        if (player == null) return false;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            String key = ArmorClassTable.armorSetKeyOf(player.getEquippedStack(slot).getItem());
            if (!SET_KEY.equals(key)) return false;
        }
        return true;
    }

    /**
     * The damage type carried by the most items in the player's inventory, or
     * {@code null} when they carry no registered weapon at all.
     *
     * <p>Counts one per occupied SLOT, not per item: a 64-stack of one throwable
     * would otherwise drown out every real weapon. Only items actually present in
     * {@link WeaponRegistry} count - unregistered items fall back to the registry's
     * bare-fist DEFAULT entry, so counting those would make every junk stack vote.
     * Ties break by enum order so the answer is stable frame to frame.
     */
    public static DamageType dominantAffinity(PlayerEntity player) {
        if (player == null) return null;
        Map<DamageType, Integer> counts = new EnumMap<>(DamageType.class);
        int size = player.getInventory().size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack == null || stack.isEmpty()) continue;
            WeaponEntry entry = WeaponRegistry.getOrNull(stack.getItem());
            if (entry == null) continue;
            counts.merge(entry.damageType(), 1, Integer::sum);
        }
        DamageType best = null;
        int bestCount = 0;
        for (Map.Entry<DamageType, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /**
     * Whole affinity points this set contributes to {@code type} right now. Called
     * from {@link DamageType#getTotalBonus}, i.e. on every damage calculation, which
     * is exactly what makes the bonus live.
     */
    public static int affinityBonus(PlayerEntity player, DamageType type) {
        if (type == null || !isFullSet(player)) return 0;
        return type == dominantAffinity(player) ? DOMINANT_AFFINITY_BONUS : 0;
    }
}
