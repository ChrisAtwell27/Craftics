package com.crackedgames.craftics.combat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

// Each weapon maps to a damage type; armor sets, trims, and effects can specialize for bonus damage
public enum DamageType {
    SLASHING("Slashing", "\u00a7c"),
    CLEAVING("Cleaving", "\u00a76"),
    BLUNT("Blunt", "\u00a78"),
    WATER("Water", "\u00a73"),
    SPECIAL("Special", "\u00a7d"),
    PET("Pet", "\u00a7a"),
    RANGED("Ranged", "\u00a7b"),
    PHYSICAL("Physical", "\u00a77");

    public final String displayName;
    public final String color;

    DamageType(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public static DamageType fromWeapon(Item weapon) {
        return com.crackedgames.craftics.api.registry.WeaponRegistry.getDamageType(weapon);
    }

    public static boolean isCoral(Item weapon) {
        return weapon == Items.TUBE_CORAL || weapon == Items.BRAIN_CORAL
            || weapon == Items.BUBBLE_CORAL || weapon == Items.FIRE_CORAL
            || weapon == Items.HORN_CORAL
            || weapon == Items.DEAD_TUBE_CORAL || weapon == Items.DEAD_BRAIN_CORAL
            || weapon == Items.DEAD_BUBBLE_CORAL || weapon == Items.DEAD_FIRE_CORAL
            || weapon == Items.DEAD_HORN_CORAL
            || weapon == Items.TUBE_CORAL_FAN || weapon == Items.BRAIN_CORAL_FAN
            || weapon == Items.BUBBLE_CORAL_FAN || weapon == Items.FIRE_CORAL_FAN
            || weapon == Items.HORN_CORAL_FAN
            || weapon == Items.DEAD_TUBE_CORAL_FAN || weapon == Items.DEAD_BRAIN_CORAL_FAN
            || weapon == Items.DEAD_BUBBLE_CORAL_FAN || weapon == Items.DEAD_FIRE_CORAL_FAN
            || weapon == Items.DEAD_HORN_CORAL_FAN;
    }

    // Each armor set specializes in a damage type (netherite = +1 all)
    public static int getArmorSetBonus(String armorSet, DamageType type) {
        return com.crackedgames.craftics.api.registry.ArmorSetRegistry.getDamageTypeBonus(armorSet, type);
    }

    public static int getTrimBonus(TrimEffects.TrimScan scan, DamageType type) {
        if (scan == null) return 0;
        int bonus = switch (type) {
            case SLASHING -> scan.get(TrimEffects.Bonus.SWORD_POWER);
            case CLEAVING -> scan.get(TrimEffects.Bonus.CLEAVING_POWER);
            case BLUNT    -> scan.get(TrimEffects.Bonus.BLUNT_POWER);
            case WATER    -> scan.get(TrimEffects.Bonus.WATER_POWER);
            case SPECIAL  -> scan.get(TrimEffects.Bonus.SPECIAL_POWER);
            case PET      -> scan.get(TrimEffects.Bonus.ALLY_DAMAGE);
            case RANGED   -> scan.get(TrimEffects.Bonus.RANGED_POWER);
            default       -> 0;
        };
        // Generic melee power stacks on top of type-specific
        if (type == SLASHING || type == CLEAVING || type == BLUNT || type == PHYSICAL) {
            bonus += scan.get(TrimEffects.Bonus.MELEE_POWER);
        }
        return bonus;
    }

    // Water Breathing -> +2 Water, Fire Resistance -> +1 Magic
    public static int getEffectBonus(CombatEffects effects, DamageType type) {
        if (effects == null) return 0;
        int bonus = 0;
        if (type == WATER && effects.hasEffect(CombatEffects.EffectType.WATER_BREATHING)) {
            bonus += 2;
        }
        if (type == SPECIAL && effects.hasEffect(CombatEffects.EffectType.FIRE_RESISTANCE)) {
            bonus += 1;
        }
        return bonus;
    }

    /**
     * Per-point damage multiplier for every affinity source. Single source of truth:
     * 1 affinity "point" (from armor set, trim, mob head, effect, or level-up) always
     * contributes {@value} damage of its type. Change this constant once and every
     * source scales together — no hidden per-source math.
     */
    public static final int DAMAGE_PER_AFFINITY_POINT = 3;

    /**
     * Total affinity points from gear sources: armor set + trims + potion effects.
     * Returned as raw points so callers can display "+N Power" consistently with
     * the damage formula ({@link #DAMAGE_PER_AFFINITY_POINT} per point).
     */
    public static int getTotalAffinityPoints(String armorSet, TrimEffects.TrimScan trimScan,
                                              CombatEffects effects, DamageType type) {
        return getArmorSetBonus(armorSet, type)
             + getTrimBonus(trimScan, type)
             + getEffectBonus(effects, type);
    }

    /**
     * Total damage bonus from gear sources. Each point of affinity contributes
     * {@link #DAMAGE_PER_AFFINITY_POINT} damage.
     */
    public static int getTotalBonus(String armorSet, TrimEffects.TrimScan trimScan,
                                     CombatEffects effects, DamageType type) {
        return getTotalAffinityPoints(armorSet, trimScan, effects, type) * DAMAGE_PER_AFFINITY_POINT;
    }

    /** Version that also folds in the player's level-up affinity points. */
    public static int getTotalBonus(String armorSet, TrimEffects.TrimScan trimScan,
                                     CombatEffects effects, DamageType type,
                                     PlayerProgression.PlayerStats playerStats) {
        int points = getTotalAffinityPoints(armorSet, trimScan, effects, type);
        if (playerStats != null) {
            PlayerProgression.Affinity affinity = mapToAffinity(type);
            if (affinity != null) {
                points += playerStats.getAffinityPoints(affinity);
            }
        }
        return points * DAMAGE_PER_AFFINITY_POINT;
    }

    /**
     * Mob skull helmets grant 1 affinity point of a specific type when worn.
     * Skeleton → Ranged, Creeper → Blunt, Piglin → Slashing,
     * Wither Skeleton → Special, Zombie → Physical.
     */
    public static int getMobHeadAffinityPoints(ItemStack helmet, DamageType type) {
        if (helmet == null || helmet.isEmpty()) return 0;
        Item item = helmet.getItem();
        if (item == Items.SKELETON_SKULL && type == RANGED) return 1;
        if (item == Items.CREEPER_HEAD && type == BLUNT) return 1;
        if (item == Items.PIGLIN_HEAD && type == SLASHING) return 1;
        if (item == Items.WITHER_SKELETON_SKULL && type == SPECIAL) return 1;
        if (item == Items.ZOMBIE_HEAD && type == PHYSICAL) return 1;
        return 0;
    }

    /**
     * Damage variant of {@link #getMobHeadAffinityPoints} — returns the actual
     * damage contribution (points × {@link #DAMAGE_PER_AFFINITY_POINT}). Used by
     * combat code that adds mob-head bonus alongside {@link #getTotalBonus}.
     */
    public static int getMobHeadBonus(ItemStack helmet, DamageType type) {
        return getMobHeadAffinityPoints(helmet, type) * DAMAGE_PER_AFFINITY_POINT;
    }

    /** Map DamageType to the corresponding Affinity for level-up bonuses. */
    private static PlayerProgression.Affinity mapToAffinity(DamageType type) {
        return switch (type) {
            case SLASHING -> PlayerProgression.Affinity.SLASHING;
            case CLEAVING -> PlayerProgression.Affinity.CLEAVING;
            case BLUNT -> PlayerProgression.Affinity.BLUNT;
            case RANGED -> PlayerProgression.Affinity.RANGED;
            case WATER -> PlayerProgression.Affinity.WATER;
            case SPECIAL -> PlayerProgression.Affinity.SPECIAL;
            case PHYSICAL -> PlayerProgression.Affinity.PHYSICAL;
            case PET -> PlayerProgression.Affinity.PET;
        };
    }
}
