package com.crackedgames.craftics.item;

import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Keeps the Move item locked into the player's chosen hotbar slot. The Move
 * item is auto-restocked, can't be dropped or moved out of its slot, and the
 * slot itself can be rotated left/right by the player via a keybind.
 *
 * <p>Call {@link #enforce(ServerPlayerEntity)} from any hook that runs
 * frequently (player tick, join, combat enter/exit). It's idempotent and
 * cheap when nothing needs fixing.
 */
public final class MoveSlotManager {

    private MoveSlotManager() {}

    /** True if the stack is the Move item. */
    public static boolean isMoveStack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModItems.MOVE_ITEM;
    }

    /**
     * Run every tick: ensure exactly one Move item exists in the player's
     * inventory and that it lives in {@code pd.lockedMoveSlot}. If it drifted
     * elsewhere (the player tried to drag it in a GUI, dropped it, etc.) it's
     * teleported back, swapping whatever currently occupies the locked slot
     * into the source slot so nothing is lost.
     */
    public static void enforce(ServerPlayerEntity player) {
        if (player == null) return;
        ServerWorld world = player.getEntityWorld() instanceof ServerWorld sw ? sw : null;
        if (world == null) return;
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());

        boolean inCombat = com.crackedgames.craftics.combat.CombatManager.get(player).isActive();
        PlayerInventory inv = player.getInventory();

        if (!inCombat) {
            // Outside combat the Move item shouldn't exist at all — strip any
            // copies (including a player attempt to Q-drop and dupe).
            for (int i = 0; i < inv.size(); i++) {
                if (isMoveStack(inv.getStack(i))) {
                    inv.setStack(i, ItemStack.EMPTY);
                }
            }
            return;
        }

        int locked = clampSlot(pd.lockedMoveSlot);
        ItemStack lockedStack = inv.getStack(locked);

        if (isMoveStack(lockedStack)) {
            // Nothing to do — and ensure any stray duplicates are merged out.
            removeStrayDuplicates(inv, locked);
            return;
        }

        // Find a Move item elsewhere in the inventory and swap it back home.
        int strayIdx = findMoveSlot(inv);
        if (strayIdx >= 0) {
            ItemStack moveStack = inv.getStack(strayIdx);
            inv.setStack(strayIdx, lockedStack);
            inv.setStack(locked, moveStack);
            removeStrayDuplicates(inv, locked);
            return;
        }

        // No Move item anywhere — create one. If the locked slot is occupied,
        // push the displaced item into the first empty slot (or drop it as a
        // last resort so the Move always wins its slot).
        if (!lockedStack.isEmpty()) {
            int empty = inv.getEmptySlot();
            if (empty >= 0) {
                inv.setStack(empty, lockedStack);
            } else {
                player.dropItem(lockedStack, false);
            }
        }
        inv.setStack(locked, MoveItem.newStack());
    }

    /**
     * Rotate the locked slot by one hotbar position. Wraps within 0..8. The
     * stack currently at the destination is swapped into the slot the Move
     * item just vacated so the player's hotbar layout shifts cleanly.
     */
    public static void shift(ServerPlayerEntity player, int direction) {
        if (player == null || direction == 0) return;
        ServerWorld world = player.getEntityWorld() instanceof ServerWorld sw ? sw : null;
        if (world == null) return;
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());

        int oldSlot = clampSlot(pd.lockedMoveSlot);
        int step = Integer.signum(direction);
        int newSlot = ((oldSlot + step) % 9 + 9) % 9;
        if (newSlot == oldSlot) return;

        PlayerInventory inv = player.getInventory();
        ItemStack moveStack = inv.getStack(oldSlot);
        if (!isMoveStack(moveStack)) {
            // Out of sync — re-enforce first, then retry the shift on a fresh tick.
            enforce(player);
            return;
        }
        ItemStack neighbor = inv.getStack(newSlot);
        inv.setStack(newSlot, moveStack);
        inv.setStack(oldSlot, neighbor);

        pd.lockedMoveSlot = newSlot;
        data.markDirty();
    }

    /** Strip any Move items from slots other than {@code keepSlot}. */
    private static void removeStrayDuplicates(PlayerInventory inv, int keepSlot) {
        for (int i = 0; i < inv.size(); i++) {
            if (i == keepSlot) continue;
            if (isMoveStack(inv.getStack(i))) {
                inv.setStack(i, ItemStack.EMPTY);
            }
        }
    }

    /** First inventory slot holding a Move item, or -1. */
    private static int findMoveSlot(PlayerInventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            if (isMoveStack(inv.getStack(i))) return i;
        }
        return -1;
    }

    private static int clampSlot(int slot) {
        if (slot < 0) return 0;
        if (slot > 8) return 8;
        return slot;
    }
}
