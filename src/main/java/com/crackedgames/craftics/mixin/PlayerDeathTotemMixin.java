package com.crackedgames.craftics.mixin;

import com.crackedgames.craftics.combat.CombatManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Totem safety net for damage Craftics never metered.
 *
 * <p>All turn-based damage goes through {@code CombatManager.damagePlayer}, which clamps HP to 1
 * so it can never kill, and every death route it owns asks for a totem first. But VANILLA damage
 * reaches the player untouched - a boss ability that spawns a real explosion, fire, fall damage -
 * and could take someone from full HP to dead without the totem ever being consulted. That is the
 * "boss one-shot through a totem" players were reporting.
 *
 * <p>This intercepts {@code LivingEntity.damage} for a player in active Craftics combat, and when
 * the blow would be lethal, offers the totem. If one is spent the player is resurrected and the
 * damage is CANCELLED outright (returning false = "not damaged"), because
 * {@code tryConsumeTotemAndResurrect} has already set their health; letting the original blow land
 * afterwards would kill them anyway and waste the totem.
 *
 * <p>Deliberately last-resort: it only fires on damage that would actually be fatal, so it never
 * competes with the combat manager's own totem handling on the paths that do route through it.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerDeathTotemMixin {

    //? if <=1.21.1 {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void craftics$totemAgainstLethalVanillaDamage(
            DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        craftics$tryTotem(amount, cir);
    }
    //?} else {
    /*@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void craftics$totemAgainstLethalVanillaDamage(
            net.minecraft.server.world.ServerWorld world, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        craftics$tryTotem(amount, cir);
    }
    *///?}

    private void craftics$tryTotem(float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity player)) return;

        CombatManager cm = CombatManager.getActiveCombat(player.getUuid());
        if (cm == null || !cm.isActive()) return;

        // The arena is still being built: the player is falling through a floor that does not
        // exist yet, so ANY damage here is an artifact of the load, not the fight. Swallow it
        // whole - killing them, or spending a totem to "save" them, would both be punishing
        // someone purely for having a slow machine.
        if (cm.isArenaSettling()) {
            cir.setReturnValue(false);
            return;
        }

        // Only lethal blows. Non-fatal vanilla damage is left entirely alone.
        float absorbed = Math.max(0f, amount - player.getAbsorptionAmount());
        if (player.getHealth() - absorbed > 0f) return;

        if (cm.tryTotemAgainstLethalDamage(player)) {
            // Resurrected: swallow the blow. The totem already set their health, and letting
            // the original damage through would drop them straight back to dead.
            cir.setReturnValue(false);
        }
    }
}
