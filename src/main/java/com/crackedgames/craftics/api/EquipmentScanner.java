package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.StatModifiers;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Scans a player's non-standard inventory slots and returns the combat stat bonuses
 * those slots contribute.
 *
 * <p>Craftics only inspects the four vanilla armor slots and the main-hand slot for
 * trim and weapon bonuses. Addons that add extra equipment slots (trinkets, baubles,
 * curios, rings, …) register an {@code EquipmentScanner} so their items can also
 * modify Craftics stats. The scanner runs alongside the built-in armor scan and its
 * result is merged into the player's combined {@link StatModifiers} for the fight.
 *
 * <p>Register via {@code CrafticsAPI.registerEquipmentScanner}:
 *
 * <pre>{@code
 * CrafticsAPI.registerEquipmentScanner("mymod:trinkets", player -> {
 *     StatModifiers mods = new StatModifiers();
 *     ItemStack trinket = MyTrinketSlot.get(player);
 *     if (trinket.isOf(MyItems.SPEED_RING)) {
 *         mods.add(TrimEffects.Bonus.SPEED, 2);
 *     }
 *     return mods;
 * });
 * }</pre>
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface EquipmentScanner {

    /**
     * Inspect the player's non-standard slots and return their combined stat bonuses.
     *
     * @param player the player to scan
     * @return a {@link StatModifiers} accumulator populated with the bonuses found;
     *         return an empty {@code StatModifiers} (not {@code null}) if no bonuses apply
     */
    StatModifiers scan(ServerPlayerEntity player);
}
