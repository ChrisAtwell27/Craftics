package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.vfx.EntityBounceState;
//? if >=1.21.3 {
/*import com.crackedgames.craftics.client.anim.CrafticsAnimHolder;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
*///?}
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the visual knock-up offset from {@link EntityBounceState}: the whole
 * model (and name tag) is translated up by a parabolic hop while a bounce is
 * active. Uses an unconditional push at HEAD and pop at RETURN so the matrix
 * stack always balances, whatever vanilla does in between.
 *
 * <p>On 1.21.1 the live entity is available so the offset is read directly by
 * entity id. On 1.21.3+ render receives only a render-state snapshot, so the
 * offset is ferried onto the state by {@code LivingEntityRendererAnimMixin}
 * (same pattern as the anim pose) and read back here.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityBounceMixin {

    //? if <=1.21.1 {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void craftics$bouncePush(LivingEntity entity, float yaw, float tickDelta,
                                     MatrixStack matrices, VertexConsumerProvider vcp, int light,
                                     CallbackInfo ci) {
        matrices.push();
        float off = EntityBounceState.offsetFor(entity.getId());
        if (off != 0f) matrices.translate(0.0, off, 0.0);
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void craftics$bouncePop(LivingEntity entity, float yaw, float tickDelta,
                                    MatrixStack matrices, VertexConsumerProvider vcp, int light,
                                    CallbackInfo ci) {
        matrices.pop();
    }
    //?} else {
    /*@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void craftics$bouncePush(LivingEntityRenderState state, MatrixStack matrices,
                                     VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        matrices.push();
        float off = ((CrafticsAnimHolder) state).craftics$getBounceY();
        if (off != 0f) matrices.translate(0.0, off, 0.0);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void craftics$bouncePop(LivingEntityRenderState state, MatrixStack matrices,
                                    VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        matrices.pop();
    }
    *///?}
}
