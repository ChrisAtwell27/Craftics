package com.crackedgames.craftics.combat.sherd;

import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete sherd spell, as data.
 *
 * <p>Everything the old code spread across five places - membership in {@code POTTERY_SHERDS},
 * an entry in the AP-cost if-chain, an entry in the range if-chain, a line in the tooltip
 * if-chain, and a method in the dispatch chain - is one object. Those five lists had already
 * drifted apart at least twice (the file's own comments record the cost list and the membership
 * list disagreeing), which is the failure mode a single definition makes impossible: a sherd
 * that exists has a cost, because they are the same object.
 *
 * <p>Spells are immutable. The Scribe does not edit one; it derives a new one via
 * {@link #toBuilder()}, which is what lets an inscribed sherd differ from every other sherd of
 * the same item without either of them knowing about the other.
 */
public final class SherdSpell {

    /** What the player must aim at for the cast to be legal. */
    public enum TargetMode {
        /** No target tile at all. */
        SELF,
        /** A tile with a living enemy on it. */
        ENEMY,
        /** A walkable, unoccupied tile - the hex trap. */
        EMPTY_TILE,
        /** A walkable tile the caster can stand on - the blink. */
        WALKABLE_TILE
    }

    private final Item item;
    private final String name;
    private final String color;
    private final int apCost;
    private final int range;
    private final TargetMode targetMode;
    private final int breakPercent;
    private final List<SpellStep> steps;
    private final SpellVisuals castVisuals;
    private final List<String> tooltipLines;
    private final String emptyMessage;

    private SherdSpell(Builder b) {
        this.item = b.item;
        this.name = b.name;
        this.color = b.color;
        this.apCost = b.apCost;
        this.range = b.range;
        this.targetMode = b.targetMode;
        this.breakPercent = b.breakPercent;
        this.steps = List.copyOf(b.steps);
        this.castVisuals = b.castVisuals;
        this.tooltipLines = List.copyOf(b.tooltipLines);
        this.emptyMessage = b.emptyMessage;
    }

    public Item item() { return item; }
    public String name() { return name; }
    public String color() { return color; }
    public int apCost() { return apCost; }
    public int range() { return range; }
    public TargetMode targetMode() { return targetMode; }
    /** Percent chance to shatter per cast. 0 means the sherd is unbreakable. */
    public int breakPercent() { return breakPercent; }
    public List<SpellStep> steps() { return steps; }
    public SpellVisuals castVisuals() { return castVisuals; }
    public List<String> tooltipLines() { return tooltipLines; }
    /** What to say when the cast resolved but affected nothing. */
    public String emptyMessage() { return emptyMessage; }

    public boolean isSelfCast() { return targetMode == TargetMode.SELF; }

    /** The bold prefix every cast message opens with, e.g. {@code "§e§lChain Lightning!"}. */
    public String banner() { return color + "§l" + name + "!"; }

    public static Builder of(Item item, String name) { return new Builder(item, name); }

    /** A mutable copy, for deriving an inscribed variant. See {@link SherdInscription}. */
    public Builder toBuilder() {
        Builder b = new Builder(item, name);
        b.color = color;
        b.apCost = apCost;
        b.range = range;
        b.targetMode = targetMode;
        b.breakPercent = breakPercent;
        b.steps.addAll(steps);
        b.castVisuals = castVisuals;
        b.tooltipLines.addAll(tooltipLines);
        b.emptyMessage = emptyMessage;
        return b;
    }

    public static final class Builder {
        private final Item item;
        private final String name;
        private String color = "§d";
        private int apCost = 3;
        private int range = 3;
        private TargetMode targetMode = TargetMode.ENEMY;
        private int breakPercent = 10;
        private final List<SpellStep> steps = new ArrayList<>();
        private SpellVisuals castVisuals = SpellVisuals.none();
        private final List<String> tooltipLines = new ArrayList<>();
        private String emptyMessage = "§7Nothing in range.";

        private Builder(Item item, String name) {
            this.item = item;
            this.name = name;
        }

        public Builder color(String c) { this.color = c; return this; }
        public Builder ap(int cost) { this.apCost = cost; return this; }
        public Builder range(int r) { this.range = r; return this; }
        public Builder targets(TargetMode mode) { this.targetMode = mode; return this; }
        /** Self-cast: no target tile, range 0. */
        public Builder selfCast() { this.targetMode = TargetMode.SELF; this.range = 0; return this; }
        public Builder breakPercent(int percent) { this.breakPercent = percent; return this; }
        public Builder unbreakable() { this.breakPercent = 0; return this; }
        public Builder castVisuals(SpellVisuals v) { this.castVisuals = v; return this; }
        public Builder castVisuals(SpellVisuals.Builder v) { this.castVisuals = v.build(); return this; }
        public Builder step(SpellStep s) { this.steps.add(s); return this; }
        public Builder step(SpellStep.Builder s) { this.steps.add(s.build()); return this; }
        public Builder tooltip(String line) { this.tooltipLines.add(line); return this; }
        public Builder emptyMessage(String m) { this.emptyMessage = m; return this; }

        // ── Transformations, used by inscriptions ────────────────────────

        /** Shift the AP cost, never below 1. */
        public Builder adjustAp(int delta) { this.apCost = Math.max(1, apCost + delta); return this; }

        /** Shift the targeting range. A self-cast stays self-cast. */
        public Builder adjustRange(int delta) {
            if (targetMode != TargetMode.SELF) this.range = Math.max(1, range + delta);
            return this;
        }

        /** Attach an effect to every existing step - "make it also do X". */
        public Builder augmentAllSteps(SpellEffect extra) {
            for (int i = 0; i < steps.size(); i++) {
                SpellStep old = steps.get(i);
                SpellStep.Builder rebuilt = SpellStep.of(old.selector().toBuilder())
                    .visuals(old.visuals()).heading(old.heading());
                for (SpellEffect e : old.effects()) rebuilt.effect(e);
                rebuilt.effect(extra);
                steps.set(i, rebuilt.build());
            }
            return this;
        }

        /** Widen every step's area of effect. */
        public Builder augmentRadius(int extra) {
            return rebuildSelectors(s -> s.withExtraRadius(extra));
        }

        /** Give every step more chain links, starting one if it had none. */
        public Builder augmentChain(int extraHops, int hopRange) {
            return rebuildSelectors(s -> s.withExtraChain(extraHops, hopRange));
        }

        private Builder rebuildSelectors(java.util.function.UnaryOperator<Selector> op) {
            for (int i = 0; i < steps.size(); i++) {
                SpellStep old = steps.get(i);
                SpellStep.Builder rebuilt = SpellStep.of(op.apply(old.selector()).toBuilder())
                    .visuals(old.visuals()).heading(old.heading());
                for (SpellEffect e : old.effects()) rebuilt.effect(e);
                steps.set(i, rebuilt.build());
            }
            return this;
        }

        public SherdSpell build() { return new SherdSpell(this); }
    }
}
