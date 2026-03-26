package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppress vanilla attack swing animation during combat.
 * Prevents the player from punching the air when clicking tiles.
 */
@Mixin(MinecraftClient.class)
public class AttackSuppressMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void craftics$suppressAttackInCombat(CallbackInfoReturnable<Boolean> cir) {
        if (CombatState.isInCombat()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void craftics$suppressItemUseInCombat(CallbackInfo ci) {
        if (CombatState.isInCombat()) {
            ci.cancel();
        }
    }
}
