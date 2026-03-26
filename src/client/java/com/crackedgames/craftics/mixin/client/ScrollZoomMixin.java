package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts scroll wheel during combat to zoom the camera
 * instead of switching hotbar slots.
 */
@Mixin(Mouse.class)
public class ScrollZoomMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void craftics$handleCombatZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (CombatState.isInCombat()) {
            // Zoom: positive scroll = zoom in (decrease distance), negative = zoom out
            CombatState.zoom((float) vertical * 1.5f);
            ci.cancel(); // prevent hotbar switching
        }
    }
}
