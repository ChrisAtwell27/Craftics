package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.DamageType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmorSetRegistry {
    private static final Map<String, ArmorSetEntry> REGISTRY = new ConcurrentHashMap<>();

    private ArmorSetRegistry() {}

    public static void register(ArmorSetEntry entry) {
        REGISTRY.put(entry.armorSetId(), entry);
    }

    public static ArmorSetEntry get(String armorSetId) {
        return REGISTRY.get(armorSetId);
    }

    public static int getDamageTypeBonus(String armorSet, DamageType type) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.getDamageTypeBonus(type) : 0;
    }

    public static int getSpeedBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.speedBonus() : 0;
    }

    public static int getApBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.apBonus() : 0;
    }

    public static int getDefenseBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.defenseBonus() : 0;
    }

    public static int getAttackBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.attackBonus() : 0;
    }

    public static int getApCostReduction(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.apCostReduction() : 0;
    }

    public static String getDescription(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.description() : "";
    }
}
