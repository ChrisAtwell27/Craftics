package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.vfx.EntityBounceState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//? if >=1.21.3 {
/*import com.crackedgames.craftics.client.anim.CrafticsAnimHolder;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
*///?}
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Applies the visual knock-up offset from {@link EntityBounceState}: the whole
 * model (and name tag) is translated up by a parabolic hop while a bounce is
 * active. Wraps the render call so the matrix stack is restored even when a
 * cancellable render mixin exits before the method's RETURN injection runs.
 *
 * <p>On 1.21.1 the live entity is available so the offset is read directly by
 * entity id. On 1.21.3+ render receives only a render-state snapshot, so the
 * offset is ferried onto the state by {@code LivingEntityRendererAnimMixin}
 * (same pattern as the anim pose) and read back here.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityBounceMixin {

    //? if <=1.21.1 {
        @WrapMethod(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
        private void craftics$renderWithBounce(LivingEntity entity, float yaw, float tickDelta,
                                                                                   MatrixStack matrices, VertexConsumerProvider vcp, int light,
                                                                                   Operation<Void> original) {
        matrices.push();
                try {
                        float off = EntityBounceState.offsetFor(entity.getId());
                        if (off != 0f) matrices.translate(0.0, off, 0.0);
                        original.call(entity, yaw, tickDelta, matrices, vcp, light);
                } finally {
                        matrices.pop();
                }
    }
    //?} else {
        /*@WrapMethod(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
        private void craftics$renderWithBounce(LivingEntityRenderState state, MatrixStack matrices,
                                                                                   VertexConsumerProvider vcp, int light,
                                                                                   Operation<Void> original) {
        matrices.push();
                try {
                        float off = ((CrafticsAnimHolder) state).craftics$getBounceY();
                        if (off != 0f) matrices.translate(0.0, off, 0.0);
                        original.call(state, matrices, vcp, light);
                } finally {
                        matrices.pop();
                }
    }
    *///?}
}
