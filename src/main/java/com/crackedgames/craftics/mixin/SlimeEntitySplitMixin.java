package com.crackedgames.craftics.mixin;

import net.minecraft.entity.mob.SlimeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppress vanilla slime/magma-cube splitting for arena mobs.
 *
 * Craftics manages slime/magma-cube splitting explicitly via CombatManager
 * (trySplitOnDeath for regular mobs, notifyBossOfDamage for the Molten King).
 * The dying-mob shrink animation calls {@code mob.setHealth(0)}, which causes
 * vanilla {@link SlimeEntity#remove} to spawn mini-slime children alongside the
 * Craftics copies — producing frozen ghost cubes that do nothing. This redirect
 * returns size 1 for arena-tagged slimes only during {@code remove}, so the
 * vanilla {@code size > 1} split check fails while leaving normal overworld
 * slimes unaffected.
 */
@Mixin(SlimeEntity.class)
public abstract class SlimeEntitySplitMixin {
    @Redirect(
        method = "remove(Lnet/minecraft/entity/Entity$RemovalReason;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/mob/SlimeEntity;getSize()I")
    )
    private int craftics$suppressArenaSplit(SlimeEntity self) {
        if (self.getCommandTags().contains("craftics_arena")) {
            return 1; // tricks vanilla remove() into skipping its split branch
        }
        return self.getSize();
    }
}
