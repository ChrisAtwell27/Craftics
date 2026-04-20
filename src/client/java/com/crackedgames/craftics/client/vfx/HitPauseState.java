package com.crackedgames.craftics.client.vfx;

/** Client-side freeze counter. When > 0, per-tick animation/effect advancement is suppressed. */
public final class HitPauseState {
    private static int freezeTicks = 0;

    public static void freeze(int ticks) {
        freezeTicks = Math.max(freezeTicks, ticks);
    }

    public static boolean isFrozen() { return freezeTicks > 0; }

    public static void tick() {
        if (freezeTicks > 0) freezeTicks--;
    }

    public static void reset() { freezeTicks = 0; }
}
