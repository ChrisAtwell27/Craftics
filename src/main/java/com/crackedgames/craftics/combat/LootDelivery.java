package com.crackedgames.craftics.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Helper for routing battle loot to a player.
 *
 * <p>Stackable post-battle loot is first offered to any bundles the player is
 * carrying, spilling into the next bundle when one is full. Anything that
 * remains (or that could not enter a bundle because no bundle has room) is
 * handed to {@link PlayerInventory#insertStack(ItemStack)}, matching vanilla's
 * normal insertion fallback.
 *
 * <p>Unstackable items ({@code stack.getMaxCount() == 1} — swords, armor,
 * totems, goat horns, music discs, shulker boxes, etc.) always bypass bundles
 * and go straight to the inventory.
 */
public final class LootDelivery {
    private LootDelivery() {}

    /**
     * Routes battle loot to the player, preferring bundles for stackable items.
     * Call instead of {@code player.getInventory().insertStack(stack)} whenever
     * granting combat rewards, event rewards, or post-victory loot.
     */
    public static void deliver(ServerPlayerEntity player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        PlayerInventory inv = player.getInventory();

        // Unstackable items (max count 1) must never go into bundles.
        if (stack.getMaxCount() == 1) {
            inv.insertStack(stack);
            return;
        }

        // Try every bundle in the inventory until the loot stack is drained.
        int size = inv.size();
        for (int i = 0; i < size; i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty() || !(slot.getItem() instanceof BundleItem)) {
                continue;
            }

            BundleContentsComponent current = slot.getOrDefault(
                    DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT);
            BundleContentsComponent.Builder builder = new BundleContentsComponent.Builder(current);
            int added = builder.add(stack);
            if (added > 0) {
                slot.set(DataComponentTypes.BUNDLE_CONTENTS, builder.build());
                stack.decrement(added);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }

        // Anything bundles could not absorb falls back to normal insertion.
        if (!stack.isEmpty()) {
            inv.insertStack(stack);
        }
    }
}
