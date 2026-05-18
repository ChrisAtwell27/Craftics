package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.HybridEffect;
import java.util.Objects;

/**
 * One hybrid armor set — the subclass bonus a player gets from wearing exactly two
 * distinct armor materials.
 *
 * <p>{@code materialA} and {@code materialB} are the two armor-set material keys
 * (e.g. {@code "iron"}, {@code "diamond"}), stored sorted alphabetically by the
 * builder so {@code materialA <= materialB} — the pair is unordered, so
 * {iron, diamond} and {diamond, iron} are the same hybrid. {@code className} is the
 * subclass display name; {@code description} is the one-line mechanic text shown on
 * the armor tooltip; {@code effect} selects the combat mechanic (applied in Phase 2).
 */
public record HybridSetEntry(
    String materialA,
    String materialB,
    String className,
    String description,
    HybridEffect effect
) {
    public static Builder builder(String materialA, String materialB) {
        return new Builder(materialA, materialB);
    }

    public static class Builder {
        private final String materialA;
        private final String materialB;
        private String className = "";
        private String description = "";
        private HybridEffect effect;

        private Builder(String a, String b) {
            // Normalize the pair order alphabetically so {iron,diamond} == {diamond,iron}.
            if (a.compareTo(b) <= 0) { this.materialA = a; this.materialB = b; }
            else                    { this.materialA = b; this.materialB = a; }
        }

        public Builder className(String name)   { this.className = name; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder effect(HybridEffect e)   { this.effect = e; return this; }

        public HybridSetEntry build() {
            Objects.requireNonNull(effect, "effect must be set before build()");
            return new HybridSetEntry(materialA, materialB, className, description, effect);
        }
    }
}
