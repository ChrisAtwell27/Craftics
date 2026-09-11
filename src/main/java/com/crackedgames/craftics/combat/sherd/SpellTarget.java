package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;

/**
 * One resolved recipient of a spell step: an entity, or a bare tile when the step is placing
 * something rather than hitting somebody.
 *
 * <p>{@code hop} is what makes chains and falloff expressible as data. The selector stamps how
 * many links from the origin this target sits, and a damage effect reads it to apply decay. It
 * is 0 for the primary target, for every member of a flat AoE, and for a self-cast - so an
 * effect that ignores {@code hop} behaves identically whether or not the spell chains, and a
 * decay value only ever bites on a spell that actually asked to chain.
 */
public final class SpellTarget {

    private final CombatEntity entity;
    private final GridPos tile;
    private final int hop;

    private SpellTarget(CombatEntity entity, GridPos tile, int hop) {
        this.entity = entity;
        this.tile = tile;
        this.hop = hop;
    }

    public static SpellTarget of(CombatEntity entity, int hop) {
        return new SpellTarget(entity, entity.getGridPos(), hop);
    }

    public static SpellTarget ofTile(GridPos tile) {
        return new SpellTarget(null, tile, 0);
    }

    /** The entity hit, or null when this target is a bare tile. */
    public CombatEntity entity() { return entity; }

    /** True when there is an entity to affect. Tile-only targets answer false. */
    public boolean hasEntity() { return entity != null; }

    /**
     * Where this target is. For an entity this re-reads its CURRENT grid position rather than
     * the one captured at selection: knockback and pull move entities mid-step, and a later
     * effect (or the impact visual) that used the stale tile would land where the target used
     * to be.
     */
    public GridPos tile() { return entity != null ? entity.getGridPos() : tile; }

    /** Links from the origin of the chain. 0 for a primary, flat-AoE or self target. */
    public int hop() { return hop; }

    public String name() { return entity != null ? entity.getDisplayName() : "the ground"; }
}
