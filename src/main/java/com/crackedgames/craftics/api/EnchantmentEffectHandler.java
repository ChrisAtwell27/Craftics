package com.crackedgames.craftics.api;

/**
 * Applies the Craftics stat bonuses for a registered enchantment.
 *
 * <p>Craftics scans the player's weapon and armor slots for registered enchantments
 * before each combat. For each enchantment found, the corresponding handler is called
 * with the enchantment level and an accumulator; the handler adds bonuses to that
 * accumulator, which Craftics then merges into the player's combat stats.
 *
 * <p>Register via {@code CrafticsAPI.registerEnchantment}:
 *
 * <pre>{@code
 * CrafticsAPI.registerEnchantment("mymod:fortify", ctx -> {
 *     ctx.getModifiers().add(TrimEffects.Bonus.DEFENSE, ctx.getLevel());
 * });
 * }</pre>
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface EnchantmentEffectHandler {

    /**
     * Apply the enchantment's Craftics stat bonuses.
     *
     * @param ctx context carrying the enchantment level, the player, and the
     *            {@link StatModifiers} accumulator to write bonuses into
     */
    void apply(EnchantmentContext ctx);
}
