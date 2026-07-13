package com.crackedgames.craftics.api;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * A weapon action aimed at a tile rather than at an enemy.
 *
 * <p>An ordinary {@link WeaponAbilityHandler} runs after a hit has landed, so it needs a
 * target. Some weapons instead do something to the ground: rain arrows across the arena,
 * lay a trap, seed a field. Those have nothing to hit and would be rejected by the normal
 * attack path ("No enemy in range of that attack!"), so they get their own handler.
 *
 * <p>Craftics calls {@link #cast} when the player attacks an empty tile with a weapon that
 * has one registered, after charging the weapon's AP and its ammunition. The handler owns
 * everything the cast does; it reports back only what to tell the player.
 *
 * <p>Reach the live {@code CombatManager} - to lay traps, summon, or damage tiles - through
 * {@code CombatManager.getActiveCombat(player.getUuid())}, the same way a summoning ability
 * does.
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface TargetlessCastHandler {

    /**
     * Run the cast.
     *
     * @param player     the casting player
     * @param aimTile    the tile they clicked; always in bounds, may hold anything or nothing
     * @param arena      the arena the fight is in
     * @param baseDamage the weapon's resolved attack power, already scaled by affinity,
     *                   enchants, and progression - use it so a cast keeps pace with a shot
     * @param luckPoints the player's luck total, for probability-based casts
     * @return combat-log lines to show the player; empty if the cast says nothing
     */
    List<String> cast(ServerPlayerEntity player, GridPos aimTile, GridArena arena,
                      int baseDamage, int luckPoints);
}
