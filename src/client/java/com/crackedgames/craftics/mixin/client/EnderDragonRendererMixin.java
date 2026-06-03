package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
//? if >=1.21.2 {
import net.minecraft.client.render.entity.state.EnderDragonEntityRenderState;
//?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skip rendering the Ender Dragon when it is parked far above the player during
 * Craftics combat (ATTACKING phase — dragon sits at Y+100 hidden; PERCHING phase
 * drops it back to Y+5).
 *
 * <p>On 1.21.1 the render method receives the live entity, so we can also read
 * its {@code isAiDisabled} flag as a secondary gate. On 1.21.2+ rendering was
 * refactored to pass a {@code EnderDragonEntityRenderState} snapshot instead,
 * which has no AI-disabled field — so on the newer shards we rely solely on the
 * Y-distance check plus the Craftics combat state gate.
 */
@Mixin(EnderDragonEntityRenderer.class)
public abstract class EnderDragonRendererMixin {

    //? if <=1.21.1 {
    /*@Inject(method = "render(Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void craftics$hideDragonWhenOffStage(EnderDragonEntity entity, float yaw,
            float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, CallbackInfo ci) {
        if (entity.isAiDisabled()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && entity.getY() - mc.player.getY() > 30) {
                ci.cancel();
            }
        }
    }
    *///?} else {
    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void craftics$hideDragonWhenOffStage(EnderDragonEntityRenderState state,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, CallbackInfo ci) {
        if (!CombatState.isInCombat()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && state.y - mc.player.getY() > 30) {
            ci.cancel();
        }
    }
    //?}
}
