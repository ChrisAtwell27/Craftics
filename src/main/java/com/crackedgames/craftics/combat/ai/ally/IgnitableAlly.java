package com.crackedgames.craftics.combat.ai.ally;

/**
 * Stat transform for an ally that can be ignited (flint &amp; steel) into a
 * one-shot lit state - it hits harder, moves faster, burns on hit, and dies
 * after its next attack. The runtime "is lit" state lives on the
 * {@code CombatEntity} ({@code litOneShot}); this describes only the attack/speed
 * boost so compat modules can supply mod-specific numbers without the core
 * referencing them.
 *
 * @since 0.3.0
 */
public interface IgnitableAlly {

    /** Lit attack derived from the ally's base attack. */
    int litAttack(int baseAttack);

    /** Lit speed derived from the ally's base speed. */
    int litSpeed(int baseSpeed);

    /**
     * Flip the mod-specific "lit" visual state on the ally's live world mob, if the
     * source mod has one. Default is a no-op; a compat module overrides this to
     * switch the mob to its ignited texture/model (e.g. Golem Overhaul's
     * {@code CoalGolem#setLit}, which drives a synced data-tracker the renderer reads
     * to pick {@code coal_golem_lit.png}). The vanilla fire overlay alone can't do
     * this - fire-immune golems don't even render it - so the mod's own lit flag is
     * the only way to show the ignited texture. Called server-side right after an
     * ally is ignited; the data-tracker change syncs to clients.
     *
     * @param mob the ally's live {@link net.minecraft.entity.mob.MobEntity}, or {@code null}
     */
    default void applyLitVisual(net.minecraft.entity.mob.MobEntity mob) { }
}
