package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.anim.CrafticsAnimHolder;
import com.crackedgames.craftics.combat.animation.AnimState;
//? if >=1.21.3 {
/*import net.minecraft.client.render.entity.state.LivingEntityRenderState;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Stores the Craftics anim pose on {@code LivingEntityRenderState}. Only
 * applies on 1.21.3+ where model {@code setAngles} receives a render-state
 * snapshot — on 1.21.1 the entity is passed directly and this mixin is a no-op.
 */
//? if >=1.21.3 {
/*@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateAnimMixin implements CrafticsAnimHolder {
    @Unique private AnimState craftics$animState = AnimState.IDLE;
    @Unique private float craftics$animTicks = 0f;

    @Override public AnimState craftics$getAnimState() { return craftics$animState; }
    @Override public void craftics$setAnimState(AnimState s) { this.craftics$animState = s; }
    @Override public float craftics$getAnimTicks() { return craftics$animTicks; }
    @Override public void craftics$setAnimTicks(float t) { this.craftics$animTicks = t; }
}
*///?} else {
@Mixin(net.minecraft.entity.LivingEntity.class)
public abstract class LivingEntityRenderStateAnimMixin {
    // No-op on 1.21.1: anim state is read directly from the entity in BipedAnimMixin.
    // Targeting LivingEntity (which exists on every shard) keeps this class loadable
    // without affecting behavior — no @Inject / @Redirect members are declared.
}
//?}
