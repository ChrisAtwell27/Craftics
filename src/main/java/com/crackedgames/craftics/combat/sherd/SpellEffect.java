package com.crackedgames.craftics.combat.sherd;

/**
 * One thing a spell does to one target.
 *
 * <p>The whole point of the sherd rework. Damage, burning, knockback, an execute, a heal, a
 * buff, a summon - each used to exist only as statements inside one sherd's method, so "make
 * the lightning also burn" meant writing burning code into the lightning method, and the burn
 * sherd's version of the same idea stayed where it was. As an interface, an effect is written
 * once and attached to anything.
 *
 * <p>Implementations live in {@link Effects} as small immutable records with factory methods,
 * so a definition reads as a list of nouns rather than a block of statements. They must be safe
 * to run against a target that has already died earlier in the same cast - a chain that kills
 * its first link still walks the rest - so each one re-checks what it needs.
 */
@FunctionalInterface
public interface SpellEffect {

    /**
     * Apply this effect.
     *
     * @param ctx    everything the effect may touch
     * @param target the recipient, carrying its chain depth for falloff
     * @param report where to record damage, chat fragments and impact tiles
     */
    void apply(SpellContext ctx, SpellTarget target, SpellReport report);
}
