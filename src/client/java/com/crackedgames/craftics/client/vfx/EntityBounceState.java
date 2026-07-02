package com.crackedgames.craftics.client.vfx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side visual knock-up offsets, fed by {@code VfxClientPayload}'s
 * EntityBounce primitive and read every frame by the LivingEntityRenderer
 * bounce mixin. Purely cosmetic: the entity's real position never changes,
 * so a desync can never strand a mob in the air - the offset simply expires.
 *
 * <p>The curve is a main parabolic hop followed by a smaller rebound hop
 * (roughly how a ragdoll settles), wall-clock driven so it plays at the same
 * speed at any frame rate.
 */
public final class EntityBounceState {

    private record Bounce(long startMs, long durationMs, float amplitude) {}

    private static final Map<Integer, Bounce> ACTIVE = new ConcurrentHashMap<>();

    /** Fraction of the total duration spent on the first (main) hop. */
    private static final float MAIN_HOP_END = 0.62f;
    /** Rebound hop height as a fraction of the main amplitude. */
    private static final float REBOUND_SCALE = 0.35f;

    private EntityBounceState() {}

    /** Start (or restart) a bounce for each entity id. */
    public static void trigger(int[] entityIds, float amplitude, int durationTicks) {
        long now = System.currentTimeMillis();
        long durMs = Math.max(50L, durationTicks * 50L);
        for (int id : entityIds) {
            ACTIVE.put(id, new Bounce(now, durMs, amplitude));
        }
    }

    /** Current render Y offset for the entity, 0 when it isn't bouncing. */
    public static float offsetFor(int entityId) {
        Bounce b = ACTIVE.get(entityId);
        if (b == null) return 0f;
        float t = (System.currentTimeMillis() - b.startMs()) / (float) b.durationMs();
        if (t >= 1f) {
            ACTIVE.remove(entityId);
            return 0f;
        }
        if (t < MAIN_HOP_END) {
            float u = t / MAIN_HOP_END;
            return b.amplitude() * 4f * u * (1f - u);
        }
        float v = (t - MAIN_HOP_END) / (1f - MAIN_HOP_END);
        return b.amplitude() * REBOUND_SCALE * 4f * v * (1f - v);
    }

    /** Drop everything. Called on ExitCombatPayload and on disconnect, which
     *  bounds the map - entries for mobs that die mid-bounce (and so are never
     *  queried again) linger only until the fight ends. */
    public static void clear() {
        ACTIVE.clear();
    }
}
