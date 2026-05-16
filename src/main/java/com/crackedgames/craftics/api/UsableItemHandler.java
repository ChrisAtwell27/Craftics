package com.crackedgames.craftics.api;

/**
 * Runs the effect of a registered usable item when the player activates it during a
 * Craftics turn.
 *
 * <p>The handler receives a {@link UsableItemContext} exposing the player, the arena,
 * the targeted tile, and the operations an item may perform (deal damage, heal, apply
 * effects, knock back, place tile effects, …). It returns an {@link ItemUseResult} that
 * tells Craftics whether to spend AP and consume the item.
 *
 * <p>Build handlers from the composable factories in {@link ItemEffects}, or implement
 * this interface directly for custom logic:
 *
 * <pre>{@code
 * UsableItemHandler handler = ItemEffects.heal(6)
 *     .and(ItemEffects.applyToSelf(CombatEffects.EffectType.ABSORPTION, 4, 0));
 * }</pre>
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface UsableItemHandler {

    /** Apply the item's effect. Called once per activation. */
    ItemUseResult use(UsableItemContext ctx);

    /**
     * Returns a handler that runs {@code this}, then {@code next} — but only if
     * {@code this} succeeded. If {@code this} fails, {@code next} is skipped and the
     * failure is propagated.
     */
    default UsableItemHandler and(UsableItemHandler next) {
        return ctx -> {
            ItemUseResult first = this.use(ctx);
            if (!first.success()) {
                return first;
            }
            return next.use(ctx);
        };
    }
}
