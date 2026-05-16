package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The operations a {@link UsableItemHandler} may perform during combat.
 *
 * <p>Craftics passes a context to the handler when the player uses a registered item.
 * It exposes read access to the fight (player, arena, target, combatants) and a typed
 * set of mutations (damage, healing, status effects, knockback, teleport, tile effects)
 * so addon items never need to touch Craftics internals directly.
 *
 * @since 0.2.0
 */
public interface UsableItemContext {

    // --- Read access ---------------------------------------------------------

    /** The player using the item. */
    ServerPlayerEntity player();

    /** The arena the fight is taking place in. */
    GridArena arena();

    /** The tile the player targeted. For a {@link TargetType#SELF} item this is the player's tile. */
    GridPos targetPos();

    /** The combatant standing on {@link #targetPos()}, or {@code null} if the tile is empty. */
    @Nullable
    CombatEntity targetEntity();

    /** The {@link ItemStack} being used. */
    ItemStack stack();

    /** Every combatant in the fight — enemies and allies alike. Treat as read-only. */
    List<CombatEntity> combatants();

    // --- Damage & healing ----------------------------------------------------

    /**
     * Deal {@code amount} damage to {@code target} through its defense.
     *
     * @return the actual damage dealt after defense reduction
     */
    int damage(CombatEntity target, int amount);

    /** Heal the player by {@code amount} (capped at their max health). */
    void healPlayer(int amount);

    /** Heal {@code entity} by {@code amount} (capped at its max HP). */
    void healEntity(CombatEntity entity, int amount);

    // --- Status effects ------------------------------------------------------

    /** Apply a combat effect to the player. Supports every {@link CombatEffects.EffectType}. */
    void applyPlayerEffect(CombatEffects.EffectType type, int turns, int amplifier);

    /**
     * Apply a combat effect to a combatant. Enemies support a subset of effect types
     * (poison, wither, burning, soaked, slowness, confusion, weakness, bleeding);
     * unsupported types are ignored.
     */
    void applyEffect(CombatEntity target, CombatEffects.EffectType type, int turns, int amplifier);

    /**
     * Apply a custom (addon-registered) status effect to a combatant.
     *
     * @implNote Custom effects are introduced in Phase 2 of the Addon SDK. Until then
     *           this is a no-op.
     */
    void applyCustomEffect(CombatEntity target, String effectId, int turns, int amplifier);

    // --- Control -------------------------------------------------------------

    /** Stun {@code target} so it loses its next turn. */
    void stun(CombatEntity target);

    /** Push {@code target} up to {@code distance} tiles directly away from the player. */
    void knockback(CombatEntity target, int distance);

    /** Teleport the player to {@code pos} (must be a walkable, unoccupied, in-bounds tile). */
    void teleportPlayer(GridPos pos);

    // --- World / tiles -------------------------------------------------------

    /**
     * Place a tile effect at {@code pos}. {@code effectType} is a Craftics tile-effect
     * key (e.g. {@code "lava"}, {@code "campfire"}, {@code "poison_cloud"}).
     */
    void placeTileEffect(GridPos pos, String effectType);

    // --- Feedback ------------------------------------------------------------

    /** Send a chat message to the player. */
    void message(String text);
}
