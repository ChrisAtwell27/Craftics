package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During combat, force GameRenderer.getFov to return the raw settings FOV
 * so that status effects (slowness, speed, blindness) don't warp the view.
 * This keeps the rendered FOV in sync with TileRaycast's FOV.
 */
@Mixin(GameRenderer.class)
public class FovLockMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void lockFovDuringCombat(Camera camera, float tickDelta, boolean changingFov,
                                     CallbackInfoReturnable<Double> cir) {
        if (CombatState.isInCombat()) {
            cir.setReturnValue((double) MinecraftClient.getInstance().options.getFov().getValue());
        }
    }
}
