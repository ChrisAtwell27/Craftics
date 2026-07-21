package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
//? if >=1.21.3 {
/*import com.crackedgames.craftics.client.anim.CrafticsAnimHolder;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
*///?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-player Darkness fog-of-war: cancels rendering an arena enemy on the LOCAL
 * client when the local player is under the Darkness effect and that enemy is
 * beyond the reveal radius (see {@link CombatState#isEnemyHiddenByDarkness}).
 *
 * <p>Purely client-side and per-player - it reads only the local client's own
 * combat state, so teammates see everything normally and the server is never
 * involved. This mirrors the client render-cancel pattern in
 * {@code EnderDragonRendererMixin}.
 *
 * <p>Shard split: on 1.21.1 the render method receives the live entity, so we
 * query {@link CombatState} directly by entity id. On 1.21.3+ rendering was
 * refactored to a {@code LivingEntityRenderState} snapshot with no entity ref,
 * so we read the {@code craftics$isDarkHidden} flag ferried onto the snapshot by
 * {@code LivingEntityRendererAnimMixin} during {@code updateRenderState}.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityDarknessHideMixin {

    //? if <=1.21.1 {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void craftics$hideInDarkness(LivingEntity entity, float yaw, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            CallbackInfo ci) {
        if (CombatState.isEnemyHiddenByDarkness(entity.getId())) {
            ci.cancel();
        }
    }
    //?} else {
    /*@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void craftics$hideInDarkness(LivingEntityRenderState state, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (((CrafticsAnimHolder) state).craftics$isDarkHidden()) {
            ci.cancel();
        }
    }
    *///?}
}
