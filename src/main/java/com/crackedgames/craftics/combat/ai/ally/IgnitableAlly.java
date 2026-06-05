package com.crackedgames.craftics.combat.ai.ally;

/**
 * Stat transform for an ally that can be ignited (flint &amp; steel) into a
 * one-shot lit state — it hits harder, moves faster, burns on hit, and dies
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
}
