package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.anim.CrafticsAnimHolder;
import com.crackedgames.craftics.combat.animation.AnimState;
import com.crackedgames.craftics.component.CrafticsAnimComponent;
import com.crackedgames.craftics.component.CrafticsComponents;
import net.minecraft.client.render.entity.model.BipedEntityModel;
//? if >=1.21.3 {
/*import net.minecraft.client.render.entity.state.BipedEntityRenderState;
*///?}
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Craftics-controlled bone-angle overrides on top of vanilla idle/walk
 * for craftics_arena mobs. Injected at TAIL so vanilla has already set its
 * angles and we override last.
 *
 * <p>1.21.1 receives the live entity + 5 floats. 1.21.3+ switched to a
 * render-state snapshot (see {@link CrafticsAnimHolder} for how state is ferried).
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedAnimMixin {

    //? if <=1.21.1 {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL"))
    private void craftics$applyAnim(LivingEntity entity, float limbAngle, float limbDistance,
                                    float animationProgress, float headYaw, float headPitch,
                                    CallbackInfo ci) {
        if (!entity.getCommandTags().contains("craftics_arena")) return;
        CrafticsAnimComponent comp = CrafticsComponents.ANIM.getNullable(entity);
        if (comp == null) return;
        AnimState state = comp.getState();
        if (state == AnimState.IDLE) return;
        long now = entity.getEntityWorld().getTime();
        float ticks = Math.max(0f, (float)(now - comp.getStartTick()));
        applyPose((BipedEntityModel<?>)(Object) this, state, ticks);
    }
    //?} else {
    /*@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("TAIL"))
    private void craftics$applyAnim(BipedEntityRenderState state, CallbackInfo ci) {
        CrafticsAnimHolder h = (CrafticsAnimHolder) state;
        AnimState s = h.craftics$getAnimState();
        if (s == null || s == AnimState.IDLE) return;
        applyPose((BipedEntityModel<?>)(Object) this, s, h.craftics$getAnimTicks());
    }
    *///?}

    /**
     * Shared pose code — reads the bone fields inherited from AnimalModel via
     * the BipedEntityModel cast and nudges them by the active state. Version-
     * neutral since rightArm/leftArm/body/head exist on every shard.
     */
    private static void applyPose(BipedEntityModel<?> model, AnimState state, float ticks) {
        float t = ticks;
        switch (state) {
            case WINDUP -> {
                // Progress 0..1 over 10 ticks; arm pulls back, body leans forward
                float p = Math.min(1f, t / 10f);
                model.rightArm.pitch = -0.3f - p * 1.2f;
                model.rightArm.roll = -0.1f * p;
                model.body.pitch = 0.15f * p;
            }
            case ATTACK -> {
                // Quick forward swing
                float p = Math.min(1f, t / 4f);
                model.rightArm.pitch = -1.5f + p * 2.2f;
                model.body.pitch = 0.1f - p * 0.2f;
            }
            case RECOIL -> {
                float p = 1f - Math.min(1f, t / 8f);
                model.rightArm.pitch = 0.4f * p;
                model.body.pitch = -0.05f * p;
            }
            case HIT -> {
                // Brief flinch — head and body jerk back
                float p = 1f - Math.min(1f, t / 4f);
                model.head.pitch -= 0.35f * p;
                model.body.pitch -= 0.15f * p;
            }
            case CAST -> {
                // Both arms raised, head tilted up, gentle sway
                model.rightArm.pitch = -2.3f;
                model.leftArm.pitch = -2.3f;
                model.rightArm.roll = 0.25f;
                model.leftArm.roll = -0.25f;
                model.head.pitch = -0.4f;
                float sway = (float) Math.sin(t * 0.3f) * 0.15f;
                model.head.yaw += sway;
            }
            case ROAR -> {
                float p = Math.min(1f, t / 6f);
                model.head.pitch = -0.9f * p;
                model.body.pitch = -0.15f * p;
                model.rightArm.pitch = -0.6f * p;
                model.leftArm.pitch = -0.6f * p;
                model.rightArm.roll = -0.5f * p;
                model.leftArm.roll = 0.5f * p;
            }
            case STUNNED -> {
                model.head.pitch = 0.7f;
                model.head.yaw = (float) Math.sin(t * 0.25f) * 0.3f;
                model.rightArm.pitch = 0.45f;
                model.leftArm.pitch = 0.45f;
                model.body.pitch = 0.1f;
            }
            case IDLE -> {}
        }
    }
}
