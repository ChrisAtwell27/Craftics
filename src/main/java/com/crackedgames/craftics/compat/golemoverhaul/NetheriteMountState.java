package com.crackedgames.craftics.compat.golemoverhaul;

/**
 * Reflective bridge to Golem Overhaul's netherite golem "charged" state — the
 * lit furnace-chest glow. Craftics has no compile dependency on the mod, so the
 * state is flipped reflectively, exactly like {@link LitCoalState#setModLit} does
 * for the coal golem.
 *
 * <p>{@code NetheriteGolem#setCharged(boolean)} drives a synced {@code ID_CHARGED}
 * data-tracker that the mod's {@code NetheriteGolemFireLayer} reads to swap the
 * furnace overlay to {@code netherite_golem_charged_glow_open.png} (furnace open /
 * glowing). Craftics turns it on while the mount moves, summons, or fires its lava
 * bonus so the golem visibly powers up, then back off.
 */
public final class NetheriteMountState {
    private NetheriteMountState() {}

    public static final String NETHERITE_GOLEM_ID = "golemoverhaul:netherite_golem";

    // Resolved NetheriteGolem#setCharged(boolean), cached after first lookup.
    private static volatile java.lang.reflect.Method setChargedMethod;

    /**
     * Flip the netherite golem's furnace glow on the live mount mob. Best-effort and
     * cosmetic: if the mod is absent or the method shape differs (other version), it
     * silently does nothing rather than breaking combat.
     */
    public static void setCharged(net.minecraft.entity.mob.MobEntity mob, boolean charged) {
        if (mob == null) return;
        try {
            java.lang.reflect.Method m = setChargedMethod;
            if (m == null || !m.getDeclaringClass().isInstance(mob)) {
                m = mob.getClass().getMethod("setCharged", boolean.class);
                setChargedMethod = m;
            }
            m.invoke(mob, charged);
        } catch (ReflectiveOperationException e) {
            // NetheriteGolem#setCharged not present (mod missing or different build) — skip.
        }
    }
}
