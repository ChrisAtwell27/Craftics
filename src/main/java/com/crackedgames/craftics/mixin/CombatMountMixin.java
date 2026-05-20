package com.crackedgames.craftics.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freeze Craftics combat mounts so vanilla's per-tick movement physics
 * don't fight {@link com.crackedgames.craftics.combat.CombatManager}'s
 * tile-by-tile control. With a player passenger, vanilla tickMovement
 * applies rider-input prediction, velocity decay, collision, and friction
 * each tick — those nudges add up on the client and slingshot the mount.
 *
 * <p>Cancelling {@code tickMovement} at HEAD for entities tagged
 * {@code craftics_arena_mount} freezes the mount's position entirely.
 * CombatManager is the sole authority; the riding link stays intact so
 * the player still renders on top of the mount.
 */
@Mixin(LivingEntity.class)
public abstract class CombatMountMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    private void craftics$skipMovementForCombatMount(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getCommandTags().contains("craftics_arena_mount")) {
            ci.cancel();
        }
    }
}
