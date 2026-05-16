package com.crackedgames.craftics.api;

import org.jetbrains.annotations.Nullable;

/**
 * The outcome of a {@link UsableItemHandler}.
 *
 * <p>On {@link #ok()}, Craftics spends the item's AP cost, consumes one of the item if
 * its {@link com.crackedgames.craftics.api.registry.UsableItemEntry entry} is marked
 * consumable, and runs a death/victory check. On {@link #fail(String)}, nothing is spent
 * and the failure message is shown to the player.
 *
 * @param success        whether the item was used
 * @param failureMessage player-facing reason when {@code success} is {@code false};
 *                       {@code null} on success
 * @since 0.2.0
 */
public record ItemUseResult(boolean success, @Nullable String failureMessage) {

    private static final ItemUseResult OK = new ItemUseResult(true, null);

    /** The item was used successfully — AP is spent and the item may be consumed. */
    public static ItemUseResult ok() {
        return OK;
    }

    /**
     * The item could not be used. {@code reason} is shown to the player and no AP is
     * spent — typically used for an invalid target or an unmet precondition.
     */
    public static ItemUseResult fail(String reason) {
        return new ItemUseResult(false, reason);
    }
}
