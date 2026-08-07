package com.crackedgames.craftics.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;

import java.util.function.IntConsumer;

/**
 * A vanilla generic container screen handler whose top inventory is a read-only menu of
 * icon items rather than real storage. Subclassing the vanilla type - instead of registering
 * a brand new {@code ScreenHandlerType} - means the client renders it with the built-in
 * generic container screen: no custom client Screen class, no networking, nothing beyond
 * what {@link net.minecraft.screen.SimpleNamedScreenHandlerFactory} already sends.
 *
 * <p>Every click that lands on a top slot is swallowed: nothing can be taken out of, or
 * shifted into, the menu region. For the click types a player actually uses to interact with
 * a menu - left/right click, shift-click, and the number-key swap - the slot index is instead
 * handed to {@code onClick} so the caller can treat it as "the player picked this icon" (the
 * same way the reference Bukkit menus cancel every click and switch on the raw slot). Drags,
 * throws and quick-craft touching a menu slot are just cancelled with no callback, since
 * those aren't really "clicks" on one icon.
 *
 * <p>The player's own inventory below the menu is untouched and behaves normally, except
 * that nothing can be shift-clicked up into the read-only region ({@link #quickMove} always
 * refuses).
 */
public class ReadOnlyMenuScreenHandler extends GenericContainerScreenHandler {

    private final int menuSlotCount;
    private final IntConsumer onClick;

    /**
     * @param type   {@code ScreenHandlerType.GENERIC_9X1} through {@code GENERIC_9X6} -
     *               must match {@code rows}, since that's what tells the client which size
     *               of vanilla chest screen to draw.
     * @param rows   how many 9-wide rows the menu inventory occupies.
     * @param onClick called with the 0-based slot index (within the menu, not the combined
     *                screen handler slot list) whenever a menu slot is clicked, swapped, or
     *                shift-clicked. Never called for the player's own inventory slots.
     */
    public ReadOnlyMenuScreenHandler(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory,
                                     Inventory menuInventory, int rows, IntConsumer onClick) {
        super(type, syncId, playerInventory, menuInventory, rows);
        this.menuSlotCount = rows * 9;
        this.onClick = onClick;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // The menu region never receives anything and never gives anything up; the player's
        // own inventory slots simply don't shift-move anywhere when there's no valid target.
        return ItemStack.EMPTY;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < menuSlotCount) {
            boolean isClick = actionType == SlotActionType.PICKUP
                || actionType == SlotActionType.QUICK_MOVE
                || actionType == SlotActionType.SWAP;
            if (isClick && onClick != null) {
                onClick.accept(slotIndex);
            }
            // Anything else touching a menu slot (drag, throw, quick-craft) is cancelled with
            // no callback - never let vanilla move, drop, or place anything in an icon slot.
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }
}
