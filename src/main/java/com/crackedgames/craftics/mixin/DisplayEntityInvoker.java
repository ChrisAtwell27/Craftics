package com.crackedgames.craftics.mixin;

import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches DisplayEntity's private setters: billboard mode for the infinite-score hologram, and
 * transformation / teleport / interpolation / brightness for the lootbox reveal's rising item
 * (see {@code LootboxReveal}).
 */
@Mixin(DisplayEntity.class)
public interface DisplayEntityInvoker {
    @Invoker("setBillboardMode")
    void craftics$setBillboardMode(DisplayEntity.BillboardMode mode);

    @Invoker("setTransformation")
    void craftics$setTransformation(AffineTransformation transformation);

    @Invoker("setTeleportDuration")
    void craftics$setTeleportDuration(int ticks);

    @Invoker("setInterpolationDuration")
    void craftics$setInterpolationDuration(int ticks);

    @Invoker("setBrightness")
    void craftics$setBrightness(Brightness brightness);
}
