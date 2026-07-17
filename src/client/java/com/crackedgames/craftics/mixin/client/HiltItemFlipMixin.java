package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.combat.CrafticsEnchantments;
import com.crackedgames.craftics.combat.PlayerCombatStats;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders a Hilt-enchanted held weapon upside down. Purely cosmetic: the flip is a
 * 180 degree MatrixStack rotation applied inside the held-item render method only, so
 * it never touches the hitbox, damage, or interaction. The GUI/inventory icon is
 * flipped separately by {@code HiltGuiFlipMixin}.
 *
 * <p>Detection reads the {@code craftics:hilt} enchantment straight off the rendered
 * {@link ItemStack}'s enchantment data component via
 * {@link PlayerCombatStats#getEnchantLevel(ItemStack, String)}, which string-matches
 * the registry id and is client-safe (no ServerPlayerEntity). {@code heldLevel} would
 * need a server player, so it is deliberately avoided here.
 *
 * <p>The push at HEAD and pop at RETURN are unconditional so the matrix stack always
 * balances whatever vanilla does in between; the rotation is only added when Hilt is
 * present. {@code HeldItemRenderer.renderItem} covers the item held in hand in both
 * first and third person. Its signature diverges after the 1.21.4 render-state
 * refactor (the {@code ModelTransformationMode}+boolean pair became a single
 * {@code ItemDisplayContext}), so the injection target is split per shard.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HiltItemFlipMixin {

    @org.spongepowered.asm.mixin.Unique
    private static boolean craftics$isHilt(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && PlayerCombatStats.getEnchantLevel(stack, CrafticsEnchantments.HILT.fullId()) > 0;
    }

    @org.spongepowered.asm.mixin.Unique
    private static void craftics$flip(MatrixStack matrices, boolean hilt) {
        if (!hilt) return;
        // At HeldItemRenderer.renderItem HEAD the matrix is in the HAND/arm frame set
        // up by the caller (renderFirstPersonItem / renderArmHoldingItem): the origin
        // sits at the hand grip and the item model has NOT yet been scaled into its
        // 0-1 model box. That method delegates straight to ItemRenderer.renderItem with
        // no transform of its own, so HEAD is exactly the hand frame.
        //
        // A center-offset translate of 0.5 here would move the item half a WORLD block
        // (the "flung to the right" bug), because 0.5 is only the model center once the
        // matrix is in item 0-1 space, which happens deeper inside ItemRenderer. So we
        // use a BARE rotation with no translate: it pivots the item 180 degrees about
        // the hand grip, which keeps the sword in the hand and reads as "upside down".
        // The Z axis is the roll axis of the flat held sprite, so a Z rotation flips it.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
    }

    //? if <=1.21.1 {
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void craftics$hiltFlipPush(LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, boolean leftHand, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.push();
        craftics$flip(matrices, craftics$isHilt(stack));
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void craftics$hiltFlipPop(LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, boolean leftHand, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.pop();
    }
    //?} else if >=1.21.5 {
    /*@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void craftics$hiltFlipPush(LivingEntity entity, ItemStack stack,
            ItemDisplayContext mode, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.push();
        craftics$flip(matrices, craftics$isHilt(stack));
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void craftics$hiltFlipPop(LivingEntity entity, ItemStack stack,
            ItemDisplayContext mode, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.pop();
    }
    *///?} else {
    /*// 1.21.3 / 1.21.4: HeldItemRenderer.renderItem still takes ModelTransformationMode
    // plus the leftHand boolean, but ModelTransformationMode moved to net.minecraft.item.
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void craftics$hiltFlipPush(LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, boolean leftHand, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.push();
        craftics$flip(matrices, craftics$isHilt(stack));
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void craftics$hiltFlipPop(LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, boolean leftHand, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.pop();
    }
    *///?}
}
