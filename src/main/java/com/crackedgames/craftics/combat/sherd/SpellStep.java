package com.crackedgames.craftics.combat.sherd;

import java.util.ArrayList;
import java.util.List;

/**
 * One "and then it also does..." of a spell: a set of targets, and what happens to each.
 *
 * <p>This is what makes three completely different functionalities in one sherd a matter of
 * writing three steps. The primary hit is a step, the splash is a step, the self-buff you get
 * for landing it is a step - and because each carries its own selector, a single sherd can hit
 * an enemy, heal every pet, and buff the caster in one cast without any of those three knowing
 * the others exist.
 *
 * <p>Steps run in order and share one {@link SpellReport}, which is how {@code lifesteal} in a
 * later step can heal for damage an earlier step dealt.
 */
public final class SpellStep {

    private final Selector selector;
    private final List<SpellEffect> effects;
    private final SpellVisuals visuals;
    private final String heading;

    private SpellStep(Selector selector, List<SpellEffect> effects, SpellVisuals visuals, String heading) {
        this.selector = selector;
        this.effects = List.copyOf(effects);
        this.visuals = visuals;
        this.heading = heading;
    }

    public Selector selector() { return selector; }
    public List<SpellEffect> effects() { return effects; }
    public SpellVisuals visuals() { return visuals; }
    /** Optional clause introducing this step's fragments, e.g. {@code "Cleave!"}. */
    public String heading() { return heading; }

    public static Builder of(Selector.Builder selector) { return new Builder(selector.build()); }

    public static final class Builder {
        private final Selector selector;
        private final List<SpellEffect> effects = new ArrayList<>();
        private SpellVisuals visuals = SpellVisuals.none();
        private String heading = "";

        private Builder(Selector selector) { this.selector = selector; }

        public Builder effect(SpellEffect e) { effects.add(e); return this; }
        public Builder effect(Effects.DamageBuilder d) { effects.add(d.build()); return this; }
        public Builder visuals(SpellVisuals v) { this.visuals = v; return this; }
        public Builder visuals(SpellVisuals.Builder v) { this.visuals = v.build(); return this; }
        public Builder heading(String h) { this.heading = h; return this; }

        public SpellStep build() { return new SpellStep(selector, effects, visuals, heading); }
    }
}
