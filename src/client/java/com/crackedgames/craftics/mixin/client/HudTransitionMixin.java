package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.TransitionOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla HUD rendering (hotbar, health, etc.) while a
 * transition overlay is active, giving a clean loading screen.
 * The TransitionOverlay itself is rendered manually before cancelling.
 */
@Mixin(InGameHud.class)
public class HudTransitionMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void craftics$hideHudDuringTransition(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (TransitionOverlay.isActive()) {
            TransitionOverlay.render(context, tickCounter);
            ci.cancel();
        }
    }
}
