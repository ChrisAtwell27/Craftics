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
 * WeaponAbilityHandler handler = Abilities.sweepAdjacent(0.10, 0.05)
 *     .and(Abilities.stun(0.05, 0.03));
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
     * Describe what this ability would do, without doing any of it.
     *
     * <p>The forward-looking half of this interface. {@link #apply} resolves everything on the
     * spot and returns a total, which is why an ability's projectile can only ever be shown
     * flying AFTER its damage has landed, and why a ricochet that calls {@code takeDamage}
     * directly skips every on-hit effect the weapon has. A plan instead says who is hit, for
     * how much, in what order - and lets the caller fly the projectile and resolve each hop as
     * it arrives, through the ordinary on-hit path.
     *
     * <p>The default implementation runs the legacy {@link #apply} and reports what it did
     * after the fact, so every existing weapon keeps working unchanged while abilities are
     * converted one at a time. A converted ability overrides this and leaves {@code apply}
     * delegating to it. Once nothing implements {@code apply} directly, both it and this
     * default go away.
     *
     * @return the intended chain of hits; never null
     * @since 0.3.9
     */
    default AbilityPlan plan(ServerPlayerEntity player, CombatEntity target,
                             GridArena arena, int baseDamage,
                             PlayerProgression.PlayerStats stats, int luckPoints) {
        WeaponAbility.AttackResult done = apply(player, target, arena, baseDamage, stats, luckPoints);
        // The legacy path has ALREADY applied its damage and effects. Reporting hops here
        // would double them, so the plan carries the messages only - the caller sees the same
        // behaviour it always did, with no second application.
        return new AbilityPlan(java.util.List.of(), false, done.messages());
    }

    /**
     * Whether {@link #plan} is safe to call ahead of the attack.
     *
     * <p>Not cosmetic - it is the guard on a live trap. The default {@code plan} above runs the
     * legacy {@code apply}, which APPLIES everything. A caller that asked every weapon for its
     * plan in order to launch the projectile first would, for every unconverted weapon, resolve
     * the whole hit at the moment of the throw. So the caller asks this first, and only a
     * handler that has genuinely converted - one whose {@code plan} computes targets and touches
     * nothing - answers true.
     *
     * @since 0.3.9
     */
    default boolean isPlanned() {
        return false;
    }

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
        // An anonymous class, not a lambda. A lambda can only carry apply(), so a composed
        // handler would report isPlanned() == false and fall back to the legacy path - which
        // would silently un-convert exactly the weapons built by composition. Chomp'olotl and
        // Tempest are chakramAbility().and(theirProc()); a lambda here is how they would quietly
        // lose their flight while the plain chakram kept it.
        return new WeaponAbilityHandler() {
            @Override
            public WeaponAbility.AttackResult apply(ServerPlayerEntity player, CombatEntity target,
                                                    GridArena arena, int baseDamage,
                                                    PlayerProgression.PlayerStats stats, int luckPoints) {
                WeaponAbility.AttackResult r1 = first.apply(player, target, arena, baseDamage, stats, luckPoints);
                WeaponAbility.AttackResult r2 = next.apply(player, target, arena, r1.totalDamage(), stats, luckPoints);
                var msgs = new java.util.ArrayList<>(r1.messages());
                msgs.addAll(r2.messages());
                var extras = new java.util.ArrayList<>(r1.extraTargets());
                extras.addAll(r2.extraTargets());
                return new WeaponAbility.AttackResult(r2.totalDamage(), msgs, extras);
            }

            @Override
            public boolean isPlanned() {
                return first.isPlanned() || next.isPlanned();
            }

            /**
             * Only the CONVERTED halves contribute geometry.
             *
             * <p>Deliberately not {@code first.plan().andThen(next.plan())}: for an unconverted
             * half, {@code plan} is the wrapper that runs {@code apply}, so composing it here
             * would fire that half's whole effect at throw time - a Chomp'olotl axolotl
             * spawning before the disc had left the hand. An unconverted half stays exactly
             * where it was, on the impact-time {@code apply} path, and only contributes hops
             * once it converts.
             */
            @Override
            public AbilityPlan plan(ServerPlayerEntity player, CombatEntity target,
                                    GridArena arena, int baseDamage,
                                    PlayerProgression.PlayerStats stats, int luckPoints) {
                AbilityPlan p = AbilityPlan.none();
                if (first.isPlanned()) {
                    p = p.andThen(first.plan(player, target, arena, baseDamage, stats, luckPoints));
                }
                if (next.isPlanned()) {
                    p = p.andThen(next.plan(player, target, arena, baseDamage, stats, luckPoints));
                }
                return p;
            }
        };
    }
}
