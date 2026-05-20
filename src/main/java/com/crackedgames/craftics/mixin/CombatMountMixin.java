package com.crackedgames.craftics.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Neutralize the controlling-passenger relationship for Craftics combat
 * mounts. When the player rides a mount in combat, vanilla treats them as
 * the mount's controlling passenger and lets the client steer the mount via
 * input prediction — that prediction overrides the server's tile-by-tile
 * movement and slingshots the mount back each tick.
 *
 * <p>Mobs tagged with {@code craftics_arena_mount} return {@code null} from
 * {@link LivingEntity#getControllingPassenger()}, so {@code travel()} skips
 * the rider-input branch and the server is the sole authority for the
 * mount's position. The player still renders as a passenger visually.
 */
@Mixin(LivingEntity.class)
public abstract class CombatMountMixin {

    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void craftics$disableSteeringForCombatMount(
            CallbackInfoReturnable<LivingEntity> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getCommandTags().contains("craftics_arena_mount")) {
            cir.setReturnValue(null);
        }
    }
}
