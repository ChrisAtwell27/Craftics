package com.crackedgames.craftics.api;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Context passed to an {@link EnchantmentEffectHandler} when Craftics scans a
 * player's equipment for registered enchantments.
 *
 * <p>The handler reads {@link #getLevel()} to scale its bonuses, then writes them
 * into {@link #getModifiers()}. The {@link #getPlayer()} accessor is available for
 * handlers that need to inspect additional player state.
 *
 * @since 0.2.0
 */
public class EnchantmentContext {
    private final int level;
    private final ServerPlayerEntity player;
    private final StatModifiers modifiers;

    /**
     * Constructs the context for one enchantment scan.
     *
     * @param level     the highest level of this enchantment found on the player's weapon
     *                  or armor; always at least {@code 1}
     * @param player    the player being scanned
     * @param modifiers the accumulator the handler should write stat bonuses into
     */
    public EnchantmentContext(int level, ServerPlayerEntity player, StatModifiers modifiers) {
        this.level = level;
        this.player = player;
        this.modifiers = modifiers;
    }

    /** The highest level of this enchantment found on the player's weapon or armor. */
    public int getLevel() { return level; }

    /** The player being scanned. */
    public ServerPlayerEntity getPlayer() { return player; }

    /** The stat-modifier accumulator. Add bonuses here to contribute them to combat stats. */
    public StatModifiers getModifiers() { return modifiers; }
}
