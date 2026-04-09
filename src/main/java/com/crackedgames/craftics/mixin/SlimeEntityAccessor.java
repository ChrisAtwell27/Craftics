package com.crackedgames.craftics.mixin;

import net.minecraft.entity.mob.SlimeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin accessor that exposes SlimeEntity#setSize so we can force visual sizes
 * on slimes/magma cubes (vanilla normally chooses random sizes at spawn).
 * Used by the arena split mechanic to make 2x2 parents look big and 1x1 minis
 * look small, instead of inheriting whatever vanilla rolled.
 */
@Mixin(SlimeEntity.class)
public interface SlimeEntityAccessor {
    @Invoker("setSize")
    void craftics$setSize(int size, boolean heal);
}
