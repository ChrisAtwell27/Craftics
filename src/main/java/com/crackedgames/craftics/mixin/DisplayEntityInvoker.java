package com.crackedgames.craftics.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reaches DisplayEntity's private billboard setter for the infinite-score hologram. */
@Mixin(DisplayEntity.class)
public interface DisplayEntityInvoker {
    @Invoker("setBillboardMode")
    void craftics$setBillboardMode(DisplayEntity.BillboardMode mode);
}
