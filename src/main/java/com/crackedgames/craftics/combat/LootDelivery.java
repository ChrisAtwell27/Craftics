package com.crackedgames.craftics.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Random;

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
     * Pool of vanilla tipped-arrow potion variants used when loot tables drop
     * bare {@code Items.TIPPED_ARROW} stacks without any PotionContents set.
     */
    private static final List<RegistryEntry<Potion>> TIPPED_ARROW_POTIONS = List.of(
        Potions.POISON, Potions.SLOWNESS, Potions.WEAKNESS, Potions.HARMING
    );

    /** Pool for drinkable potions (curative / buff). */
    private static final List<RegistryEntry<Potion>> DRINKABLE_POTIONS = List.of(
        Potions.HEALING, Potions.REGENERATION, Potions.STRENGTH,
        Potions.SWIFTNESS, Potions.FIRE_RESISTANCE
    );

    /** Pool for splash + lingering potions (offensive / debuff). */
    private static final List<RegistryEntry<Potion>> THROWN_POTIONS = List.of(
        Potions.HARMING, Potions.POISON, Potions.SLOWNESS, Potions.WEAKNESS
    );

    private static final Random RANDOM = new Random();

    /**
     * Routes battle loot to the player, preferring bundles for stackable items.
     * Call instead of {@code player.getInventory().insertStack(stack)} whenever
     * granting combat rewards, event rewards, or post-victory loot.
     *
     * @return the portion of {@code stack} that did not fit anywhere — empty if
     *         the loot was fully delivered.
     */
    public static ItemStack deliver(ServerPlayerEntity player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Emeralds are the virtual currency tracked in PlayerData and spent at
        // traders/shrines. Letting them land as inventory items breaks the
        // economy — players hoard physical stacks instead of seeing them in
        // their balance, and the trader's emerald-collection sweep then misses
        // anything that wasn't given by the trader event itself. Route to the
        // virtual balance and consume the item.
        if (stack.getItem() == Items.EMERALD) {
            if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                var data = com.crackedgames.craftics.world.CrafticsSavedData.get(sw);
                data.getPlayerData(player.getUuid()).addEmeralds(stack.getCount());
                data.markDirty();
            }
            return ItemStack.EMPTY;
        }

        // Loot tables build tipped arrows via `new ItemStack(Items.TIPPED_ARROW, n)`
        // which has no PotionContents component, so they render as grey arrows with
        // no effect when shot. Assign a random vanilla variant at delivery time so
        // every loot source (mob drops, biome completion loot) produces a working
        // tipped arrow without needing per-call-site fixup.
        if (stack.getItem() == Items.TIPPED_ARROW) {
            PotionContentsComponent existing = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (existing == null || !existing.hasEffects()) {
                RegistryEntry<Potion> pick = TIPPED_ARROW_POTIONS.get(
                    RANDOM.nextInt(TIPPED_ARROW_POTIONS.size()));
                stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(pick));
            }
        }

        // Same pre-fill for bare potion items — loot tables that build
        // `new ItemStack(Items.SPLASH_POTION, 1)` with no PotionContents
        // otherwise render as "Uncraftable Potion" with no effect when used.
        // Drinkable potions roll from the curative/buff pool; splash and
        // lingering potions roll from the offensive/debuff pool so the
        // item is contextually useful when delivered as combat loot.
        if (stack.getItem() == Items.POTION || stack.getItem() == Items.SPLASH_POTION
                || stack.getItem() == Items.LINGERING_POTION) {
            PotionContentsComponent existing = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (existing == null || !existing.hasEffects()) {
                List<RegistryEntry<Potion>> pool = (stack.getItem() == Items.POTION)
                    ? DRINKABLE_POTIONS : THROWN_POTIONS;
                RegistryEntry<Potion> pick = pool.get(RANDOM.nextInt(pool.size()));
                stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(pick));
            }
        }

        PlayerInventory inv = player.getInventory();

        // Unstackable items (max count 1) must never go into bundles.
        if (stack.getMaxCount() == 1) {
            inv.insertStack(stack);
            return stack;
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
                    return ItemStack.EMPTY;
                }
            }
        }

        // Anything bundles could not absorb falls back to normal insertion.
        if (!stack.isEmpty()) {
            inv.insertStack(stack);
        }
        return stack;
    }
}
