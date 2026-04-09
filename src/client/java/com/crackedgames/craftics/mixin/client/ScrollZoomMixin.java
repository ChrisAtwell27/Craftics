package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts scroll wheel during combat. Shift+scroll zooms the camera;
 * plain scroll falls through to vanilla hotbar selection.
 */
@Mixin(Mouse.class)
public class ScrollZoomMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void craftics$handleCombatZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!CombatState.isInCombat()) return;

        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean shiftHeld = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (shiftHeld) {
            // Zoom: positive scroll = zoom in (decrease distance), negative = zoom out
            CombatState.zoom((float) vertical * 1.5f);
            ci.cancel();
        }
        // Otherwise let scroll fall through to vanilla hotbar selection
    }
}
