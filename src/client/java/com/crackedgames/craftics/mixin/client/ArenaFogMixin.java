package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CrafticsFog;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if <=1.21.1 {
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import net.minecraft.client.render.Fog;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * Pulls the fog band in around a Craftics build so the void beyond a pasted schematic reads as
 * fog instead of empty sky. See {@link CrafticsFog} for the band math and the activation rule;
 * this mixin only applies what that resolves.
 *
 * <p><b>Distance only, terrain pass only.</b> Vanilla's fog COLOUR is carried through untouched,
 * and the SKY pass is left alone entirely - it shades the sky dome with this same fog, so both
 * tinting the colour and shortening the band there repaint the horizon, which reads as the sky
 * having gone wrong rather than as fog. Arena colour is the cloud geometry's job
 * ({@link com.crackedgames.craftics.client.CloudSeaRenderer}), where it can't touch the sky.
 *
 * <p>Because only the terrain pass is hooked, it runs exactly once per frame, which is also the
 * cadence {@link CrafticsFog}'s ease expects.
 *
 * <p>Shard split: on 1.21.1 {@code applyFog} pushes the band straight into the shader uniforms, so
 * the override is a TAIL inject that rewrites them. On 1.21.3+ it returns an immutable {@code Fog}
 * record, so the override swaps in a new one carrying the same colour.
 */
@Mixin(BackgroundRenderer.class)
public class ArenaFogMixin {

    //? if <=1.21.1 {
    @Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/BackgroundRenderer$FogType;FZF)V",
            at = @At("TAIL"))
    private static void craftics$tightenFog(Camera camera, BackgroundRenderer.FogType fogType,
            float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        // TERRAIN pass only. The SKY pass uses the same fog to shade the sky dome, so touching it
        // repaints the horizon - which is what made the sky look wrong.
        if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN) return;
        float[] band = CrafticsFog.resolve(camera,
            RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), true);
        if (band == null) return;
        RenderSystem.setShaderFogStart(band[0]);
        RenderSystem.setShaderFogEnd(band[1]);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
    //?} else {
    /*@Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/BackgroundRenderer$FogType;Lorg/joml/Vector4f;FZF)Lnet/minecraft/client/render/Fog;",
            at = @At("RETURN"), cancellable = true)
    private static void craftics$tightenFog(Camera camera, BackgroundRenderer.FogType fogType,
            Vector4f color, float viewDistance, boolean thickFog, float tickDelta,
            CallbackInfoReturnable<Fog> cir) {
        Fog fog = cir.getReturnValue();
        if (fog == null) return;
        // TERRAIN pass only - the SKY pass shades the sky dome with this same fog, and
        // overriding it there repaints the horizon.
        if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN) return;
        float[] band = CrafticsFog.resolve(camera, fog.start(), fog.end(), true);
        if (band == null) return;
        // Distance only - vanilla's colour is carried over untouched, so the band always agrees
        // with the sky instead of tinting against it.
        cir.setReturnValue(new Fog(band[0], band[1], FogShape.SPHERE,
            fog.red(), fog.green(), fog.blue(), fog.alpha()));
    }
    *///?}
}
