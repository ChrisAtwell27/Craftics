package com.crackedgames.craftics.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks applying an enchanted book to a STACK of items at the anvil.
 *
 * <p>Craftics makes several stackable items enchantable (corals as swords, sticks and rods
 * as maces). Vanilla's anvil copies the left input - count included - into the result, so
 * one book on a stack of 64 corals would enchant all 64 for a single book and one XP cost.
 * The enchanting table is already safe (its input slot caps at one item); this closes the
 * anvil half: book + a stack of more than one = no result. Renaming stacks and every other
 * anvil operation is untouched.
 *
 * <p>Slot indices are the anvil's fixed layout (0/1 inputs, 2 output), reached through the
 * public slot API rather than the ForgingScreenHandler fields - the field route needs a
 * dummy super-constructor whose signature drifted across the supported versions.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilStackEnchantMixin {

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void craftics$blockStackEnchanting(CallbackInfo ci) {
        ScreenHandler self = (ScreenHandler) (Object) this;
        ItemStack left = self.getSlot(0).getStack();
        ItemStack right = self.getSlot(1).getStack();
        if (left.getCount() > 1 && right.isOf(Items.ENCHANTED_BOOK)
                && !self.getSlot(2).getStack().isEmpty()) {
            self.getSlot(2).setStack(ItemStack.EMPTY);
        }
    }
}
