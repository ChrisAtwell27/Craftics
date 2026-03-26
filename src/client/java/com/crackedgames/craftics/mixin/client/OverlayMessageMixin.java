package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatLog;
import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts overlay (action bar) messages during combat and feeds them
 * to the CombatLog for persistent display.
 */
@Mixin(InGameHud.class)
public class OverlayMessageMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void craftics$captureOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        if (CombatState.isInCombat()) {
            CombatLog.addMessage(message.getString());
        }
    }
}
