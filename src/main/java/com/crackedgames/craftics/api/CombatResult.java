package com.crackedgames.craftics.api;

import java.util.List;

/**
 * Return type for combat effect callbacks that can modify combat values.
 * Carries a modified value, combat log messages, and a cancellation flag.
 */
public record CombatResult(
    int modifiedValue,
    List<String> messages,
    boolean cancelled
) {
    /** No change — pass through the original value. */
    public static CombatResult unchanged(int originalValue) {
        return new CombatResult(originalValue, List.of(), false);
    }

    /** Modify the value and add a combat log message. */
    public static CombatResult modify(int newValue, String message) {
        return new CombatResult(newValue, List.of(message), false);
    }

    /** Cancel the action entirely (e.g., prevent death). */
    public static CombatResult cancel(String message) {
        return new CombatResult(0, List.of(message), true);
    }
}
