package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.DamageType;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable definition of one armor set's Craftics combat bonuses.
 *
 * <p>{@code damageTypeBonuses} holds the affinity value per 2 pieces of the matching
 * material. Each single worn piece grants half that much affinity of its type, and a
 * full 4-piece set grants twice the value. Affinity accumulates per piece; every worn
 * piece counts, not just complete sets. The flat stat bonuses ({@code speedBonus},
 * {@code defenseBonus}, etc.) and the {@code description} apply only when all four
 * armor pieces share the same material.
 *
 * <p>Build entries with {@link #builder(String)} and register them through
 * {@code CrafticsAPI.registerArmorSet}:
 *
 * <pre>{@code
 * CrafticsAPI.registerArmorSet(ArmorSetEntry.builder("mymod:obsidian")
 *     .damageBonus(DamageType.SPECIAL, 1)
 *     .defenseBonus(3)
 *     .description("Phantom: +3 AC, 15% chance to negate an incoming hit")
 *     .build());
 * }</pre>
 *
 * @param armorSetId        the armor material key, e.g. {@code "iron"} or {@code "mymod:obsidian"}
 * @param damageTypeBonuses affinity bonus per 2 pieces for each damage type
 * @param speedBonus        flat speed bonus when the full 4-piece set is worn
 * @param apBonus           flat AP bonus when the full 4-piece set is worn
 * @param defenseBonus      flat defense (armor class) bonus when the full 4-piece set is worn
 * @param attackBonus       flat attack bonus when the full 4-piece set is worn
 * @param apCostReduction   AP cost reduction per attack when the full 4-piece set is worn
 * @param description       one-line mechanic text shown on armor tooltips
 * @since 0.2.0
 */
public record ArmorSetEntry(
    String armorSetId,
    Map<DamageType, Integer> damageTypeBonuses,
    int speedBonus,
    int apBonus,
    int defenseBonus,
    int attackBonus,
    int apCostReduction,
    String description
) {
    public static Builder builder(String armorSetId) { return new Builder(armorSetId); }

    public int getDamageTypeBonus(DamageType type) {
        return damageTypeBonuses.getOrDefault(type, 0);
    }

    /** Fluent builder for {@link ArmorSetEntry}. */
    public static class Builder {
        private final String armorSetId;
        private final Map<DamageType, Integer> damageTypeBonuses = new HashMap<>();
        private int speedBonus = 0, apBonus = 0, defenseBonus = 0, attackBonus = 0, apCostReduction = 0;
        private String description = "";

        public Builder(String id) { this.armorSetId = id; }

        /** Affinity bonus per 2 pieces for a specific damage type. */
        public Builder damageBonus(DamageType type, int bonus) { damageTypeBonuses.put(type, bonus); return this; }

        /** Apply the same affinity bonus per 2 pieces to every damage type. */
        public Builder allDamageBonus(int bonus) { for (DamageType t : DamageType.values()) damageTypeBonuses.put(t, bonus); return this; }

        /** Flat speed bonus when the full 4-piece set is worn. Default {@code 0}. */
        public Builder speedBonus(int v) { this.speedBonus = v; return this; }

        /** Flat AP bonus when the full 4-piece set is worn. Default {@code 0}. */
        public Builder apBonus(int v) { this.apBonus = v; return this; }

        /** Flat defense (armor class) bonus when the full 4-piece set is worn. Default {@code 0}. */
        public Builder defenseBonus(int v) { this.defenseBonus = v; return this; }

        /** Flat attack bonus when the full 4-piece set is worn. Default {@code 0}. */
        public Builder attackBonus(int v) { this.attackBonus = v; return this; }

        /** AP cost reduction per attack when the full 4-piece set is worn. Default {@code 0}. */
        public Builder apCostReduction(int v) { this.apCostReduction = v; return this; }

        /** One-line mechanic text shown on armor tooltips. */
        public Builder description(String d) { this.description = d; return this; }
        public ArmorSetEntry build() {
            return new ArmorSetEntry(armorSetId, Map.copyOf(damageTypeBonuses),
                speedBonus, apBonus, defenseBonus, attackBonus, apCostReduction, description);
        }
    }
}
