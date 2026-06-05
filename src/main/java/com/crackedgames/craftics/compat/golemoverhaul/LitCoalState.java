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
    };

    /** Lit attack: heavy fire-aspect strike. */
    public static int litAttack(int baseAttack) {
        return baseAttack * 2 + 3;
    }

    /** Lit speed: rushes its target. */
    public static int litSpeed(int baseSpeed) {
        return baseSpeed + 2;
    }
}
