package com.crackedgames.craftics.mixin.client;

//? if >=1.21.5 {
/*import com.crackedgames.craftics.client.CrafticsHiltHolder;
import com.crackedgames.craftics.combat.CrafticsEnchantments;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Records the "is this stack Hilt-enchanted" bit onto the built
 * {@code ItemRenderState} so {@link HiltGuiFlipMixin} can flip the GUI icon on 1.21.5+.
 *
 * <p>The 1.21.4 render-state refactor means the draw step no longer sees the
 * {@code ItemStack}; the stack is only visible when the state is built by
 * {@code ItemModelManager.clearAndUpdate}. This mixin runs at the tail of that build,
 * reads the Hilt enchantment off the stack, and stashes the flag on the render state
 * through {@code CrafticsHiltHolder}. Detection is client-safe (string-matches the
 * enchantment registry id off the stack's data component).
 *
 * <p>On 1.21.1 this class targets {@code LivingEntity} (present on every shard) with no
 * injected members, so it stays loadable without doing anything - the 1.21.1 GUI flip
 * detects Hilt inline in {@code HiltGuiFlipMixin}.
 */
//? if >=1.21.5 {
/*@Mixin(ItemModelManager.class)
public abstract class HiltRenderStateMixin {

    @Inject(method = "clearAndUpdate(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
            at = @At("TAIL"))
    private void craftics$markHilt(ItemRenderState state, ItemStack stack,
            ItemDisplayContext displayContext, World world, LivingEntity entity, int seed,
            CallbackInfo ci) {
        boolean hilt = stack != null
            && !stack.isEmpty()
            && PlayerCombatStats.getEnchantLevel(stack, CrafticsEnchantments.HILT.fullId()) > 0;
        ((CrafticsHiltHolder) (Object) state).craftics$setHiltState(hilt);
    }
}
*///?} else {
@org.spongepowered.asm.mixin.Mixin(net.minecraft.entity.LivingEntity.class)
public abstract class HiltRenderStateMixin {
    // No-op on 1.21.1: ItemRenderer.renderItem still receives the ItemStack directly,
    // so HiltGuiFlipMixin detects Hilt inline. Targeting LivingEntity (which exists on
    // every shard) keeps this class loadable without declaring any injected members.
}
//?}
