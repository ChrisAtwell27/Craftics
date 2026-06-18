package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.anim.CrafticsAnimHolder;
import com.crackedgames.craftics.component.CrafticsAnimComponent;
import com.crackedgames.craftics.component.CrafticsComponents;
//? if >=1.21.3 {
/*import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
*///?}
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On 1.21.3+, copies the Craftics animation component from a mob onto its
 * render-state snapshot during {@code updateRenderState} so the per-frame model
 * pose (see {@code BipedAnimMixin}) can read it without holding an entity ref.
 */
//? if >=1.21.3 {
/*@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererAnimMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void craftics$copyAnim(LivingEntity entity, LivingEntityRenderState state,
                                   float tickDelta, CallbackInfo ci) {
        if (!entity.getCommandTags().contains("craftics_arena")) {
            CrafticsAnimHolder h = (CrafticsAnimHolder) state;
            h.craftics$setAnimState(com.crackedgames.craftics.combat.animation.AnimState.IDLE);
            h.craftics$setAnimTicks(0f);
            return;
        }
        CrafticsAnimComponent comp = CrafticsComponents.ANIM.getNullable(entity);
        if (comp == null) return;
        CrafticsAnimHolder h = (CrafticsAnimHolder) state;
        h.craftics$setAnimState(comp.getState());
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = mc.world != null ? mc.world.getTime() : 0L;
        h.craftics$setAnimTicks(Math.max(0f, (now - comp.getStartTick()) + tickDelta));
    }
}
*///?} else {
@Mixin(LivingEntity.class)
public abstract class LivingEntityRendererAnimMixin {
    // No-op on 1.21.1: entity is available directly in BipedAnimMixin#setAngles,
    // so no state-snapshot pre-population is needed.
}
//?}
