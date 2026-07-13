package com.crackedgames.craftics.compat.simplybows;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls Simply Bows uniques as rare boss-kill drops, mirroring the Simply Swords roller.
 * The pool is whatever {@link SimplyBowsUniques} managed to register this launch, so it
 * tracks whichever Simply Bows version is installed. Returns {@link ItemStack#EMPTY} when
 * the mod is absent or the pool is empty.
 */
public final class SimplyBowsLootRoller {

    private SimplyBowsLootRoller() {}

    /**
     * Roll one unique bow for {@code recipient}, preferring bows the player does not
     * already carry so a full collection stays reachable. Falls back to the whole pool
     * once they own everything.
     */
    public static ItemStack rollOne(ServerPlayerEntity recipient) {
        List<Item> all = SimplyBowsCompat.registeredBows();
        if (all.isEmpty()) return ItemStack.EMPTY;

        List<Item> unowned = new ArrayList<>();
        for (Item candidate : all) {
            if (recipient == null || !playerCarries(recipient, candidate)) unowned.add(candidate);
        }
        List<Item> from = unowned.isEmpty() ? all : unowned;
        return new ItemStack(from.get(new java.util.Random().nextInt(from.size())));
    }

    private static boolean playerCarries(ServerPlayerEntity player, Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() == item) return true;
        }
        return false;
    }
}
