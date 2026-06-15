package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.registry.ArmorSetRegistry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.HashMap;
import java.util.Map;

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

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /**
     * Per-piece armor affinity for a damage type, in <b>half-points</b>. Every worn
     * armor piece of a material contributes that material's per-piece affinity, so a
     * single piece counts - affinity is no longer full-set-only. Returned in
     * half-points: each piece is worth 0.5 affinity, so a full 4-piece set totals 4
     * half-points (= 2 whole affinity points). The half-point unit keeps the 0.5
     * per-piece granularity exact without floating-point math.
     */
    public static int getArmorAffinityHalfPoints(ServerPlayerEntity player, DamageType type) {
        Map<String, Integer> counts = new HashMap<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            String material = ArmorClassTable.armorSetKeyOf(player.getEquippedStack(slot).getItem());
            if (material != null) counts.merge(material, 1, Integer::sum);
        }
        return affinityFromCounts(counts, type);
    }

    /**
     * Pure per-piece affinity math, in half-points: {@code Σ count × ArmorSetEntry value}.
     * The registered {@code ArmorSetEntry} value is the affinity per 2 pieces, which
     * equals the half-point value of one piece - so multiplying by the worn count gives
     * that material's affinity total in half-points.
     */
    public static int affinityFromCounts(Map<String, Integer> materialCounts, DamageType type) {
        int halfPoints = 0;
        for (Map.Entry<String, Integer> e : materialCounts.entrySet()) {
            halfPoints += e.getValue() * ArmorSetRegistry.getDamageTypeBonus(e.getKey(), type);
        }
        return halfPoints;
    }

    /**
     * Formats an affinity value given in half-points as a human-readable number:
     * {@code 0 → "0"}, {@code 1 → "0.5"}, {@code 2 → "1"}, {@code 3 → "1.5"},
     * {@code 4 → "2"}. Used by the armor tooltip and the affinity panel.
     */
    public static String formatAffinityHalfPoints(int halfPoints) {
        return (halfPoints % 2 == 0)
            ? String.valueOf(halfPoints / 2)
            : (halfPoints / 2) + ".5";
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
     * source scales together - no hidden per-source math.
     */
    public static final int DAMAGE_PER_AFFINITY_POINT = 3;

    /**
     * Total damage bonus from gear sources: per-piece armor affinity + trims + potion
     * effects. Armor affinity is per-piece (half-point granularity) and is converted
     * to damage at half resolution so a single piece's 0.5 affinity is not floored
     * away; trims and effects are whole affinity points. Each whole point contributes
     * {@link #DAMAGE_PER_AFFINITY_POINT} damage.
     */
    public static int getTotalBonus(ServerPlayerEntity player, TrimEffects.TrimScan trimScan,
                                     CombatEffects effects, DamageType type) {
        return getTotalBonus(player, trimScan, effects, type, null);
    }

    /** Version that also folds in the player's level-up affinity points. */
    public static int getTotalBonus(ServerPlayerEntity player, TrimEffects.TrimScan trimScan,
                                     CombatEffects effects, DamageType type,
                                     PlayerProgression.PlayerStats playerStats) {
        // Armor affinity is per-piece in half-points: multiply by the damage rate then
        // halve, so a lone piece (1 half-point) still yields floor(3/2) = 1 damage.
        int armorDamage = getArmorAffinityHalfPoints(player, type) * DAMAGE_PER_AFFINITY_POINT / 2;
        // Trims, potion effects, and level-up choices are whole affinity points.
        int wholePoints = getTrimBonus(trimScan, type) + getEffectBonus(effects, type);
        if (playerStats != null) {
            PlayerProgression.Affinity affinity = mapToAffinity(type);
            if (affinity != null) {
                wholePoints += playerStats.getAffinityPoints(affinity);
            }
        }
        return armorDamage + wholePoints * DAMAGE_PER_AFFINITY_POINT;
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
     * Damage variant of {@link #getMobHeadAffinityPoints} - returns the actual
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
