package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.StatModifiers;
import com.crackedgames.craftics.api.registry.EquipmentScannerRegistry;
import com.crackedgames.craftics.api.registry.TrimMaterialEntry;
import com.crackedgames.craftics.api.registry.TrimMaterialRegistry;
import com.crackedgames.craftics.api.registry.TrimPatternRegistry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
//? if <=1.21.1 {
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.item.trim.ArmorTrimPatterns;
//?} else {
/*import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.item.equipment.trim.ArmorTrimPatterns;
*///?}
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

// Per-piece stackable trim bonuses + full-set bonus when all 4 pieces match
// Trim templates drop from dimension-appropriate bosses
public class TrimEffects {

    public enum Bonus {
        RANGED_POWER, MELEE_POWER, SPEED, AP, DEFENSE, LUCK,
        ATTACK_RANGE, MAX_HP, ARMOR_PEN, REGEN, ALLY_DAMAGE, STEALTH_RANGE,
        // Damage-type-specific power bonuses
        SWORD_POWER, CLEAVING_POWER, BLUNT_POWER, WATER_POWER, SPECIAL_POWER
    }

    public enum SetBonus {
        NONE,
        OVERWATCH,      // Sentry: counter-attack ranged
        SANDSTORM,      // Dune: enemy speed aura
        TIDAL,          // Coast: water heals
        FERAL,          // Wild: first attack free
        FORTRESS,       // Ward: halve damage when stationary
        ALL_SEEING,     // Eye: ranged crit bonus
        ETHEREAL,       // Vex: dodge chance
        OCEAN_BLESSING, // Tide: emergency heal
        BRUTE_FORCE,    // Snout: splash damage
        INFERNAL,       // Rib: fire attacks bonus
        FORTUNE_PEAK,   // Spire: double emeralds
        PATHFINDER,     // Wayfinder: ignore obstacles
        TERRAFORMER,    // Shaper: movement deals AoE damage
        PHANTOM,        // Silence: invisible start
        RALLY,          // Raiser: ally buff
        SYMBIOTE,       // Host: heal on kill
        CURRENT,        // Flow: kill refunds AP
        THUNDERSTRIKE   // Bolt: crit stuns
    }

    public record TrimScan(
        Map<Bonus, Integer> bonuses,  // accumulated per-piece bonuses
        SetBonus setBonus,            // active full-set bonus (NONE if no full match)
        String setName,               // display name of active set bonus
        int trimCount,                // how many armor pieces have trims
        Map<String, Integer> materialCounts, // material id -> count for display
        java.util.List<com.crackedgames.craftics.api.NamedCombatEffect> combatEffects // addon combat effects
    ) {
        public int get(Bonus b) { return bonuses.getOrDefault(b, 0); }
        public boolean hasSet() { return setBonus != SetBonus.NONE; }
        public java.util.List<com.crackedgames.craftics.api.NamedCombatEffect> getCombatEffects() {
            return combatEffects != null ? combatEffects : java.util.List.of();
        }
    }

    public static TrimScan scan(ServerPlayerEntity player) {
        Map<Bonus, Integer> bonuses = new HashMap<>();
        Map<String, Integer> patternCounts = new HashMap<>();
        Map<String, Integer> materialCounts = new HashMap<>();
        int trimCount = 0;

        EquipmentSlot[] armorSlots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;

            ArmorTrim trim = stack.get(DataComponentTypes.TRIM);
            if (trim == null) continue;

            trimCount++;
            RegistryEntry<ArmorTrimPattern> pattern = trim.getPattern();
            String patternId = pattern.getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            patternCounts.merge(patternId, 1, Integer::sum);

            Bonus bonus = TrimPatternRegistry.getPerPieceBonus(patternId);
            if (bonus != null) {
                bonuses.merge(bonus, 1, Integer::sum);
            }

            RegistryEntry<ArmorTrimMaterial> material = trim.getMaterial();
            String materialId = material.getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            materialCounts.merge(materialId, 1, Integer::sum);
            Bonus matBonus = TrimMaterialRegistry.getMaterialBonus(materialId);
            if (matBonus != null) {
                TrimMaterialEntry matEntry = TrimMaterialRegistry.get(materialId);
                int matValue = matEntry != null ? matEntry.valuePerPiece() : 1;
                bonuses.merge(matBonus, matValue, Integer::sum);
            }
        }

        SetBonus setBonus = SetBonus.NONE;
        String setName = "";
        for (var entry : patternCounts.entrySet()) {
            if (entry.getValue() >= 4) {
                setBonus = TrimPatternRegistry.getSetBonus(entry.getKey());
                setName = TrimPatternRegistry.getSetBonusName(entry.getKey());
                break;
            }
        }

        // Merge addon equipment scanner bonuses
        StatModifiers addonMods = EquipmentScannerRegistry.scanAll(player);
        for (var entry : addonMods.getAll().entrySet()) {
            bonuses.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        if (addonMods.getSetBonus() != SetBonus.NONE && setBonus == SetBonus.NONE) {
            setBonus = addonMods.getSetBonus();
            setName = addonMods.getSetBonusName();
        }

        // Collect combat effect handlers from addon scanners
        var combatEffects = new java.util.ArrayList<>(addonMods.getCombatEffects());

        return new TrimScan(bonuses, setBonus, setName, trimCount, materialCounts, combatEffects);
    }

    public static String getMaterialDescription(String materialId) {
        return TrimMaterialRegistry.getDescription(materialId);
    }

    public static String getPerPieceDescription(String patternId) {
        return TrimPatternRegistry.getPerPieceDescription(patternId);
    }

    public static String getSetBonusDescription(SetBonus bonus) {
        return switch (bonus) {
            case OVERWATCH      -> "Counter-attack ranged enemies that hit you";
            case SANDSTORM      -> "Enemies within 2 tiles lose 1 Speed";
            case TIDAL          -> "Water tiles heal 1 HP/turn";
            case FERAL          -> "Kill streak: 1.3x damage per streak level (resets if no kills on your turn)";
            case FORTRESS       -> "50% less damage when you didn't move this turn";
            case ALL_SEEING     -> "Ranged attacks have +30% crit chance";
            case ETHEREAL       -> "20% chance to dodge incoming attacks";
            case OCEAN_BLESSING -> "Full heal when dropping below 25% HP (once per combat)";
            case BRUTE_FORCE    -> "Melee attacks splash to adjacent enemies";
            case INFERNAL       -> "Fire attacks deal +3 bonus damage";
            case FORTUNE_PEAK   -> "Double emerald rewards";
            case PATHFINDER     -> "Movement ignores obstacle tiles";
            case TERRAFORMER    -> "Moving 3+ tiles deals 2 damage to all enemies adjacent to your destination";
            case PHANTOM        -> "Invisible for first 2 turns (enemies don't act)";
            case RALLY          -> "Tamed allies get +2 Speed and +1 Attack";
            case SYMBIOTE       -> "Heal 1 HP for each enemy killed";
            case CURRENT        -> "Killing an enemy refunds 1 AP";
            case THUNDERSTRIKE  -> "Critical hits stun the target for 1 turn";
            case NONE           -> "";
        };
    }

    // Which trims drop from bosses in each dimension
    public static net.minecraft.item.Item[] getBossDropTrims(String dimension) {
        return switch (dimension) {
            case "overworld" -> new net.minecraft.item.Item[]{
                net.minecraft.item.Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            };
            case "nether" -> new net.minecraft.item.Item[]{
                net.minecraft.item.Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            };
            case "end" -> new net.minecraft.item.Item[]{
                net.minecraft.item.Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            };
            case "trial" -> new net.minecraft.item.Item[]{
                net.minecraft.item.Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
                net.minecraft.item.Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
            };
            default -> new net.minecraft.item.Item[0];
        };
    }
}
