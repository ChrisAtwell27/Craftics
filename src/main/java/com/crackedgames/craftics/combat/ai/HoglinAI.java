package com.crackedgames.craftics.combat.ai;

/**
 * Hoglin AI: Aggressive 2x2 beast with bull rush and ground stomp.
 *
 * Behaviorally identical to the ravager — straight-line bull rush with charge
 * damage and knockback, a ground stomp AoE when surrounded by 2+ attackers,
 * tusk swipes with knockback otherwise — so it shares {@link RavagerAI}'s
 * implementation instead of carrying its own copy (which had drifted: the old
 * duplicate documented the stomp but never implemented it).
 */
public class HoglinAI extends RavagerAI {
}
