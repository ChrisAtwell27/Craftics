package com.crackedgames.craftics.mixin.client;

import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Companion to {@link RenderLayerInvoker}: {@code MultiPhaseParameters.Builder
 * #build(boolean)} is protected, and 1.21.5 offers no public way to obtain a
 * parameters instance for a custom layer. Empty on older versions (which don't
 * build a custom layer at all).
 */
@Mixin(RenderLayer.MultiPhaseParameters.Builder.class)
public interface RenderPhaseBuilderInvoker {
    //? if >=1.21.5 {
    @org.spongepowered.asm.mixin.gen.Invoker("build")
    RenderLayer.MultiPhaseParameters craftics$build(boolean affectsOutline);
    //?}
}
