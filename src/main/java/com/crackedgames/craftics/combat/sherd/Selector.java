package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who a spell step hits, as data.
 *
 * <p>This is the half of the old sherd methods that was never reusable. Cleave, splash, chain
 * lightning, the tidal AoE and the petsplosion were five hand-rolled loops over
 * {@code enemies}, each with its own distance check, its own "skip allies and the primary"
 * condition, and its own idea of what counted as in range. None of them could be moved to
 * another sherd without being rewritten. Expressed here once, any of them can be attached to
 * any spell by changing a field.
 *
 * <p>Resolution runs in three stages, and every stage is optional:
 * <ol>
 *   <li><b>Origins</b> - one point (the aimed tile / the caster) or many (every pet), from
 *       {@link Origin}</li>
 *   <li><b>Gather</b> - entities within {@link #radius} of each origin that pass the side and
 *       condition filters, stamped hop 0</li>
 *   <li><b>Chain</b> - breadth-first hops outward from those, stamped with their depth</li>
 * </ol>
 *
 * <p>Duplicates are kept when the origins are plural. A petsplosion that catches one enemy in
 * two pets' blasts must hit it twice, separately resisted, and de-duplicating would silently
 * halve a mechanic. A single-origin selector cannot produce duplicates, so this costs nothing
 * anywhere else.
 */
public final class Selector {

    /** Where the step measures from. */
    public enum Origin {
        /** The tile the player aimed at. The default for a targeted spell. */
        TARGET_TILE,
        /** The caster's own tile, for self-centred bursts like the tidal surge. */
        CASTER,
        /** Every live pet, each its own blast centre. Produces one origin per pet. */
        EACH_PET
    }

    /** Which side of the fight is eligible. */
    public enum Side {
        ENEMY,
        /** Allies excluding seeker projectiles - see {@link SpellContext#livePets()}. */
        ALLY,
        ANY
    }

    /** A breadth-first spread outward from the gathered targets. */
    public record Chain(int maxHops, int hopRange, int decayPerHop) {}

    private final Origin origin;
    private final Side side;
    private final int radius;
    private final boolean includeOrigin;
    private final Chain chain;
    private final int maxTargets;
    private final double belowHpFraction;
    private final boolean excludeBosses;
    private final boolean tileOnly;
    private final boolean shuffle;

    private Selector(Builder b) {
        this.origin = b.origin;
        this.side = b.side;
        this.radius = b.radius;
        this.includeOrigin = b.includeOrigin;
        this.chain = b.chain;
        this.maxTargets = b.maxTargets;
        this.belowHpFraction = b.belowHpFraction;
        this.excludeBosses = b.excludeBosses;
        this.tileOnly = b.tileOnly;
        this.shuffle = b.shuffle;
    }

    public int radius() { return radius; }
    public Chain chain() { return chain; }
    public Origin origin() { return origin; }
    /** Which side this step reaches. Read by the client's range indicator to pick a colour. */
    public Side side() { return side; }

    // ─────────────────────────────────────────────────────────────────────
    // Factories - the common shapes, so a definition reads as a sentence
    // ─────────────────────────────────────────────────────────────────────

    /** The single enemy standing on the aimed tile. */
    public static Builder enemy() {
        return new Builder().origin(Origin.TARGET_TILE).side(Side.ENEMY);
    }

    /** Enemies within {@code radius} of the aimed tile, the aimed one included. */
    public static Builder enemiesAround(int radius) {
        return new Builder().origin(Origin.TARGET_TILE).side(Side.ENEMY).radius(radius);
    }

    /** Enemies within {@code radius} of the aimed tile, EXCLUDING the one aimed at. */
    public static Builder enemiesNear(int radius) {
        return new Builder().origin(Origin.TARGET_TILE).side(Side.ENEMY)
            .radius(radius).includeOrigin(false);
    }

    /** Enemies within {@code radius} of the caster. */
    public static Builder enemiesAroundCaster(int radius) {
        return new Builder().origin(Origin.CASTER).side(Side.ENEMY).radius(radius);
    }

    /** Every live pet. The selector for ally-facing spells. */
    public static Builder pets() {
        return new Builder().origin(Origin.EACH_PET).side(Side.ALLY).radius(0);
    }

    /** Enemies within {@code radius} of EACH pet - blasts stack where they overlap. */
    public static Builder enemiesAroundEachPet(int radius) {
        return new Builder().origin(Origin.EACH_PET).side(Side.ENEMY)
            .radius(radius).includeOrigin(false);
    }

    /** The aimed tile itself, with no entity. For placing and teleporting. */
    public static Builder tile() {
        return new Builder().origin(Origin.TARGET_TILE).tileOnly(true);
    }

    /** A step that affects only the caster - no entity targets at all. */
    public static Builder self() {
        return new Builder().origin(Origin.CASTER).tileOnly(true);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Resolution
    // ─────────────────────────────────────────────────────────────────────

    public List<SpellTarget> resolve(SpellContext ctx) {
        List<SpellTarget> out = new ArrayList<>();
        if (tileOnly) {
            out.add(SpellTarget.ofTile(origin == Origin.CASTER ? ctx.casterPos() : ctx.targetTile()));
            return out;
        }

        List<GridPos> origins = new ArrayList<>();
        CombatEntity aimedAt = null;
        switch (origin) {
            case TARGET_TILE -> {
                origins.add(ctx.targetTile());
                aimedAt = ctx.arena().getOccupant(ctx.targetTile());
            }
            case CASTER -> origins.add(ctx.casterPos());
            case EACH_PET -> {
                for (CombatEntity pet : ctx.livePets()) origins.add(pet.getGridPos());
            }
        }

        // Stage 2 - gather. Kept per-origin so overlapping blasts stack (see class doc).
        List<SpellTarget> primaries = new ArrayList<>();
        for (GridPos point : origins) {
            if (origin == Origin.EACH_PET && side == Side.ALLY && radius == 0) {
                // "every pet" as targets in their own right, not as blast centres.
                for (CombatEntity pet : ctx.livePets()) primaries.add(SpellTarget.of(pet, 0));
                break;
            }
            for (CombatEntity e : ctx.combatants()) {
                if (!eligible(e, ctx)) continue;
                if (!includeOrigin && e == aimedAt) continue;
                if (e.minDistanceTo(point) > radius) continue;
                primaries.add(SpellTarget.of(e, 0));
            }
        }
        out.addAll(primaries);

        // Stage 3 - chain outward.
        if (chain != null && !primaries.isEmpty()) {
            out.addAll(spread(ctx, primaries));
        }

        if (shuffle) Collections.shuffle(out);
        if (out.size() > maxTargets) return new ArrayList<>(out.subList(0, maxTargets));
        return out;
    }

    /**
     * Breadth-first hops outward from the already-selected targets.
     *
     * <p>The old chain lightning walked the whole reachable graph with no hop cap, so
     * {@code maxHops} defaults to unlimited in the builder to preserve that. Capping it is what
     * makes "three ricochets" expressible.
     */
    private List<SpellTarget> spread(SpellContext ctx, List<SpellTarget> seeds) {
        Set<CombatEntity> seen = new LinkedHashSet<>();
        Deque<CombatEntity> queue = new ArrayDeque<>();
        Map<CombatEntity, Integer> depth = new HashMap<>();
        for (SpellTarget seed : seeds) {
            if (!seed.hasEntity()) continue;
            seen.add(seed.entity());
            queue.add(seed.entity());
            depth.put(seed.entity(), 0);
        }

        List<SpellTarget> chained = new ArrayList<>();
        while (!queue.isEmpty()) {
            CombatEntity current = queue.poll();
            int d = depth.get(current);
            if (d >= chain.maxHops()) continue;
            for (CombatEntity e : ctx.combatants()) {
                if (seen.contains(e) || !eligible(e, ctx)) continue;
                if (e.minDistanceTo(current.getGridPos()) > chain.hopRange()) continue;
                seen.add(e);
                queue.add(e);
                depth.put(e, d + 1);
                chained.add(SpellTarget.of(e, d + 1));
            }
        }
        return chained;
    }

    /** Side and condition filters, applied identically to primaries and chained targets. */
    private boolean eligible(CombatEntity e, SpellContext ctx) {
        if (!e.isAlive()) return false;
        switch (side) {
            case ENEMY -> { if (e.isAlly()) return false; }
            case ALLY -> { if (!e.isAlly() || e.isSeekerProjectile()) return false; }
            case ANY -> { }
        }
        if (excludeBosses && e.isBoss()) return false;
        if (belowHpFraction < 1.0) {
            if (e.getCurrentHp() > e.getMaxHp() * belowHpFraction) return false;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────
    // Derivation - an inscribed sherd is a copy, never an edit
    // ──────────────────────────────────────────────────────────────────

    /** A mutable copy of this selector, carrying every field forward. */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.origin = origin;
        b.side = side;
        b.radius = radius;
        b.includeOrigin = includeOrigin;
        b.chain = chain;
        b.maxTargets = maxTargets;
        b.belowHpFraction = belowHpFraction;
        b.excludeBosses = excludeBosses;
        b.tileOnly = tileOnly;
        b.shuffle = shuffle;
        return b;
    }

    /**
     * A copy with a wider area of effect.
     *
     * <p>A tile-only selector is returned untouched: widening "the tile you aimed at" is
     * meaningless, and silently turning a hex-trap placement into an area would be a surprise
     * rather than an upgrade.
     */
    public Selector withExtraRadius(int extra) {
        if (tileOnly || extra <= 0) return this;
        return toBuilder().radius(radius + extra).build();
    }

    /**
     * A copy with more chain links, starting a chain if there was none.
     *
     * <p>{@code hopRange} is only consulted when this selector did not already chain - an
     * inscription that adds links to chain lightning should lengthen the existing chain rather
     * than silently re-tuning how far each link reaches.
     */
    public Selector withExtraChain(int extraHops, int hopRange) {
        if (tileOnly || extraHops <= 0) return this;
        if (chain == null) return toBuilder().chain(extraHops, hopRange, 0).build();
        int hops = chain.maxHops() == Integer.MAX_VALUE
            ? Integer.MAX_VALUE : chain.maxHops() + extraHops;
        return toBuilder().chain(hops, chain.hopRange(), chain.decayPerHop()).build();
    }

    public static final class Builder {
        private Origin origin = Origin.TARGET_TILE;
        private Side side = Side.ENEMY;
        private int radius = 0;
        private boolean includeOrigin = true;
        private Chain chain = null;
        private int maxTargets = Integer.MAX_VALUE;
        private double belowHpFraction = 1.0;
        private boolean excludeBosses = false;
        private boolean tileOnly = false;
        private boolean shuffle = false;

        public Builder origin(Origin o) { this.origin = o; return this; }
        public Builder side(Side s) { this.side = s; return this; }
        public Builder radius(int r) { this.radius = r; return this; }
        public Builder includeOrigin(boolean b) { this.includeOrigin = b; return this; }
        public Builder maxTargets(int n) { this.maxTargets = n; return this; }

        /**
         * Shuffle before truncating to {@link #maxTargets}.
         *
         * <p>Blade's cleave picked a random adjacent enemy rather than a positionally-first
         * one. Without this, capping a selector would quietly turn every such pick
         * deterministic - the kind of drift a port is supposed to avoid.
         */
        public Builder random() { this.shuffle = true; return this; }
        private Builder tileOnly(boolean b) { this.tileOnly = b; return this; }

        /** Spread outward: up to {@code hops} links, each reaching {@code range} tiles. */
        public Builder chain(int hops, int range, int decayPerHop) {
            this.chain = new Chain(hops, range, decayPerHop);
            return this;
        }

        /** Spread with no hop limit - the original chain lightning's behaviour. */
        public Builder chainUnlimited(int range, int decayPerHop) {
            this.chain = new Chain(Integer.MAX_VALUE, range, decayPerHop);
            return this;
        }

        /** Only targets at or below this fraction of max HP. The execute gate. */
        public Builder onlyBelowHp(double fraction) { this.belowHpFraction = fraction; return this; }

        /** Skip bosses. Pairs with {@link #onlyBelowHp} so an execute cannot delete a boss. */
        public Builder excludeBosses() { this.excludeBosses = true; return this; }

        public Selector build() { return new Selector(this); }
    }
}
