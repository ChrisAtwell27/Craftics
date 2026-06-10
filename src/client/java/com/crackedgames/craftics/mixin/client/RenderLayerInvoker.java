package com.crackedgames.craftics.mixin.client;

import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 1.21.5's render-pipeline refactor made {@code RenderLayer.of(...)}
 * package-private, so the tile overlay's see-through ghost layer needs an
 * invoker to construct its custom layer. Older versions draw that pass with
 * direct RenderSystem depth state instead and leave this mixin empty.
 */
@Mixin(RenderLayer.class)
public interface RenderLayerInvoker {
    //? if >=1.21.5 {
    @org.spongepowered.asm.mixin.gen.Invoker("of")
    static RenderLayer.MultiPhase craftics$of(String name, int bufferSize,
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
            RenderLayer.MultiPhaseParameters parameters) {
        throw new AssertionError();
    }
    //?}
}
