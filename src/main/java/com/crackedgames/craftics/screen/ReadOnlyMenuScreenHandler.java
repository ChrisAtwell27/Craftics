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

    /**
     * Menu closing is the moment anything extracted by a path the overrides below can't
     * see (a sort mod's server component writing straight into the player's slots) would
     * otherwise become permanent. Icons are marked at build time, so destroy any the
     * player is holding - the periodic server sweep is the backstop for everything else.
     */
    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            MenuIcons.purge(sp);
        }
    }

    /**
     * Closes the PICKUP_ALL hole: a double-click collect STARTED on a player-inventory slot
     * never lands in {@link #onSlotClick}'s menu-region guard (the clicked slot is the
     * player's), yet vanilla's collect loop then sweeps matching stacks out of EVERY slot of
     * the handler - menu icons included. Inventory-sort mods fire exactly that action during
     * their merge pass, letting players pull the odds-preview icons into their inventory.
     * Vanilla consults this method per victim slot, so refusing the menu region stops both
     * collecting from it and dragging into it.
     */
    @Override
    public boolean canInsertIntoSlot(ItemStack stack, net.minecraft.screen.slot.Slot slot) {
        if (slot.id < menuSlotCount) return false;
        return super.canInsertIntoSlot(stack, slot);
    }

    @Override
    public boolean canInsertIntoSlot(net.minecraft.screen.slot.Slot slot) {
        if (slot.id < menuSlotCount) return false;
        return super.canInsertIntoSlot(slot);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < menuSlotCount) {
            // ONLY a plain left/right click counts as pressing the button. QUICK_MOVE and
            // SWAP used to count too, but inventory-sort mods' "loot all" fires QUICK_MOVE
            // at every container slot in one batch - on a confirm menu that batch walks the
            // slots and presses whatever it reaches, including "spend emeralds" buttons. A
            // menu button being shift-clicked deliberately is rarer than a sort mod being
            // installed, so the callback keeps only the unambiguous action.
            if (actionType == SlotActionType.PICKUP && onClick != null) {
                onClick.accept(slotIndex);
            }
            // Everything touching a menu slot is cancelled server-side, but the CLIENT has
            // already predicted the vanilla outcome (icon moved into the inventory) and a
            // swallowed click leaves player-inventory slots' predictions uncorrected - the
            // icons then sit in the inventory as convincing ghost items. Resync the whole
            // handler so the rollback is immediate.
            this.updateToClient();
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }
}
