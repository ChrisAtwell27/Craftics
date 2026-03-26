package com.crackedgames.craftics.combat;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Custom damage type system for class-based combat.
 * Each weapon belongs to a damage type. Armor sets, trims, and effects
 * can specialize in specific types for bonus damage.
 */
public enum DamageType {
    SWORD("Sword", "\u00a7c"),
    CLEAVING("Cleaving", "\u00a76"),
    BLUNT("Blunt", "\u00a78"),
    WATER("Water", "\u00a73"),
    MAGIC("Magic", "\u00a7d"),
    PET("Pet", "\u00a7a"),
    RANGED("Ranged", "\u00a7b"),
    PHYSICAL("Physical", "\u00a77");

    public final String displayName;
    public final String color;

    DamageType(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    /** Determine the damage type of a weapon. */
    public static DamageType fromWeapon(Item weapon) {
        // Swords
        if (weapon == Items.WOODEN_SWORD || weapon == Items.STONE_SWORD
            || weapon == Items.IRON_SWORD || weapon == Items.GOLDEN_SWORD
            || weapon == Items.DIAMOND_SWORD || weapon == Items.NETHERITE_SWORD) {
            return SWORD;
        }
        // Axes
        if (weapon == Items.WOODEN_AXE || weapon == Items.STONE_AXE
            || weapon == Items.IRON_AXE || weapon == Items.GOLDEN_AXE
            || weapon == Items.DIAMOND_AXE || weapon == Items.NETHERITE_AXE) {
            return CLEAVING;
        }
        if (weapon == Items.MACE || weapon == Items.STICK) return BLUNT;
        if (weapon == Items.TRIDENT) return WATER;
        if (weapon == Items.BOW || weapon == Items.CROSSBOW) return RANGED;
        return PHYSICAL;
    }

    /**
     * Get bonus damage from the player's armor set affinity for this damage type.
     * Each armor set specializes in a particular damage type.
     *
     * Leather (Brawler)     → Physical +2
     * Chainmail (Rogue)     → Sword +2
     * Iron (Guard)          → Cleaving +2
     * Gold (Gambler)        → Magic +2
     * Diamond (Knight)      → Blunt +2
     * Netherite (Juggernaut)→ +1 to ALL types
     * Turtle (Aquatic)      → Water +3
     */
    public static int getArmorSetBonus(String armorSet, DamageType type) {
        return switch (armorSet) {
            case "leather"   -> type == PHYSICAL ? 2 : 0;
            case "chainmail" -> type == SWORD ? 2 : 0;
            case "iron"      -> type == CLEAVING ? 2 : 0;
            case "gold"      -> type == MAGIC ? 2 : 0;
            case "diamond"   -> type == BLUNT ? 2 : 0;
            case "netherite" -> 1;
            case "turtle"    -> type == WATER ? 3 : 0;
            default -> 0;
        };
    }

    /**
     * Get bonus damage from trim effects for this damage type.
     * Type-specific trim bonuses (SWORD_POWER, CLEAVING_POWER, etc.)
     * plus generic MELEE_POWER that applies to all melee types.
     */
    public static int getTrimBonus(TrimEffects.TrimScan scan, DamageType type) {
        if (scan == null) return 0;
        int bonus = switch (type) {
            case SWORD    -> scan.get(TrimEffects.Bonus.SWORD_POWER);
            case CLEAVING -> scan.get(TrimEffects.Bonus.CLEAVING_POWER);
            case BLUNT    -> scan.get(TrimEffects.Bonus.BLUNT_POWER);
            case WATER    -> scan.get(TrimEffects.Bonus.WATER_POWER);
            case MAGIC    -> scan.get(TrimEffects.Bonus.MAGIC_POWER);
            case PET      -> scan.get(TrimEffects.Bonus.ALLY_DAMAGE);
            case RANGED   -> scan.get(TrimEffects.Bonus.RANGED_POWER);
            default       -> 0;
        };
        // Generic melee power stacks with specific melee types
        if (type == SWORD || type == CLEAVING || type == BLUNT) {
            bonus += scan.get(TrimEffects.Bonus.MELEE_POWER);
        }
        return bonus;
    }

    /**
     * Get bonus damage from active combat effects for this damage type.
     * Water Breathing → +2 Water damage
     * Fire Resistance → +1 Magic damage
     */
    public static int getEffectBonus(CombatEffects effects, DamageType type) {
        if (effects == null) return 0;
        int bonus = 0;
        if (type == WATER && effects.hasEffect(CombatEffects.EffectType.WATER_BREATHING)) {
            bonus += 2;
        }
        if (type == MAGIC && effects.hasEffect(CombatEffects.EffectType.FIRE_RESISTANCE)) {
            bonus += 1;
        }
        return bonus;
    }

    /**
     * Calculate total damage type bonus from all sources (armor set + trims + effects).
     */
    public static int getTotalBonus(String armorSet, TrimEffects.TrimScan trimScan,
                                     CombatEffects effects, DamageType type) {
        return getArmorSetBonus(armorSet, type)
             + getTrimBonus(trimScan, type)
             + getEffectBonus(effects, type);
    }
}
