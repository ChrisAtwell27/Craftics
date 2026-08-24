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
 * <p>1.21.1 AND 1.21.3 have {@code KeyboardInput.tick(boolean slowDown, float slowDownFactor)};
 * <b>1.21.4+</b> simplified this to {@code tick()} with no parameters. Both the mixin selector
 * and the injector method signature have to change accordingly.
 *
 * <p>This comment used to say 1.21.2, and the guard below was written to match it, so 1.21.3
 * asked for a {@code tick()V} that does not exist there. Nothing caught it: the selector is a
 * STRING, so every shard compiled, and a dev {@code runClient} runs against named mappings
 * where Mixin's permissive pass degrades {@code tick()V} to {@code tick} and finds the real
 * method anyway. The shipped jar is remapped to intermediary, where that fallback has nothing
 * to match - the 1.21.3 jar carried the raw string {@code method=["tick()V"]} instead of
 * {@code method_3129} and died on world join with a critical injection failure.
 *
 * <p>The field guards below are on their own boundaries and are NOT the same one: the
 * {@code pressing*} fields are 1.21.1-only, {@code movementForward}/{@code movementSideways}
 * survive through 1.21.4, and 1.21.5 replaces them with {@code movementVector}.
 */
@Mixin(KeyboardInput.class)
public class MovementDisableMixin {

    //? if <=1.21.3 {
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void craftics$disableMovementInCombat(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        craftics$clearMovement();
    }
    //?} else {
    /*@Inject(method = "tick()V", at = @At("TAIL"))
    private void craftics$disableMovementInCombat(CallbackInfo ci) {
        craftics$clearMovement();
    }
    *///?}

    private void craftics$clearMovement() {
        if (!CombatState.isInCombat() && !CombatState.isCinematicActive()) return;
        Input input = (Input)(Object)this;
        //? if <=1.21.1 {
        input.pressingForward = false;
        input.pressingBack = false;
        input.pressingLeft = false;
        input.pressingRight = false;
        input.jumping = false;
        input.sneaking = false;
        //?} else {
        /*input.playerInput = new net.minecraft.util.PlayerInput(false, false, false, false, false, false, false);
        *///?}
        //? if <=1.21.4 {
        input.movementForward = 0f;
        input.movementSideways = 0f;
        //?} else
        /*((InputAccessor)(Object)input).craftics$setMovementVector(net.minecraft.util.math.Vec2f.ZERO);*/
    }
}
