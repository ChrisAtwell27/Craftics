package com.crackedgames.craftics.compat.simplyswords;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls Simply Swords UNIQUE weapons as rare boss-kill drops.
 * Pool = every unique {@link SimplySwordsUniques} managed to register this launch,
 * so it automatically tracks whichever Simply Swords version is installed.
 * Returns {@link ItemStack#EMPTY} when the mod is absent or the pool is empty.
 */
public final class SimplySwordsLootRoller {

    private SimplySwordsLootRoller() {}

    /** Lazily-built pool of unique weapon items. */
    private static List<Item> pool;

    private static List<Item> pool() {
        if (pool != null) return pool;
        if (!SimplySwordsCompat.isLoaded() || !SimplySwordsCompat.isRegistered()) return List.of();
        List<Item> built = new ArrayList<>();
        for (String path : SimplySwordsUniques.registeredPaths()) {
            Item item = SimplySwordsCompat.lookupItem(path);
            if (item != null) built.add(item);
        }
        pool = List.copyOf(built);
        return pool;
    }

    /**
     * Roll one unique weapon for {@code recipient}, preferring uniques the player does
     * not already carry so a full collection stays reachable. Falls back to the full
     * pool once the player owns everything (duplicates become trade fodder).
     */
    public static ItemStack rollOne(ServerPlayerEntity recipient) {
        List<Item> all = pool();
        if (all.isEmpty()) return ItemStack.EMPTY;

        List<Item> unowned = new ArrayList<>();
        for (Item candidate : all) {
            if (recipient == null || !playerCarries(recipient, candidate)) {
                unowned.add(candidate);
            }
        }
        List<Item> from = unowned.isEmpty() ? all : unowned;
        Item picked = from.get(new java.util.Random().nextInt(from.size()));
        return new ItemStack(picked);
    }

    private static boolean playerCarries(ServerPlayerEntity player, Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() == item) return true;
        }
        return false;
    }
}
