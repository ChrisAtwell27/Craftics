package com.crackedgames.craftics.mixin;

import com.crackedgames.craftics.combat.CombatManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla Regeneration must never reach a player in Craftics combat.
 *
 * <p>Regeneration is the one status effect that is fundamentally incompatible with turn-based
 * combat: it heals on a real-time tick, so a player standing still between turns silently
 * regains HP and the whole damage economy stops meaning anything. Craftics has its own
 * REGENERATION combat effect that heals a small amount at the start of each of the player's
 * turns - that is the only regeneration the game should ever grant.
 *
 * <p>Blocking it HERE rather than at each source is deliberate. Regen can arrive from a potion,
 * a splash, a tipped arrow, a totem, an enchanted golden apple, an addon weapon, or a mod we
 * have never heard of. Patching call sites is a game of whack-a-mole that a future weapon would
 * quietly lose; one gate at the entity catches every source there is or ever will be.
 *
 * <p>Only players, and only during active combat: outside a fight (on the island, in the hub)
 * regeneration behaves normally, and mobs are never touched.
 */
@Mixin(LivingEntity.class)
public abstract class NoVanillaRegenMixin {

    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z",
            at = @At("HEAD"), cancellable = true)
    private void craftics$blockVanillaRegenInCombat(
            StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (effect == null || !effect.getEffectType().equals(StatusEffects.REGENERATION)) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity player)) return;

        CombatManager cm = CombatManager.getActiveCombat(player.getUuid());
        if (cm == null || !cm.isActive()) return;

        // Report "applied" so callers that branch on the result don't take an error path -
        // they simply never get the real effect. The Craftics combat regen (granted alongside
        // by every legitimate source) is what actually heals them, on their turn.
        cir.setReturnValue(true);
    }
}
