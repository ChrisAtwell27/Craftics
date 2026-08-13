package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * What a weapon ability intends to do, described rather than done.
 *
 * <p>The existing {@link WeaponAbilityHandler#apply} contract applies its own damage and hands
 * back a total the caller uses immediately. That is why a chakram flies only after its damage
 * has already landed, why its ricochet skips flame, knockback and the Simply Swords procs (the
 * bounce calls {@code takeDamage} directly, around the whole on-hit pipeline), and why a
 * crossbow's pierce cannot be shown passing through a line of enemies. All one cause.
 *
 * <p>A plan separates the decision from the execution. The handler works out WHO gets hit, for
 * HOW much, and in what ORDER; the caller flies the projectile and resolves each hop as it
 * lands, through the ordinary on-hit path so every effect a normal strike would apply is
 * applied here too.
 *
 * @param hops       targets in order. Hop 0 is the primary target; the rest are ricochets,
 *                   pierces, or whatever the weapon calls them
 * @param returnsToThrower true for a weapon that comes back (a chakram); false for one that
 *                   does not (an arrow)
 * @param messages   combat-log lines, shown when the last hop lands rather than at release
 *
 * @since 0.3.9
 */
public record AbilityPlan(List<Hop> hops, boolean returnsToThrower, List<String> messages) {

    /**
     * One target in the chain.
     *
     * @param target  who is hit
     * @param damage  damage for this hop specifically, already scaled (a ricochet is usually
     *                a fraction of the primary hit)
     * @param appliesOnHitEffects whether this hop runs the weapon's normal on-hit pipeline -
     *                enchantments, procs, knockback. True for anything a player would call a
     *                hit; false only for a purely environmental tick that should not proc
     */
    public record Hop(CombatEntity target, int damage, boolean appliesOnHitEffects) {
        public Hop(CombatEntity target, int damage) {
            this(target, damage, true);
        }
    }

    /** A plan that hits one target and stops - the shape of most weapons. */
    public static AbilityPlan single(CombatEntity target, int damage) {
        return new AbilityPlan(List.of(new Hop(target, damage)), false, List.of());
    }

    /** Nothing to do beyond the base hit. */
    public static AbilityPlan none() {
        return new AbilityPlan(List.of(), false, List.of());
    }

    /** Total damage across every hop, for the callers that still want one number. */
    public int totalDamage() {
        int sum = 0;
        for (Hop h : hops) sum += h.damage();
        return sum;
    }

    /**
     * Run this plan, then {@code next}, as one chain.
     *
     * <p>The composing counterpart to {@link WeaponAbilityHandler#and}. It matters for the
     * Simply Swords uniques, which are built as {@code chakramAbility().and(theirOwnProc())}:
     * if composition dropped to results rather than plans, the unique's proc would be silently
     * lost the moment the base ability converted.
     */
    public AbilityPlan andThen(AbilityPlan next) {
        if (next == null) return this;
        List<Hop> merged = new ArrayList<>(hops);
        merged.addAll(next.hops());
        List<String> msgs = new ArrayList<>(messages);
        msgs.addAll(next.messages());
        return new AbilityPlan(merged, returnsToThrower || next.returnsToThrower(), msgs);
    }
}
