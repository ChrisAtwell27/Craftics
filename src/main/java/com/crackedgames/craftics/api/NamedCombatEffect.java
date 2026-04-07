package com.crackedgames.craftics.api;

/**
 * Pairs a display name with a combat effect handler instance.
 * The name is used for combat log messages and debugging.
 */
public record NamedCombatEffect(String name, CombatEffectHandler handler) {}
