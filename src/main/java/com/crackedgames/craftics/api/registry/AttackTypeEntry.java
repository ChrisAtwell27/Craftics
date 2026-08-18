package com.crackedgames.craftics.api.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An <b>attack type</b>: a trait of an attack that decides how well it lands against a
 * defender, and nothing else.
 *
 * <h2>How this differs from DamageType and Affinity</h2>
 *
 * <p>Craftics already has two related ideas, and this is deliberately a third:
 *
 * <ul>
 *   <li>{@code Affinity} is what a PLAYER levels up. Points in it make their attacks
 *       hit harder.</li>
 *   <li>{@code DamageType} is what an attack costs and scales from - it decides which
 *       affinity a weapon benefits from.</li>
 *   <li><b>An attack type is a trait of the attack itself.</b> Nobody levels it. It exists
 *       only to be compared against what the defender is, producing a multiplier.</li>
 * </ul>
 *
 * <p>The three are orthogonal. A move can cost SPECIAL damage (so it scales with the
 * Special affinity) while being typed {@code fire} (so it lands hard on a grass defender
 * and poorly on a water one). Changing the typing does not change what the player levels
 * to improve it.
 *
 * <h2>The chart, not per-mob tables</h2>
 *
 * <p>Effectiveness is authored once per attacking type as a chart of "what am I good and
 * bad against", and defenders simply declare which types they ARE. That is the only shape
 * that scales: a roster of a thousand creatures across eighteen types needs eighteen chart
 * entries and one line per creature, where a per-creature resistance table would need
 * eighteen thousand.
 *
 * <p>A defender with more than one type multiplies its way through them, so being strong
 * against one of a defender's types and weak against the other cancels out - the standard
 * dual-type rule.
 *
 * <pre>{@code
 * CrafticsAPI.registerAttackType(AttackTypeEntry.builder("mymod:fire")
 *     .displayName("Fire").colorCode("§c")
 *     .superEffectiveAgainst("mymod:grass", "mymod:ice", "mymod:bug")
 *     .notVeryEffectiveAgainst("mymod:water", "mymod:rock", "mymod:fire")
 *     .noEffectAgainst("mymod:stone_idol")
 *     .build());
 *
 * // A creature that IS fire and flying.
 * CrafticsAPI.setDefendingTypes("mymod:charizard", "mymod:fire", "mymod:flying");
 * }</pre>
 *
 * @param id            unique type id, e.g. {@code "mymod:fire"}
 * @param displayName   name shown to players, e.g. {@code "Fire"}
 * @param colorCode     Minecraft color code used wherever the type is named
 * @param multipliers   defending type id to damage multiplier. Anything absent is
 *                      {@code 1.0}; {@code 0.0} means no effect at all
 * @since 0.3.9
 */
public record AttackTypeEntry(
    String id,
    String displayName,
    String colorCode,
    Map<String, Double> multipliers
) {
    /** Multiplier applied when an attacking type is strong against a defending type. */
    public static final double SUPER_EFFECTIVE = 2.0;
    /** Multiplier applied when an attacking type is weak against a defending type. */
    public static final double NOT_VERY_EFFECTIVE = 0.5;
    /** Multiplier applied when an attacking type cannot touch a defending type at all. */
    public static final double NO_EFFECT = 0.0;

    public AttackTypeEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("AttackTypeEntry requires a non-blank id");
        }
        if (displayName == null || displayName.isBlank()) displayName = id;
        if (colorCode == null) colorCode = "§f";
        multipliers = multipliers == null ? Map.of()
            : Collections.unmodifiableMap(new HashMap<>(multipliers));
    }

    /**
     * How hard this type hits a single defending type. {@code 1.0} when the chart says
     * nothing about it, which is the common case and why charts stay small: only the
     * interesting matchups are written down.
     */
    public double multiplierAgainst(String defendingTypeId) {
        if (defendingTypeId == null) return 1.0;
        Double m = multipliers.get(defendingTypeId);
        return m == null ? 1.0 : m;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder for {@link AttackTypeEntry}. */
    public static final class Builder {
        private final String id;
        private String displayName;
        private String colorCode = "§f";
        private final Map<String, Double> multipliers = new LinkedHashMap<>();

        public Builder(String id) {
            this.id = id;
        }

        /** Name shown to players. Defaults to the id. */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Minecraft color code used wherever the type is named. Defaults to white. */
        public Builder colorCode(String colorCode) {
            this.colorCode = colorCode;
            return this;
        }

        /** Double damage against each of these defending types. */
        public Builder superEffectiveAgainst(String... defendingTypeIds) {
            return against(SUPER_EFFECTIVE, defendingTypeIds);
        }

        /** Half damage against each of these defending types. */
        public Builder notVeryEffectiveAgainst(String... defendingTypeIds) {
            return against(NOT_VERY_EFFECTIVE, defendingTypeIds);
        }

        /** No damage at all against each of these defending types. */
        public Builder noEffectAgainst(String... defendingTypeIds) {
            return against(NO_EFFECT, defendingTypeIds);
        }

        /**
         * An arbitrary multiplier against these defending types, for charts that want
         * something other than the usual 2x / 0.5x / 0x.
         */
        public Builder against(double multiplier, String... defendingTypeIds) {
            if (defendingTypeIds == null) return this;
            for (String t : defendingTypeIds) {
                if (t != null && !t.isBlank()) multipliers.put(t, multiplier);
            }
            return this;
        }

        public AttackTypeEntry build() {
            return new AttackTypeEntry(id, displayName, colorCode, multipliers);
        }
    }
}
