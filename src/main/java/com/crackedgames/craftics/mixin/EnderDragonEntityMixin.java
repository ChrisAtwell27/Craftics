package com.crackedgames.craftics.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.Phase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix Ender Dragon rendering in Craftics combat arenas.
 *
 * <p><b>Problem:</b> vanilla {@code tickMovement()} has an early {@code return}
 * when {@code isAiDisabled()} is true. This skips the segment circular-buffer
 * update AND the client-side position interpolation, making the dragon
 * completely invisible and stuck at its spawn position.</p>
 *
 * <p><b>Server:</b> we cancel {@code tickMovement()} entirely at HEAD so the
 * PhaseManager cannot fight the position set by {@code CombatManager}.</p>
 *
 * <p><b>Client:</b> we redirect the {@code isAiDisabled()} call inside
 * {@code tickMovement()} to return {@code false}, allowing the segment buffer
 * and position interpolation to run. We then suppress
 * {@code Phase.clientTick()} to prevent the HOVER phase from overriding the
 * server-authoritative position.</p>
 */
@Mixin(EnderDragonEntity.class)
public abstract class EnderDragonEntityMixin {

    /** Server: cancel tickMovement entirely for arena dragons. */
    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    private void craftics$suppressServerMovement(CallbackInfo ci) {
        EnderDragonEntity self = (EnderDragonEntity) (Object) this;
        if (!self.getWorld().isClient() && self.isAiDisabled()) {
            ci.cancel();
        }
    }

    /**
     * Client: override the {@code isAiDisabled()} check inside
     * {@code tickMovement()} to return {@code false}. This prevents the
     * vanilla early-return that skips the segment buffer update and position
     * interpolation — both of which are required for the dragon to render
     * and to reach the position the server sends via {@code requestTeleport}.
     */
    @Redirect(method = "tickMovement",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;isAiDisabled()Z"))
    private boolean craftics$bypassAiDisabledOnClient(EnderDragonEntity self) {
        if (self.getWorld().isClient()) {
            return false; // let buffer + interpolation run
        }
        return self.isAiDisabled();
    }

    /**
     * Client: suppress {@code Phase.clientTick()} for arena dragons so the
     * HOVER phase cannot override the interpolated server position.
     */
    @Redirect(method = "tickMovement",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/entity/boss/dragon/phase/Phase;clientTick()V"))
    private void craftics$suppressPhaseClientTick(Phase phase) {
        EnderDragonEntity self = (EnderDragonEntity) (Object) this;
        if (!self.isAiDisabled()) {
            phase.clientTick(); // normal behavior for non-arena dragons
        }
    }
}
