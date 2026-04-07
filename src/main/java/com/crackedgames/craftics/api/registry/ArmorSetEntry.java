package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.DamageType;
import java.util.HashMap;
import java.util.Map;

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

    public static class Builder {
        private final String armorSetId;
        private final Map<DamageType, Integer> damageTypeBonuses = new HashMap<>();
        private int speedBonus = 0, apBonus = 0, defenseBonus = 0, attackBonus = 0, apCostReduction = 0;
        private String description = "";

        public Builder(String id) { this.armorSetId = id; }
        public Builder damageBonus(DamageType type, int bonus) { damageTypeBonuses.put(type, bonus); return this; }
        public Builder allDamageBonus(int bonus) { for (DamageType t : DamageType.values()) damageTypeBonuses.put(t, bonus); return this; }
        public Builder speedBonus(int v) { this.speedBonus = v; return this; }
        public Builder apBonus(int v) { this.apBonus = v; return this; }
        public Builder defenseBonus(int v) { this.defenseBonus = v; return this; }
        public Builder attackBonus(int v) { this.attackBonus = v; return this; }
        public Builder apCostReduction(int v) { this.apCostReduction = v; return this; }
        public Builder description(String d) { this.description = d; return this; }
        public ArmorSetEntry build() {
            return new ArmorSetEntry(armorSetId, Map.copyOf(damageTypeBonuses),
                speedBonus, apBonus, defenseBonus, attackBonus, apCostReduction, description);
        }
    }
}
