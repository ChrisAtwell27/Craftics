package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.DamageType;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of armor set combat bonuses, keyed by {@link ArmorSetEntry#armorSetId()}.
 *
 * <p>Craftics registers its built-in sets (leather, chainmail, iron, gold, diamond,
 * netherite, turtle) at startup via {@code VanillaContent}; addons register their own
 * through {@code CrafticsAPI.registerArmorSet}.
 *
 * @since 0.2.0
 */
public final class ArmorSetRegistry {
    private static final Map<String, ArmorSetEntry> REGISTRY = new ConcurrentHashMap<>();
    /** Set IDs whose current entry came from a JSON datapack - dropped on /reload. */
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private ArmorSetRegistry() {}

    /** Register an armor set from code (survives {@code /reload}). */
    public static void register(ArmorSetEntry entry) {
        register(entry, RegistrationSource.CODE);
    }

    /** Register an armor set, tagging whether it came from code or a datapack. */
    public static void register(ArmorSetEntry entry, RegistrationSource source) {
        REGISTRY.put(entry.armorSetId(), entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(entry.armorSetId());
        } else {
            DATAPACK_KEYS.remove(entry.armorSetId());
        }
    }

    /** Remove every armor set entry that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
        }
        DATAPACK_KEYS.clear();
    }

    /** The entry for {@code armorSetId}, or {@code null} if none is registered. */
    public static ArmorSetEntry get(String armorSetId) {
        return REGISTRY.get(armorSetId);
    }

    /** Affinity bonus per 2 pieces for {@code type} on {@code armorSet}, or {@code 0}. */
    public static int getDamageTypeBonus(String armorSet, DamageType type) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.getDamageTypeBonus(type) : 0;
    }

    /** Flat speed bonus for the full 4-piece set, or {@code 0} if not registered. */
    public static int getSpeedBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.speedBonus() : 0;
    }

    /** Flat AP bonus for the full 4-piece set, or {@code 0} if not registered. */
    public static int getApBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.apBonus() : 0;
    }

    /** Flat defense bonus for the full 4-piece set, or {@code 0} if not registered. */
    public static int getDefenseBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.defenseBonus() : 0;
    }

    /** Flat attack bonus for the full 4-piece set, or {@code 0} if not registered. */
    public static int getAttackBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.attackBonus() : 0;
    }

    /** AP cost reduction per attack for the full 4-piece set, or {@code 0} if not registered. */
    public static int getApCostReduction(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.apCostReduction() : 0;
    }

    /** Bonus damage with a light (1-AP) weapon for the full 4-piece set, or {@code 0}. */
    public static int getLightWeaponDamage(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.lightWeaponDamage() : 0;
    }

    /** Extra crit chance (percent) with a light (1-AP) weapon for the full 4-piece set, or {@code 0}. */
    public static int getLightWeaponCrit(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.lightWeaponCrit() : 0;
    }

    /** Tooltip description for the full 4-piece set, or an empty string if not registered. */
    public static String getDescription(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.description() : "";
    }
}
