package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseUnlockMixin {

    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void craftics$preventLockInCombat(CallbackInfo ci) {
        if (CombatState.isInCombat()) {
            ci.cancel();
        }
    }
}
