package com.crackedgames.craftics.screen;

import com.crackedgames.craftics.block.ModScreenHandlers;
import com.crackedgames.craftics.combat.CombatManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server/client screen handler for the post-victory loot screen. The top 18
 * slots hold loot that overflowed a full inventory; below them are the player's
 * own 36 inventory slots. Items move freely between the two via drag/shift-click.
 * Loot still in the loot slots when the screen closes is discarded.
 */
public class LootManagementScreenHandler extends ScreenHandler {

    public static final int LOOT_ROWS = 2;
    public static final int LOOT_SLOTS = LOOT_ROWS * 9; // 18

    private final Inventory lootInventory;

    /** Server constructor - {@code lootInventory} is pre-filled with the overflow. */
    public LootManagementScreenHandler(int syncId, PlayerInventory playerInventory, Inventory lootInventory) {
        super(ModScreenHandlers.LOOT_MANAGEMENT_SCREEN_HANDLER, syncId);
        checkSize(lootInventory, LOOT_SLOTS);
        this.lootInventory = lootInventory;
        lootInventory.onOpen(playerInventory.player);

        // Loot slots (2 rows of 9)
        for (int row = 0; row < LOOT_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(lootInventory, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        // Player main inventory (3 rows)
        int invTop = LOOT_ROWS * 18 + 31;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invTop + row * 18));
            }
        }
        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, invTop + 58));
        }
    }

    /** Client constructor - the loot inventory starts empty and is filled by slot sync. */
    public LootManagementScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(LOOT_SLOTS));
    }

    /** Number of loot slots still holding an item - used for the close confirmation. */
    public int remainingLootCount() {
        int n = 0;
        for (int i = 0; i < LOOT_SLOTS; i++) {
            if (!lootInventory.getStack(i).isEmpty()) n++;
        }
        return n;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stackInSlot = slot.getStack();
            moved = stackInSlot.copy();
            if (index < LOOT_SLOTS) {
                // loot -> player inventory
                if (!this.insertItem(stackInSlot, LOOT_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // player inventory -> loot
                if (!this.insertItem(stackInSlot, 0, LOOT_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stackInSlot.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return moved;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        // Re-home the cursor stack into the player inventory before vanilla can
        // drop it into the world (the hub is a void). Discard if there's no room.
        ItemStack cursor = getCursorStack();
        if (!cursor.isEmpty()) {
            player.getInventory().insertStack(cursor);
            setCursorStack(ItemStack.EMPTY);
        }
        super.onClosed(player);
        // Loot still in the loot inventory is left behind - discarded with the
        // transient inventory. Task 4 adds the combat-continuation notification here.
        if (player instanceof ServerPlayerEntity sp) {
            CombatManager cm = CombatManager.getActiveCombat(sp.getUuid());
            if (cm != null) {
                cm.handleLootScreenClosed(sp);
            }
        }
    }
}
