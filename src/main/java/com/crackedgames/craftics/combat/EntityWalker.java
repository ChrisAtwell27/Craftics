package com.crackedgames.craftics.combat;

import java.util.Objects;

/**
 * Lerps a single entity from a start position to a target over {@code totalTicks},
 * reproducing the combat walk feel (smooth position + facing). Tick once per server
 * tick. The {@link Mover} abstraction keeps the lerp math unit-testable without a
 * live entity; the live wiring (velocity for limb animation + {@code requestTeleport})
 * lives in the production Mover created by {@code EventCinematic}.
 */
public final class EntityWalker {

    /** Applies a lerped pose to the underlying entity each tick. */
    public interface Mover {
        void apply(double x, double y, double z, float yaw);
    }

    private final Mover mover;
    private final double startX, startY, startZ;
    private final double endX, endY, endZ;
    private final int totalTicks;
    private final float yaw;
    private final Runnable onComplete;

    private int tickCounter = 0;
    private boolean complete = false;

    public EntityWalker(Mover mover,
                        double startX, double startY, double startZ,
                        double endX, double endY, double endZ,
                        int totalTicks, Runnable onComplete) {
        this.mover = Objects.requireNonNull(mover, "mover");
        this.startX = startX; this.startY = startY; this.startZ = startZ;
        this.endX = endX; this.endY = endY; this.endZ = endZ;
        this.totalTicks = Math.max(1, totalTicks);
        this.onComplete = onComplete;
        double dx = endX - startX, dz = endZ - startZ;
        this.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public boolean isComplete() { return complete; }

    public void tick() {
        if (complete) return;
        tickCounter++;
        float progress = Math.min(1.0f, (float) tickCounter / totalTicks);
        double x = startX + (endX - startX) * progress;
        double y = startY + (endY - startY) * progress;
        double z = startZ + (endZ - startZ) * progress;
        mover.apply(x, y, z, yaw);
        if (tickCounter >= totalTicks) {
            // Set complete before invoking the callback so a re-entrant tick()
            // from within onComplete early-returns instead of firing again.
            complete = true;
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
}
