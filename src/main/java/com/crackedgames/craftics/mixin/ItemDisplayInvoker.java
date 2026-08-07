package com.crackedgames.craftics.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reaches ItemDisplayEntity's private item setter for the lootbox reveal's rising item. */
@Mixin(DisplayEntity.ItemDisplayEntity.class)
public interface ItemDisplayInvoker {
    @Invoker("setItemStack")
    void craftics$setItemStack(ItemStack stack);
}
