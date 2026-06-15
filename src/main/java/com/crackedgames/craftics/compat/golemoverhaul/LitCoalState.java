package com.crackedgames.craftics.compat.golemoverhaul;

import com.crackedgames.craftics.combat.ai.ally.IgnitableAlly;

/**
 * Pure-logic rules for the lit coal golem. Using flint &amp; steel on a coal golem
 * ignites it: it hits much harder, moves faster, applies burn on hit, and dies
 * after its next attack. The runtime "is lit" state lives on the CombatEntity
 * ({@code litOneShot}); these statics describe the stat transform so they can be
 * unit-tested without the combat runtime.
 *
 * <p>The {@link #IGNITABLE} adapter exposes these statics as the core
 * {@link IgnitableAlly} hook so the compat can register the coal golem with
 * {@code IgnitableAllyRegistry} and the core never references this class directly.
 */
public final class LitCoalState {
    private LitCoalState() {}

    public static final String COAL_GOLEM_ID = "golemoverhaul:coal_golem";

    /** Core hook adapter wrapping the lit-coal stat transform. */
    public static final IgnitableAlly IGNITABLE = new IgnitableAlly() {
        @Override public int litAttack(int baseAttack) { return LitCoalState.litAttack(baseAttack); }
        @Override public int litSpeed(int baseSpeed)   { return LitCoalState.litSpeed(baseSpeed);   }
        @Override public void applyLitVisual(net.minecraft.entity.mob.MobEntity mob) {
            LitCoalState.setModLit(mob, true);
        }
    };

    // Resolved CoalGolem#setLit(boolean), cached after first lookup. Craftics has no
    // compile dependency on Golem Overhaul, so the mod's lit texture flag is flipped
    // reflectively - the renderer reads the synced ID_LIT data-tracker setLit() writes.
    private static volatile java.lang.reflect.Method setLitMethod;

    /**
     * Flip Golem Overhaul's {@code CoalGolem} ignited texture/flag on the live mob.
     * Best-effort and cosmetic: if the mod is absent or the method shape differs
     * (other version), it silently does nothing rather than breaking combat.
     */
    static void setModLit(net.minecraft.entity.mob.MobEntity mob, boolean lit) {
        if (mob == null) return;
        try {
            java.lang.reflect.Method m = setLitMethod;
            if (m == null || !m.getDeclaringClass().isInstance(mob)) {
                m = mob.getClass().getMethod("setLit", boolean.class);
                setLitMethod = m;
            }
            m.invoke(mob, lit);
        } catch (ReflectiveOperationException e) {
            // CoalGolem#setLit not present (mod missing or different build) - skip.
        }
    }

    /** Lit attack: heavy fire-aspect strike. */
    public static int litAttack(int baseAttack) {
        return baseAttack * 2 + 3;
    }

    /** Lit speed: rushes its target. */
    public static int litSpeed(int baseSpeed) {
        return baseSpeed + 2;
    }
}
