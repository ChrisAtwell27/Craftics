package com.crackedgames.craftics.mixin.client;

import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
    //? if <=1.21.4 {
    @Accessor("movementForward")
    void setMovementForward(float val);

    @Accessor("movementSideways")
    void setMovementSideways(float val);
    //?} else {
    /*@Accessor("movementVector")
    void craftics$setMovementVector(net.minecraft.util.math.Vec2f vec);
    *///?}
}
