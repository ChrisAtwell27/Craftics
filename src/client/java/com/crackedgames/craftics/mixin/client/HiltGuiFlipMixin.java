package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.combat.CrafticsEnchantments;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 * {@link HiltItemFlipMixin} applies. Purely cosmetic: a 180 degree MatrixStack rotation
 * about the slot center, GUI display mode only.
 *
 * <p><b>Why WrapMethod and not HEAD/RETURN.</b> These item-render funnels are also
 * mixed into by other mods with cancellable injections (Artifacts cancels for its
 * umbrella's custom renderer). A cancel exits through an injected return that a
 * RETURN-targeted pop never sees, leaking our push and corrupting the matrix stack -
 * shifted tooltips in the GUI, then a "Pose stack not empty" crash once a leaked frame
 * reached the world renderer (a dropped umbrella). WrapMethod owns the call in a
 * try/finally, so the pop runs on every exit path: normal, cancelled, or thrown.
 *
 * <p>Target split per shard around the 1.21.4 item render-state refactor; see the
 * per-branch notes. 1.21.4 itself stays a no-op (transitional pipeline outside the
 * supported set). The rotation is BARE - the callers have already centered the matrix
 * on the slot, so any translate here would shove the icon out of its slot.
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

    @WrapMethod(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V")
    private void craftics$guiFlip(ItemStack stack, ModelTransformationMode mode,
            boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay, BakedModel model, Operation<Void> original) {
        matrices.push();
        try {
            if (mode == ModelTransformationMode.GUI && craftics$isHilt(stack)) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
            }
            original.call(stack, mode, leftHanded, matrices, vertexConsumers, light, overlay, model);
        } finally {
            matrices.pop();
        }
    }
}
//?} else if >=1.21.5 {
/*@Mixin(ItemRenderState.class)
public abstract class HiltGuiFlipMixin implements CrafticsHiltHolder {

    @Shadow private ItemDisplayContext displayContext;

    @Unique private boolean craftics$hilt;

    @Override public boolean craftics$isHiltState() { return craftics$hilt; }
    @Override public void craftics$setHiltState(boolean hilt) { this.craftics$hilt = hilt; }

    @WrapMethod(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V")
    private void craftics$guiFlip(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay, Operation<Void> original) {
        matrices.push();
        try {
            if (this.craftics$hilt && this.displayContext == ItemDisplayContext.GUI) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
            }
            original.call(matrices, vertexConsumers, light, overlay);
        } finally {
            matrices.pop();
        }
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

    @WrapMethod(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V")
    private void craftics$guiFlip(ItemStack stack, ModelTransformationMode mode,
            boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay, BakedModel model, Operation<Void> original) {
        matrices.push();
        try {
            if (mode == ModelTransformationMode.GUI && craftics$isHilt(stack)) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
            }
            original.call(stack, mode, leftHanded, matrices, vertexConsumers, light, overlay, model);
        } finally {
            matrices.pop();
        }
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
