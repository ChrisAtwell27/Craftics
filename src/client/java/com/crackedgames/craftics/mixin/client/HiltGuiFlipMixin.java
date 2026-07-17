package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.combat.CrafticsEnchantments;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <=1.21.1 {
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
//?} else if >=1.21.5 {
/*import com.crackedgames.craftics.client.CrafticsHiltHolder;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Shadow;
*///?} else if <=1.21.3 {
/*import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.item.ModelTransformationMode;
*///?}

/**
 * Flips the Hilt inventory/hotbar icon upside down, matching the in-hand flip that
 * {@link HiltItemFlipMixin} applies. Purely cosmetic: it is a 180 degree MatrixStack
 * rotation and never touches the hitbox, the slot the item occupies, or interaction.
 *
 * <p>The GUI icon renders through a different path than the held item, and that path
 * diverges hard across the 1.21.4 item render-state refactor, so the target is split
 * per shard:
 *
 * <ul>
 *   <li><b>1.21.1 / 1.21.3</b>: {@code ItemRenderer.renderItem(ItemStack,
 *       ModelTransformationMode, boolean, MatrixStack, VertexConsumerProvider, int, int,
 *       BakedModel)}. This method is the funnel for every item render, so the flip is
 *       gated to {@code ModelTransformationMode.GUI} only, leaving the held item (already
 *       handled) and item frames / dropped items untouched. The stack is available here,
 *       so Hilt detection is inline. {@code ModelTransformationMode} lives in
 *       {@code net.minecraft.client.render.model.json} on 1.21.1 and in
 *       {@code net.minecraft.item} on 1.21.3.</li>
 *   <li><b>1.21.5+</b>: {@code ItemRenderState.render(MatrixStack, VertexConsumerProvider,
 *       int, int)}, gated to {@code ItemDisplayContext.GUI}. The render state does not
 *       carry the {@code ItemStack}, so the Hilt bit is ferried onto it at build time by
 *       {@link HiltRenderStateMixin} through {@code CrafticsHiltHolder}.</li>
 *   <li><b>1.21.4</b>: no-op. This transitional shard uses the render-state pipeline but
 *       with different field names and update-method signatures than 1.21.5, and it is
 *       outside the supported target set, so the GUI icon is left unflipped there. The
 *       class stays loadable by targeting {@code net.minecraft.entity.LivingEntity} with
 *       no injected members.</li>
 * </ul>
 *
 * <p><b>Matrix frame.</b> On the {@code ItemRenderer} shards the GUI caller
 * ({@code DrawContext.drawItem}) sets up the matrix before this method runs by
 * translating to the slot center ({@code x+8, y+8}) and scaling by {@code (16, -16, 16)}.
 * So at the injection HEAD the origin already sits at the slot center. A BARE 180 degree
 * Z rotation therefore pivots the icon about the slot center and flips it in place; a
 * {@code translate(0.5, ...)} center offset would be wrong here (that offset is only valid
 * deeper, in the item's own 0-1 model space) and would shove the icon out of its slot. The
 * same slot-center frame holds for the 1.21.5 {@code ItemRenderState.render} injection. The
 * stack is pushed unconditionally at HEAD and popped at RETURN so it always balances vanilla.
 */
//? if <=1.21.1 {
@Mixin(ItemRenderer.class)
public abstract class HiltGuiFlipMixin {

    @Unique
    private static boolean craftics$isHilt(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && PlayerCombatStats.getEnchantLevel(stack, CrafticsEnchantments.HILT.fullId()) > 0;
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"))
    private void craftics$guiFlipPush(ItemStack stack, ModelTransformationMode mode,
            boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay, BakedModel model, CallbackInfo ci) {
        matrices.push();
        if (mode == ModelTransformationMode.GUI && craftics$isHilt(stack)) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("RETURN"))
    private void craftics$guiFlipPop(ItemStack stack, ModelTransformationMode mode,
            boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay, BakedModel model, CallbackInfo ci) {
        matrices.pop();
    }
}
//?} else if >=1.21.5 {
/*@Mixin(ItemRenderState.class)
public abstract class HiltGuiFlipMixin implements CrafticsHiltHolder {

    @Shadow private ItemDisplayContext displayContext;

    @Unique private boolean craftics$hilt;

    @Override public boolean craftics$isHiltState() { return craftics$hilt; }
    @Override public void craftics$setHiltState(boolean hilt) { this.craftics$hilt = hilt; }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at = @At("HEAD"))
    private void craftics$guiFlipPush(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay, CallbackInfo ci) {
        matrices.push();
        if (this.craftics$hilt && this.displayContext == ItemDisplayContext.GUI) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at = @At("RETURN"))
    private void craftics$guiFlipPop(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay, CallbackInfo ci) {
        matrices.pop();
    }
}
*///?} else if <=1.21.3 {
/*@Mixin(ItemRenderer.class)
public abstract class HiltGuiFlipMixin {

    @Unique
    private static boolean craftics$isHilt(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && PlayerCombatStats.getEnchantLevel(stack, CrafticsEnchantments.HILT.fullId()) > 0;
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"))
    private void craftics$guiFlipPush(ItemStack stack, ModelTransformationMode mode,
            boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay, BakedModel model, CallbackInfo ci) {
        matrices.push();
        if (mode == ModelTransformationMode.GUI && craftics$isHilt(stack)) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("RETURN"))
    private void craftics$guiFlipPop(ItemStack stack, ModelTransformationMode mode,
            boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay, BakedModel model, CallbackInfo ci) {
        matrices.pop();
    }
}
*///?} else {
/*@Mixin(net.minecraft.entity.LivingEntity.class)
public abstract class HiltGuiFlipMixin {
    // No-op on 1.21.4: its render-state pipeline differs from both neighbours and this
    // shard is outside the supported target set. Targeting LivingEntity (present on every
    // shard) keeps the class loadable with no injected members and no GUI flip.
}
*///?}
