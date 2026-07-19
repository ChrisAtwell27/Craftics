package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.combat.CrafticsEnchantments;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
//? if <=1.21.1 {
import net.minecraft.client.render.model.json.ModelTransformationMode;
//?} else if >=1.21.5 {
/*import net.minecraft.item.ItemDisplayContext;
*///?} else {
/*import net.minecraft.item.ModelTransformationMode;
*///?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * Renders a Hilt-enchanted held weapon upside down. Purely cosmetic: the flip is a
 * 180 degree MatrixStack rotation applied inside the held-item render method only, so
 * it never touches the hitbox, damage, or interaction. The GUI/inventory icon is
 * flipped separately by {@code HiltGuiFlipMixin}.
 *
 * <p>Detection reads the {@code craftics:hilt} enchantment straight off the rendered
 * {@link ItemStack}'s enchantment data component via
 * {@link PlayerCombatStats#getEnchantLevel(ItemStack, String)}, which string-matches
 * the registry id and is client-safe (no ServerPlayerEntity).
 *
 * <p><b>Why WrapMethod and not HEAD/RETURN.</b> A HEAD-push / RETURN-pop pair balances
 * only when the method exits through one of ITS OWN return opcodes. Another mod's
 * cancellable HEAD injection (Artifacts cancels item rendering for its umbrella to run
 * a custom renderer) exits through a freshly injected return that RETURN handlers never
 * see - the push leaked, corrupting the matrix stack once per rendered umbrella until
 * the world renderer's "Pose stack not empty" check crashed the game. WrapMethod owns
 * the whole call in a try/finally, so the pop runs no matter how the method exits:
 * normal return, another mixin's cancel, or an exception.
 *
 * <p>The rotation is a BARE Z spin about the hand grip - no translate. At this method's
 * entry the matrix is in the hand/arm frame, where a 0.5 center offset would move the
 * item half a world block (the old "flung to the right" bug). The signature diverges
 * after the 1.21.4 render-state refactor, so the wrap target is split per shard.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HiltItemFlipMixin {

    @org.spongepowered.asm.mixin.Unique
    private static boolean craftics$isHilt(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && PlayerCombatStats.getEnchantLevel(stack, CrafticsEnchantments.HILT.fullId()) > 0;
    }

    //? if <=1.21.1 {
    @WrapMethod(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
    private void craftics$hiltFlip(LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, boolean leftHand, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, Operation<Void> original) {
        matrices.push();
        try {
            if (craftics$isHilt(stack)) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
            }
            original.call(entity, stack, mode, leftHand, matrices, vertexConsumers, light);
        } finally {
            matrices.pop();
        }
    }
    //?} else if >=1.21.5 {
    /*@WrapMethod(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
    private void craftics$hiltFlip(LivingEntity entity, ItemStack stack,
            ItemDisplayContext mode, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, Operation<Void> original) {
        matrices.push();
        try {
            if (craftics$isHilt(stack)) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
            }
            original.call(entity, stack, mode, matrices, vertexConsumers, light);
        } finally {
            matrices.pop();
        }
    }
    *///?} else {
    /*// 1.21.3 / 1.21.4: same shape, ModelTransformationMode moved to net.minecraft.item.
    @WrapMethod(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
    private void craftics$hiltFlip(LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, boolean leftHand, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, Operation<Void> original) {
        matrices.push();
        try {
            if (craftics$isHilt(stack)) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
            }
            original.call(entity, stack, mode, leftHand, matrices, vertexConsumers, light);
        } finally {
            matrices.pop();
        }
    }
    *///?}
}
