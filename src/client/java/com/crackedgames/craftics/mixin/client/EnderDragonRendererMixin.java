package com.crackedgames.craftics.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skip rendering the Ender Dragon when it is in the Craftics ATTACKING state
 * (parked 100 blocks above the arena). The vanilla renderer doesn't check
 * {@code isInvisible()}, and the invisible data-tracker flag doesn't sync
 * reliably for the dragon entity. Instead we check whether the dragon is
 * far above the player — during ATTACKING it sits at Y+100 (hidden), during
 * PERCHING it drops to Y+5 (visible).
 */
@Mixin(EnderDragonEntityRenderer.class)
public abstract class EnderDragonRendererMixin {

    @Inject(method = "render(Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
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
}
