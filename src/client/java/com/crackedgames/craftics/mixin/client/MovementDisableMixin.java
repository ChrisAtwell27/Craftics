package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MovementDisableMixin {

    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void craftics$disableMovementInCombat(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (CombatState.isInCombat()) {
            Input input = (Input)(Object)this;
            input.pressingForward = false;
            input.pressingBack = false;
            input.pressingLeft = false;
            input.pressingRight = false;
            input.jumping = false;
            input.sneaking = false;
            input.movementForward = 0f;
            input.movementSideways = 0f;
        }
    }
}
