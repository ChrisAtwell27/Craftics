package com.crackedgames.craftics.combat.animation;

/**
 * Pose state for a craftics_arena mob. Read by the client-side
 * {@code BipedAnimMixin} to override bone angles on top of vanilla idle/walk.
 *
 * <p>Each state has a natural max duration — {@link #maxTicks()} — after which
 * the server-side tick ({@code MobAnimations.tick}) resets the mob to IDLE to
 * prevent a stuck pose if a trigger forgets to clear.
 */
public enum AnimState {
    IDLE(0),
    WINDUP(20),
    ATTACK(6),
    RECOIL(10),
    HIT(4),
    CAST(30),
    ROAR(20),
    STUNNED(40);

    private final int maxTicks;

    AnimState(int maxTicks) {
        this.maxTicks = maxTicks;
    }

    public int maxTicks() {
        return maxTicks;
    }
}
