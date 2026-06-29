package com.crackedgames.craftics.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ESC fallback: pressing Escape closes any Craftics screen, even the ones that
 * deliberately return {@code shouldCloseOnEsc() == false} to force a choice
 * (Victory, Game Over, dialogue, reward reveal, level-up). This is a safety valve
 * so a player can always dismiss a menu that's stuck open. Container
 * ({@link HandledScreen}) screens already manage their own ESC handling, so they're
 * left untouched.
 */
@Mixin(Screen.class)
public class CrafticsEscCloseMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void craftics$escCloseFallback(int keyCode, int scanCode, int modifiers,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (keyCode != 256) return; // GLFW_KEY_ESCAPE
        Object self = (Object) this;
        if (self instanceof HandledScreen) return; // container screens handle their own ESC
        if (!self.getClass().getName().startsWith("com.crackedgames.craftics")) return;
        MinecraftClient.getInstance().setScreen(null);
        cir.setReturnValue(true);
    }
}
