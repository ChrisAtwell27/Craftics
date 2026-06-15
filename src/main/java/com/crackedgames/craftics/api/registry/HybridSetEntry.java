package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.HybridEffect;
import java.util.Objects;

/**
 * One hybrid armor set - the subclass bonus a player gets from wearing exactly two
 * distinct armor materials.
 *
 * <p>{@code materialA} and {@code materialB} are the two armor-set material keys
 * (e.g. {@code "iron"}, {@code "diamond"}), stored sorted alphabetically by the
 * builder so {@code materialA <= materialB}. The pair is unordered: {iron, diamond}
 * and {diamond, iron} resolve to the same hybrid entry. {@code className} is the
 * subclass display name; {@code description} is the one-line mechanic text shown on
 * the armor tooltip; {@code effect} selects the combat mechanic.
 *
 * <p>Build entries with {@link #builder(String, String)} and register them through
 * {@code CrafticsAPI.registerHybridSet}.
 *
 * @param materialA   the first material key, alphabetically earlier of the two
 * @param materialB   the second material key, alphabetically later of the two
 * @param className   subclass display name shown to players
 * @param description one-line mechanic description shown on armor tooltips
 * @param effect      the combat mechanic this hybrid activates
 * @since 0.2.0
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

    /** Fluent builder for {@link HybridSetEntry}. */
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

        /** Subclass display name shown to players. Default empty string. */
        public Builder className(String name)   { this.className = name; return this; }

        /** One-line mechanic description shown on armor tooltips. Default empty string. */
        public Builder description(String desc) { this.description = desc; return this; }

        /** The combat mechanic this hybrid activates. Required. */
        public Builder effect(HybridEffect e)   { this.effect = e; return this; }

        public HybridSetEntry build() {
            Objects.requireNonNull(effect, "effect must be set before build()");
            return new HybridSetEntry(materialA, materialB, className, description, effect);
        }
    }
}
