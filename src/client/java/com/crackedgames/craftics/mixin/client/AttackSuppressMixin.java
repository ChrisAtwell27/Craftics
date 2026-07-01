package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppress vanilla attack + item-use during combat AND in a merchant scene.
 * In combat this stops the player punching air / using items when clicking tiles.
 * In a scene it also blocks the vanilla right-click that would reopen a booth
 * villager's merchant screen directly (VillagerEntity.interactMob gates only on
 * non-empty offers), which otherwise loops the "Are you done shopping?" flow.
 */
@Mixin(MinecraftClient.class)
public class AttackSuppressMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void craftics$suppressAttackInCombat(CallbackInfoReturnable<Boolean> cir) {
        if (CombatState.isInCombat() || CombatState.isInScene()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void craftics$suppressItemUseInCombat(CallbackInfo ci) {
        if (CombatState.isInCombat() || CombatState.isInScene()) {
            ci.cancel();
        }
    }
}
