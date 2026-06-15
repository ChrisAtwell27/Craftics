package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;

import java.util.List;

/**
 * Narrow, controlled view of combat state handed to an {@link AllyRoundHook}.
 *
 * <p>Round hooks never touch the {@code CombatManager} directly - they go through
 * this context, which exposes only the four operations a per-round ally effect
 * needs: summoning a temporary ally, healing an ally, listing the living allies,
 * and broadcasting a message to the party. The manager supplies the implementation
 * wired to its own arena, ally/enemy list, and spawn/heal/message plumbing.
 *
 * @since 0.2.0
 */
public interface AllyCombatContext {

    /**
     * Summon a temporary ally near {@code near}, on a free walkable adjacent tile.
     * No-op if there is no free adjacent tile. The summoned ally inherits its stats
     * from the ally registry (or sensible defaults if unregistered) and is owned by
     * {@code near}'s owner.
     *
     * @param entityTypeId   the mob to summon, e.g. {@code minecraft:bee}
     * @param near           the ally to summon next to (typically the hook's {@code self})
     * @param lifespanRounds rounds the summon lives before auto-despawning;
     *                       {@code <= 0} means permanent
     */
    void summonAlly(String entityTypeId, CombatEntity near, int lifespanRounds);

    /** Restore {@code amount} HP to {@code target} (clamped to its max HP). */
    void healAlly(CombatEntity target, int amount);

    /** The living allies currently in the arena (a stable copy - safe to iterate). */
    List<CombatEntity> allies();

    /** Broadcast a message to every party participant. */
    void message(String msg);
}
