package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses WASD movement while Craftics combat is active, so the player
 * cannot walk off the arena grid.
 *
 * <p>1.21.1 had {@code KeyboardInput.tick(boolean slowDown, float slowDownFactor)};
 * 1.21.2+ simplified this to {@code tick()} with no parameters. Both the mixin
 * selector and the injector method signature have to change accordingly.
 */
@Mixin(KeyboardInput.class)
public class MovementDisableMixin {

    //? if <=1.21.1 {
    /*@Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void craftics$disableMovementInCombat(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        craftics$clearMovement();
    }
    *///?} else {
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void craftics$disableMovementInCombat(CallbackInfo ci) {
        craftics$clearMovement();
    }
    //?}

    private void craftics$clearMovement() {
        if (!CombatState.isInCombat()) return;
        Input input = (Input)(Object)this;
        //? if <=1.21.1 {
        /*input.pressingForward = false;
        input.pressingBack = false;
        input.pressingLeft = false;
        input.pressingRight = false;
        input.jumping = false;
        input.sneaking = false;
        *///?} else {
        input.playerInput = new net.minecraft.util.PlayerInput(false, false, false, false, false, false, false);
        //?}
        //? if <=1.21.4 {
        input.movementForward = 0f;
        input.movementSideways = 0f;
        //?} else
        /*((InputAccessor)(Object)input).craftics$setMovementVector(net.minecraft.util.math.Vec2f.ZERO);*/
    }
}
