package com.crackedgames.craftics.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * The "Move" item: a locked-slot tool that puts the player into Move mode while
 * held. Persistent (always restocked into the locked hotbar slot by
 * {@link MoveSlotManager}); cannot be dropped or moved out of its slot.
 */
public class MoveItem extends Item {
    public MoveItem(Settings settings) {
        super(settings);
    }

    /** Build a fresh stack with the Move display name baked in. */
    public static ItemStack newStack() {
        ItemStack stack = new ItemStack(ModItems.MOVE_ITEM);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
            Text.literal("§aMove"));
        return stack;
    }
}
