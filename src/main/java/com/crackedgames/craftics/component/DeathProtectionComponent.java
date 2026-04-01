package com.crackedgames.craftics.component;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.ladysnake.cca.api.v3.entity.RespawnableComponent;

/**
 * Stores a one-time inventory snapshot for recovery compass protection.
 */
public class DeathProtectionComponent implements RespawnableComponent<DeathProtectionComponent> {
    private boolean pendingRestore = false;
    private NbtList savedInventory = new NbtList();
    private int selectedSlot = 0;

    public boolean hasPendingRestore() {
        return pendingRestore;
    }

    public boolean armFromInventory(ServerPlayerEntity player) {
        if (!consumeRecoveryCompass(player)) return false;

        pendingRestore = true;
        savedInventory = player.getInventory().writeNbt(new NbtList());
        selectedSlot = player.getInventory().selectedSlot;
        return true;
    }

    public void restoreTo(ServerPlayerEntity player) {
        if (!pendingRestore) return;

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.readNbt(savedInventory);
        inventory.selectedSlot = Math.max(0, Math.min(selectedSlot, PlayerInventory.getHotbarSize() - 1));
        inventory.markDirty();
        clear();
    }

    public void clear() {
        pendingRestore = false;
        savedInventory = new NbtList();
        selectedSlot = 0;
    }

    public static boolean hasRecoveryCompass(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(Items.RECOVERY_COMPASS)) return true;
        }
        return false;
    }

    public static boolean protectCombatInventory(ServerPlayerEntity player) {
        return consumeRecoveryCompassNow(player);
    }

    public static boolean consumeRecoveryCompassNow(ServerPlayerEntity player) {
        return consumeRecoveryCompass(player);
    }

    private static boolean consumeRecoveryCompass(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isOf(Items.RECOVERY_COMPASS)) continue;
            stack.decrement(1);
            if (stack.isEmpty()) {
                inventory.setStack(slot, ItemStack.EMPTY);
            }
            inventory.markDirty();
            return true;
        }
        return false;
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        pendingRestore = tag.getBoolean("pendingRestore");
        selectedSlot = tag.getInt("selectedSlot");
        savedInventory = tag.contains("savedInventory", 9) ? tag.getList("savedInventory", 10) : new NbtList();
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean("pendingRestore", pendingRestore);
        tag.putInt("selectedSlot", selectedSlot);
        tag.put("savedInventory", savedInventory.copy());
    }
}