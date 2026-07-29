package com.crackedgames.craftics.client;

/**
 * Framing that reacts to whose turn it is: the camera leans in and a soft vignette closes around
 * the screen while the enemies act, then pulls back and clears when control returns to the player.
 *
 * <p>It's a read of authorship - your turn is open and bright, their turn is pressed in and
 * watched. Both values ease on wall-clock time so they're framerate-independent, and both are
 * pure client-side reads of {@link CombatState#getPhase()}; nothing is synced for this.
 *
 * <p>The zoom is applied by {@code CameraLockMixin} (it owns the tactical camera rig) and the
 * vignette by {@code CombatVisualEffects.render}, which already draws the status-effect vignettes
 * through the same helper.
 */
public final class TurnFramingFx {

    private TurnFramingFx() {}

    /** Camera distance multiplier while the enemy acts - below 1 leans in. */
    private static final float ENEMY_TURN_ZOOM = 0.92f;
    /** Peak vignette alpha (0-255) during the enemy turn. */
    private static final int ENEMY_TURN_VIGNETTE = 74;
    /** Seconds to cover ~63% of the distance to the target framing. */
    private static final float EASE_TAU = 0.45f;

    /** Current eased 0..1 blend toward the enemy-turn framing. */
    private static float pressure = 0.0f;
    private static long lastStepNanos = 0L;

    /** Advance the ease. Called once per client tick. */
    public static void tick() {
        float target = CombatState.isInCombat() && CombatState.isEnemyTurn() ? 1.0f : 0.0f;
        long now = System.nanoTime();
        if (lastStepNanos == 0L) lastStepNanos = now;
        float dt = Math.min(0.25f, (now - lastStepNanos) / 1_000_000_000.0f);
        lastStepNanos = now;
        pressure += (target - pressure) * (1.0f - (float) Math.exp(-dt / EASE_TAU));
        if (pressure < 0.001f) pressure = 0.0f;
    }

    /** Multiplier on the tactical camera's distance from its focus point. */
    public static float zoomScale() {
        return 1.0f + (ENEMY_TURN_ZOOM - 1.0f) * pressure;
    }

    /** Vignette alpha (0-255) for the enemy turn, or 0 when it's the player's. */
    public static int vignetteAlpha() {
        return Math.round(ENEMY_TURN_VIGNETTE * pressure);
    }

    /** How far into the enemy-turn framing we currently are, 0..1. */
    public static float pressure() { return pressure; }

    /** Snap back to neutral framing (combat end, disconnect). */
    public static void reset() {
        pressure = 0.0f;
        lastStepNanos = 0L;
    }
}
