package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import com.crackedgames.craftics.client.CombatVisualEffects;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraLockMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    // Guard vanilla Camera.update against null focusedEntity during world load
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void craftics$guardNullEntity(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (focusedEntity == null) {
            ci.cancel();
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void craftics$lockCameraInCombat(CallbackInfo ci) {
        if (CombatState.isInCombat()) {
            setRotation(CombatState.getCombatYaw(), CombatState.getCombatPitch());

            // Use smooth focus zoom/position instead of static arena center
            float distance = CombatState.getCameraFocusZoom();
            double pitchRad = Math.toRadians(CombatState.getCombatPitch());
            double yawRad = Math.toRadians(CombatState.getCombatYaw());

            double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double forwardY = -Math.sin(pitchRad);
            double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

            // Use focus position (smoothly lerps between arena center and focused entity)
            double focusX = CombatState.getCameraFocusX();
            double focusZ = CombatState.getCameraFocusZ();

            // Apply screen shake offset
            double shakeX = CombatVisualEffects.getShakeOffsetX();
            double shakeZ = CombatVisualEffects.getShakeOffsetZ();

            setPos(
                focusX - forwardX * distance + shakeX,
                CombatState.getArenaCenterY() - forwardY * distance,
                focusZ - forwardZ * distance + shakeZ
            );
        }
    }
}
