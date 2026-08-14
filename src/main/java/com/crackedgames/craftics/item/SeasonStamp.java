package com.crackedgames.craftics.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * When a combat item came into a player's hands, written invisibly onto the item itself.
 *
 * <p>Groundwork for seasons. The intent is that a new season disables everything earned in the
 * previous one - gear stops being usable in battle, islands reset - so the server restarts on
 * an even footing and new content is actually engaged with rather than skipped by whoever
 * already had a full set of end-game equipment. None of that is implemented here. This is the
 * one piece that CANNOT be added later: you cannot retroactively work out when an item was
 * acquired, so the stamping has to start before the first season boundary or every item that
 * already exists is unattributable.
 *
 * <p><b>Invisible to the player.</b> It lives in the vanilla {@code CUSTOM_DATA} component,
 * which does not render in a tooltip, does not affect stacking behaviour the player would
 * notice beyond same-timestamp grouping, and survives being dropped, stored, traded and
 * re-picked-up. Nothing shows it and nothing reads it yet.
 *
 * <p><b>Stamped by sweep rather than at the source.</b> There is no single "an item entered a
 * player's inventory" hook - items arrive from loot, crafting, trading, the auction house, a
 * dropped stack, an admin command, another mod - and a stamp applied at only some of those
 * would be worse than none, because the gaps would look like legitimately unstamped items. A
 * periodic pass over inventories catches every route by construction.
 *
 * <p>Known gap: items sitting in containers rather than inventories are never swept. That is
 * deliberate for now - a season reset is expected to clear islands anyway, which is where those
 * containers live - but it is the first thing to revisit when seasons are actually built.
 */
public final class SeasonStamp {

    private SeasonStamp() {}

    /** Key inside the item's custom data. Namespaced so nothing else collides with it. */
    private static final String ACQUIRED_KEY = "craftics_acquired";

    /** Ticks between inventory sweeps. Two seconds: a stamp being a moment late costs nothing,
     *  and this runs for every online player. */
    public static final int SWEEP_INTERVAL_TICKS = 40;

    /**
     * When this item was first seen in a player's hands, or 0 if it has never been stamped.
     *
     * @return epoch milliseconds
     */
    public static long acquiredAt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return 0L;
        NbtCompound nbt = data.copyNbt();
        //? if <=1.21.4 {
        return nbt.contains(ACQUIRED_KEY) ? nbt.getLong(ACQUIRED_KEY) : 0L;
        //?} else {
        /*return nbt.getLong(ACQUIRED_KEY, 0L);
        *///?}
    }

    /** True once this item carries a stamp. */
    public static boolean isStamped(ItemStack stack) {
        return acquiredAt(stack) != 0L;
    }

    /** Write the acquisition time, leaving any other custom data on the item alone. */
    public static void stamp(ItemStack stack, long epochMillis) {
        if (stack == null || stack.isEmpty()) return;
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack,
            nbt -> nbt.putLong(ACQUIRED_KEY, epochMillis));
    }

    /**
     * Does this item do anything in a Craftics fight?
     *
     * <p>Only these are stamped. Stamping every cobblestone would double the size of a save's
     * item data for no purpose - a season boundary cares about the sword somebody is bringing
     * to the fight, not their building blocks.
     *
     * <p>Deliberately generous at the edges: armour and weapons obviously count, but so does
     * anything the item-use system or an addon has registered as usable in combat, because
     * "has a use in battle" is exactly the registry's own question and it already knows the
     * answer for modded content too.
     */
    public static boolean isCombatRelevant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        net.minecraft.item.Item item = stack.getItem();
        // ArmorItem stopped existing in 1.21.5 - armour is the EQUIPPABLE component now, and
        // asking the component is the more honest question anyway: "can this be worn" rather
        // than "is it one of the classes that used to mean worn".
        //? if <=1.21.4 {
        if (item instanceof net.minecraft.item.ArmorItem) return true;
        //?} else {
        /*if (stack.contains(DataComponentTypes.EQUIPPABLE)) return true;
        *///?}
        if (com.crackedgames.craftics.api.registry.WeaponRegistry.hasAbility(item)) return true;
        if (com.crackedgames.craftics.api.registry.WeaponRegistry.get(item) != null
            && com.crackedgames.craftics.api.registry.WeaponRegistry.get(item).attackPower() != null) {
            return true;
        }
        return com.crackedgames.craftics.api.registry.UsableItemRegistry.getOrNull(item) != null;
    }

    /**
     * Stamp anything in this player's inventory that should be stamped and is not yet.
     *
     * <p>Only ever WRITES to an unstamped item. Re-stamping on every pass would rewrite the
     * component constantly - churning item data, breaking stacking as timestamps diverged, and
     * quietly resetting the very age this exists to record.
     *
     * @return how many items were stamped, for logging
     */
    public static int sweep(ServerPlayerEntity player, long nowMillis) {
        if (player == null) return 0;
        var inventory = player.getInventory();
        int stamped = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || isStamped(stack) || !isCombatRelevant(stack)) continue;
            stamp(stack, nowMillis);
            stamped++;
        }
        return stamped;
    }
}
