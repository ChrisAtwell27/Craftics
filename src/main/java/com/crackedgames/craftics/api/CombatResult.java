package com.crackedgames.craftics.api;

import java.util.List;

/**
 * Return type for {@link CombatEffectHandler} callbacks that can modify combat values.
 *
 * <p>Carries the modified value, optional combat-log messages, and a cancellation flag.
 * Use the static factories instead of the record constructor directly.
 *
 * @param modifiedValue  the value after modification; passed back to the combat engine
 * @param messages       combat-log lines to display to the player; may be empty
 * @param cancelled      if {@code true} the originating action is suppressed entirely
 *                       (for example, lethal damage that should be prevented)
 * @since 0.2.0
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
