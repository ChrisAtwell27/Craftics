package com.crackedgames.craftics.api;

/**
 * Pairs a display name with a {@link CombatEffectHandler} instance.
 *
 * <p>The name is used in combat log messages and for debugging. Instances are created
 * by {@link StatModifiers#addCombatEffect} and collected into
 * {@link StatModifiers#getCombatEffects()} for the fight.
 *
 * @param name    display name for log messages and debugging
 * @param handler the combat effect lifecycle callbacks
 * @since 0.2.0
 */
public record NamedCombatEffect(String name, CombatEffectHandler handler) {}
