package com.crackedgames.craftics.screen;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Marks read-only menu icons so they can be destroyed anywhere they escape to.
 *
 * <p>The {@link ReadOnlyMenuScreenHandler} refuses every VANILLA path out of a menu, but
 * inventory-management mods with a server component (ClientSort's server acceleration, for
 * one) move stacks by writing directly into the player's inventory slots server-side -
 * there is no handler method on the menu side that can refuse a write to somebody else's
 * slot, and expecting every such mod to be configured correctly on every server is not a
 * defence. So instead of trying to enumerate exits, the icons themselves are poisoned:
 * every icon carries an invisible marker component, and the server deletes marked items
 * from player inventories - immediately when a menu closes, and on the regular sweep for
 * anything that slips through at any other moment. Whatever manages to extract one is
 * holding it for at most a couple of seconds.
 *
 * <p>The marker lives in {@code CUSTOM_DATA} (same as {@link
 * com.crackedgames.craftics.item.SeasonStamp}), so it never renders in a tooltip and
 * survives every form of item movement - which is exactly what makes the purge reliable.
 */
public final class MenuIcons {

    private MenuIcons() {}

    /** Key inside the item's custom data. Namespaced so nothing else collides with it. */
    private static final String ICON_KEY = "craftics_menu_icon";

    /** Mark a stack as a menu icon, leaving any other custom data alone. */
    public static void mark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack,
            nbt -> nbt.putBoolean(ICON_KEY, true));
    }

    /** True when this stack is (or was) a read-only menu icon. */
    public static boolean isIcon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return false;
        NbtCompound nbt = data.copyNbt();
        //? if <=1.21.4 {
        return nbt.contains(ICON_KEY) && nbt.getBoolean(ICON_KEY);
        //?} else {
        /*return nbt.getBoolean(ICON_KEY, false);
        *///?}
    }

    /**
     * Delete every marked icon this player is holding - inventory and cursor.
     *
     * @return how many stacks were destroyed
     */
    public static int purge(ServerPlayerEntity player) {
        if (player == null) return 0;
        int removed = 0;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (isIcon(inv.getStack(i))) {
                inv.setStack(i, ItemStack.EMPTY);
                removed++;
            }
        }
        if (player.currentScreenHandler != null
                && isIcon(player.currentScreenHandler.getCursorStack())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            removed++;
        }
        if (removed > 0) {
            player.currentScreenHandler.sendContentUpdates();
        }
        return removed;
    }
}
