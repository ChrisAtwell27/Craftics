package com.crackedgames.craftics.client.anim;

import com.crackedgames.craftics.combat.animation.AnimState;

/**
 * Duck interface implemented by the 1.21.3+ {@code LivingEntityRenderState}
 * mixin. Used to ferry a mob's current {@link AnimState} and progress (ticks
 * since the state was set) onto the render-state snapshot, which is what the
 * model's {@code setAngles} gets on 1.21.3+.
 *
 * <p>Unused on 1.21.1 — that shard reads the CCA component directly from the
 * entity inside the {@code BipedEntityModel#setAngles} mixin.
 */
public interface CrafticsAnimHolder {
    AnimState craftics$getAnimState();
    void craftics$setAnimState(AnimState s);

    /** Ticks elapsed since the state was set (client world time at update). */
    float craftics$getAnimTicks();
    void craftics$setAnimTicks(float t);
}
