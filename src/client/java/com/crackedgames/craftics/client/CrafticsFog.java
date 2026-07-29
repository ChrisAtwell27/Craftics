package com.crackedgames.craftics.client;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Distance fog that closes in around a Craftics build so its edges don't open onto raw void.
 *
 * <p>Arenas, event scenes and trade halls are schematics pasted at fixed coordinates inside an
 * island's void dimension ({@code craftics:island/<uuid>}) - past the last floor block there is
 * nothing, and the empty sky/void plane behind a build reads as an unfinished level. Pulling the
 * fog band in to just beyond the footprint hides that: the build itself stays perfectly clear and
 * everything past its edge dissolves into the biome's own fog colour.
 *
 * <p>Only active while a combat arena, scene, or event cinematic is up - the island hub keeps
 * vanilla view distance. The band is derived from the live footprint, so it tightens on a small
 * arena and opens up on a wide trade hall rather than being one fixed number.
 *
 * <p>Purely client-side and per-player: nothing is synced for it. The colour is left to vanilla
 * (biome fog tinted by time of day), which the arena biome stamp already themes per level, so the
 * fog matches the sky instead of fighting it.
 *
 * <p><b>Only ever tightens.</b> When vanilla's own fog is already closer than ours - underwater,
 * in lava, under Blindness or Darkness - the override backs off entirely and those effects read
 * exactly as they should.
 */
public final class CrafticsFog {

    private CrafticsFog() {}

    /**
     * Clear air kept beyond the outermost arena tile before the fog starts to bite. Set wide on
     * purpose: {@link CloudSeaRenderer} owns the near look now, and this band is only here to
     * swallow the far hills and treetops that geometry can't reach. Pulling it in tighter than
     * the cloud sea would double up and read as flat haze over the stylized layer.
     */
    private static final float ARENA_MARGIN = 26.0f;
    /** Thickness of the fog band: start -> fully opaque. */
    private static final float BAND_DEPTH = 44.0f;
    /** Fallback band when no footprint is known (an event cinematic outside any build). */
    private static final float FALLBACK_START = 56.0f;
    private static final float FALLBACK_END = 104.0f;
    /** Fog never starts closer than this, however tight the footprint math comes out. */
    private static final float MIN_START = 8.0f;
    /** Seconds for the band to cover ~63% of the distance to its target (ease in/out). */
    private static final float EASE_TAU = 0.4f;

    /** Current eased band, or -1 when the override is idle. */
    private static float currentStart = -1.0f;
    private static float currentEnd = -1.0f;
    /** Wall-clock stamp of the last eased step, for framerate-independent smoothing. */
    private static long lastStepNanos = 0L;
    /** 0..1: how far the band has closed in, so the colour lift fades in with it. */
    private static float closedIn = 0.0f;

    /** 0..1: how far the fog has closed in. {@link CloudSeaRenderer} fades its sheets on this so
     *  the stylized layer and the distance band arrive and leave together. */
    public static float closeInProgress() { return closedIn; }

    // ---- Mood: the fog reacting to what's happening in the fight ----

    /** Ramp in and out of a mood, in milliseconds. Short in, slower out. */
    private static final long MOOD_IN_MS = 350L;
    private static final long MOOD_OUT_MS = 1200L;

    private static int moodColor = 0;
    /** How far the band closes in at full mood strength: 1 = unchanged, 0.6 = 40% tighter. */
    private static float moodSqueeze = 1.0f;
    private static long moodStartMs = 0L;
    private static long moodEndMs = 0L;

    /**
     * Push the fog into a mood: a colour it drifts toward and a squeeze on the band, both eased
     * in and back out. Boss phase two floods the arena red and pulls the walls in; a won fight
     * opens them up and washes them bright.
     *
     * @param packedRgb    colour to drift toward
     * @param squeeze      band multiplier at full strength (&lt;1 closes in, &gt;1 opens up)
     * @param durationMs   how long to hold before easing back out
     */
    public static void setMood(int packedRgb, float squeeze, long durationMs) {
        moodColor = packedRgb;
        moodSqueeze = squeeze;
        moodStartMs = System.currentTimeMillis();
        moodEndMs = moodStartMs + Math.max(0L, durationMs);
    }

    /** Drop any mood immediately (combat end, disconnect). */
    public static void clearMood() {
        moodStartMs = 0L;
        moodEndMs = 0L;
    }

    /**
     * 0..1 mood strength right now. Darkness overrides any timed mood: while the player is
     * shrouded the fog stays black for as long as the effect lasts, which is the whole point of
     * the Hollow King's arena going lightless.
     */
    public static float moodStrength() {
        if (CombatState.getDarknessLevel() > 0 || CombatState.getBlindnessLevel() > 0) return 1.0f;
        long now = System.currentTimeMillis();
        if (now >= moodEndMs + MOOD_OUT_MS || moodEndMs == 0L) return 0.0f;
        if (now < moodStartMs) return 0.0f;
        if (now < moodStartMs + MOOD_IN_MS) {
            return (now - moodStartMs) / (float) MOOD_IN_MS;
        }
        if (now <= moodEndMs) return 1.0f;
        return 1.0f - (now - moodEndMs) / (float) MOOD_OUT_MS;
    }

    /** Packed RGB the fog is currently drifting toward, or 0 when no mood is active. */
    public static int moodColor() {
        if (CombatState.getDarknessLevel() > 0 || CombatState.getBlindnessLevel() > 0) {
            return 0x05060A;   // shrouded: the walls go lightless
        }
        return moodColor;
    }

    /** Band multiplier from the current mood - below 1 pulls the walls in. */
    private static float moodSqueeze() {
        float strength = moodStrength();
        if (strength <= 0.0f) return 1.0f;
        float squeeze = (CombatState.getDarknessLevel() > 0
            || CombatState.getBlindnessLevel() > 0) ? 0.55f : moodSqueeze;
        return 1.0f + (squeeze - 1.0f) * strength;
    }

    /** True while the player is inside a Craftics build that floats in the void. */
    public static boolean isActive() {
        return CombatState.isInCombat() || CombatState.isInScene() || CombatState.isCinematicActive();
    }

    /**
     * Resolve the fog band to use this frame, or {@code null} to leave vanilla's fog alone.
     *
     * @param camera       the render camera (band is measured from it)
     * @param vanillaStart fog start vanilla just computed
     * @param vanillaEnd   fog end vanilla just computed
     * @param advance      true on exactly one call per frame, so the ease steps once per frame
     *                     rather than once per fog pass (sky + terrain are separate passes)
     * @return {@code {start, end}} to override with, or {@code null} for no override
     */
    public static float[] resolve(Camera camera, float vanillaStart, float vanillaEnd, boolean advance) {
        if (camera == null) return null;
        boolean active = isActive();
        if (!active && currentEnd < 0.0f) { closedIn = 0.0f; return null; }  // idle, nothing to ease out

        float[] target = active ? targetBand(camera) : null;
        // Vanilla's own fog is already tighter than ours (underwater, in lava, Blindness,
        // Darkness): stand down completely, and forget the eased state so surfacing eases in
        // again from the real view distance. Compared against the TARGET, not the current
        // value - comparing the current value bailed on the very first frame, before the ease
        // had moved at all, which is why the band never closed in.
        if (target != null && target[1] >= vanillaEnd) {
            currentStart = -1.0f;
            currentEnd = -1.0f;
            closedIn = 0.0f;
            return null;
        }

        float goalStart = target != null ? target[0] : vanillaStart;
        float goalEnd = target != null ? target[1] : vanillaEnd;

        if (currentEnd < 0.0f) {
            // Easing IN: start from whatever vanilla is doing so the band closes in smoothly
            // instead of snapping the horizon shut the instant combat begins.
            currentStart = vanillaStart;
            currentEnd = vanillaEnd;
            closedIn = 0.0f;
            lastStepNanos = System.nanoTime();
        } else if (advance) {
            long now = System.nanoTime();
            float dt = Math.min(0.25f, (now - lastStepNanos) / 1_000_000_000.0f);
            lastStepNanos = now;
            float f = 1.0f - (float) Math.exp(-dt / EASE_TAU);
            currentStart += (goalStart - currentStart) * f;
            currentEnd += (goalEnd - currentEnd) * f;
            closedIn += ((target != null ? 1.0f : 0.0f) - closedIn) * f;
        }

        // Eased all the way back out to vanilla: drop the override.
        if (target == null && currentEnd >= vanillaEnd - 1.0f) {
            currentStart = -1.0f;
            currentEnd = -1.0f;
            closedIn = 0.0f;
            return null;
        }
        float end = Math.min(currentEnd, vanillaEnd);
        return new float[] { Math.min(Math.max(MIN_START, currentStart), end - 1.0f), end };
    }

    /**
     * Where the fog should sit for the build the player is currently in.
     *
     * <p>The band starts past the footprint's farthest corner, so the whole board / hall stays
     * clear no matter where the tactical camera is orbiting. Scenes reuse the same arena origin +
     * size fields as combat ({@code CombatState#setSceneBounds}), so trade halls size their own
     * band too; only an event cinematic with no bounds synced falls back to a fixed band.
     */
    private static float[] targetBand(Camera camera) {
        if (CombatState.getArenaWidth() > 0 && CombatState.getArenaHeight() > 0) {
            Vec3d eye = camera.getPos();
            int ox = CombatState.getArenaOriginX();
            int oy = CombatState.getArenaOriginY();
            int oz = CombatState.getArenaOriginZ();
            int w = CombatState.getArenaWidth();
            int h = CombatState.getArenaHeight();
            double farthest = 0.0;
            int[][] corners = { {ox, oz}, {ox + w, oz}, {ox, oz + h}, {ox + w, oz + h} };
            for (int[] c : corners) {
                double dx = c[0] - eye.x;
                double dy = oy - eye.y;
                double dz = c[1] - eye.z;
                farthest = Math.max(farthest, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            float squeeze = moodSqueeze();
            float start = ((float) farthest + ARENA_MARGIN) * squeeze;
            return new float[] { start, start + BAND_DEPTH * squeeze };
        }
        float squeeze = moodSqueeze();
        return new float[] { FALLBACK_START * squeeze, FALLBACK_END * squeeze };
    }

    /** Drop any eased state (world change / disconnect) so the next build eases in fresh. */
    public static void reset() {
        currentStart = -1.0f;
        currentEnd = -1.0f;
        closedIn = 0.0f;
    }
}
