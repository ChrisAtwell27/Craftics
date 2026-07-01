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
        // Keep the cursor free during combat AND during a merchant scene. A scene has a
        // free-walk phase (currentScreen == null) where the player aims floor-tile raycasts,
        // so vanilla must not re-lock the cursor each frame. isInScene() is the only non-combat
        // state with that phase (combat events always have a Screen open when the cursor matters).
        if (CombatState.isInCombat() || CombatState.isInScene()) {
            ci.cancel();
        }
    }
}
