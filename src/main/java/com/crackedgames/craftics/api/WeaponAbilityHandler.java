package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridArena;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * On-hit effect callback for a registered weapon.
 *
 * <p>Craftics invokes {@link #apply} after resolving the attack and applying base
 * damage, giving the ability a chance to add status effects, deal bonus damage to
 * extra targets, push combatants, and so on. The result carries the final damage
 * total, any combat-log messages, and the list of extra targets hit.
 *
 * <p>Use the ready-made factories in {@link Abilities} to build handlers from composable
 * building blocks, and chain them with {@link #and(WeaponAbilityHandler)}:
 *
 * <pre>{@code
 * WeaponAbilityHandler handler = Abilities.bleed()
 *     .and(Abilities.sweepAdjacent(0.10, 0.05));
 * }</pre>
 *
 * <p>Implement this interface directly only when the built-in factories cannot express
 * the desired behavior.
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface WeaponAbilityHandler {

    /**
     * Run the weapon's on-hit effect.
     *
     * @param player     the attacking player
     * @param target     the primary target of the attack
     * @param arena      the arena the fight is taking place in
     * @param baseDamage damage already dealt to the primary target before the ability runs
     * @param stats      the player's current progression stats, or {@code null} if not available
     * @param luckPoints the player's current luck point total, used to scale probability-based effects
     * @return the final {@link WeaponAbility.AttackResult} with total damage, messages, and extra targets
     */
    WeaponAbility.AttackResult apply(ServerPlayerEntity player, CombatEntity target,
                                      GridArena arena, int baseDamage,
                                      PlayerProgression.PlayerStats stats, int luckPoints);

    /**
     * Returns a handler that runs {@code this}, then passes the resulting damage total
     * to {@code next}. Both handlers' messages and extra targets are merged into the
     * final result.
     *
     * @param next the handler to run after this one
     * @return a composed handler that runs both effects in sequence
     */
    default WeaponAbilityHandler and(WeaponAbilityHandler next) {
        WeaponAbilityHandler first = this;
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            WeaponAbility.AttackResult r1 = first.apply(player, target, arena, baseDamage, stats, luckPoints);
            WeaponAbility.AttackResult r2 = next.apply(player, target, arena, r1.totalDamage(), stats, luckPoints);
            var msgs = new java.util.ArrayList<>(r1.messages());
            msgs.addAll(r2.messages());
            var extras = new java.util.ArrayList<>(r1.extraTargets());
            extras.addAll(r2.extraTargets());
            return new WeaponAbility.AttackResult(r2.totalDamage(), msgs, extras);
        };
    }
}
