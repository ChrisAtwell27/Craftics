package com.crackedgames.craftics.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.item.trim.ArmorTrimPatterns;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Armor trim combat effects. Each trim pattern grants a stackable per-piece bonus
 * and a powerful full-set bonus when all 4 armor pieces share the same trim.
 *
 * Trim templates drop from dimension-appropriate bosses.
 */
public class TrimEffects {

    /** Per-piece bonus types that stack (1 per trimmed armor piece, max 4). */
    public enum Bonus {
        RANGED_POWER, MELEE_POWER, SPEED, AP, DEFENSE, LUCK,
        ATTACK_RANGE, MAX_HP, ARMOR_PEN, REGEN, ALLY_DAMAGE,
        STEALTH_RANGE, BURN_REDUCTION, WATER_DAMAGE_REDUCTION,
        // Damage-type-specific power bonuses
        SWORD_POWER, CLEAVING_POWER, BLUNT_POWER, WATER_POWER, SPECIAL_POWER
    }

    /** Full set bonus names. */
    public enum SetBonus {
        NONE,
        OVERWATCH,      // Sentry: counter-attack ranged
        SANDSTORM,      // Dune: enemy speed aura
        TIDAL,          // Coast: water heals
        FERAL,          // Wild: first attack free
        FORTRESS,       // Ward: halve damage when stationary
        ALL_SEEING,     // Eye: see enemy stats
        ETHEREAL,       // Vex: dodge chance
        OCEAN_BLESSING, // Tide: emergency heal
        BRUTE_FORCE,    // Snout: splash damage
        INFERNAL,       // Rib: fire attacks bonus
        FORTUNE_PEAK,   // Spire: double emeralds
        PATHFINDER,     // Wayfinder: ignore obstacles
        TERRAFORMER,    // Shaper: free barrier
        PHANTOM,        // Silence: invisible start
        RALLY,          // Raiser: ally buff
        SYMBIOTE,       // Host: heal on kill
        CURRENT,        // Flow: kill refunds AP
        THUNDERSTRIKE   // Bolt: crit stuns
    }

    /** Result of scanning a player's equipped armor for trim effects. */
    public record TrimScan(
        Map<Bonus, Integer> bonuses,  // accumulated per-piece bonuses
        SetBonus setBonus,            // active full-set bonus (NONE if no full match)
        String setName,               // display name of active set bonus
        int trimCount,                // how many armor pieces have trims
        Map<String, Integer> materialCounts // material id -> count for display
    ) {
        public int get(Bonus b) { return bonuses.getOrDefault(b, 0); }
        public boolean hasSet() { return setBonus != SetBonus.NONE; }
    }

    /** Scan a player's equipped armor and compute all trim bonuses. */
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

            // Apply per-piece bonus based on pattern
            Bonus bonus = getPerPieceBonus(patternId);
            if (bonus != null) {
                bonuses.merge(bonus, 1, Integer::sum);
            }

            // Apply per-piece bonus based on material
            RegistryEntry<ArmorTrimMaterial> material = trim.getMaterial();
            String materialId = material.getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            materialCounts.merge(materialId, 1, Integer::sum);
            Bonus matBonus = getMaterialBonus(materialId);
            if (matBonus != null) {
                bonuses.merge(matBonus, 1, Integer::sum);
            }
        }

        // Check for full set (4 pieces with same trim pattern)
        SetBonus setBonus = SetBonus.NONE;
        String setName = "";
        for (var entry : patternCounts.entrySet()) {
            if (entry.getValue() >= 4) {
                setBonus = getSetBonus(entry.getKey());
                setName = getSetBonusName(entry.getKey());
                break;
            }
        }

        return new TrimScan(bonuses, setBonus, setName, trimCount, materialCounts);
    }

    /** Map trim pattern ID to per-piece bonus type. */
    private static Bonus getPerPieceBonus(String patternId) {
        return switch (patternId) {
            case "sentry"    -> Bonus.RANGED_POWER;
            case "dune"      -> Bonus.BLUNT_POWER;
            case "coast"     -> Bonus.WATER_POWER;
            case "wild"      -> Bonus.AP;
            case "ward"      -> Bonus.DEFENSE;
            case "eye"       -> Bonus.ATTACK_RANGE;
            case "vex"       -> Bonus.ARMOR_PEN;
            case "tide"      -> Bonus.REGEN;
            case "snout"     -> Bonus.CLEAVING_POWER;
            case "rib"       -> Bonus.SPECIAL_POWER;
            case "spire"     -> Bonus.LUCK;
            case "wayfinder" -> Bonus.SPEED;
            case "shaper"    -> Bonus.DEFENSE;
            case "silence"   -> Bonus.STEALTH_RANGE;
            case "raiser"    -> Bonus.ALLY_DAMAGE;
            case "host"      -> Bonus.MAX_HP;
            case "flow"      -> Bonus.SPEED;
            case "bolt"      -> Bonus.SWORD_POWER;
            default -> null;
        };
    }

    /**
     * Map trim material ID to per-piece bonus type.
     * Materials determine the color of the trim and grant a secondary stat bonus.
     *
     * Iron       → +1 Defense
     * Copper     → +1 Speed
     * Gold       → +1 Luck
     * Lapis      → +1 Magic Power
     * Emerald    → +1 AP
     * Diamond    → +1 Melee Power
     * Netherite  → +1 Armor Pen
     * Redstone   → +1 Ranged Power
     * Amethyst   → +1 Regen
     * Quartz     → +1 Max HP
     */
    private static Bonus getMaterialBonus(String materialId) {
        return switch (materialId) {
            case "iron"      -> Bonus.DEFENSE;
            case "copper"    -> Bonus.SPEED;
            case "gold"      -> Bonus.LUCK;
            case "lapis"     -> Bonus.SPECIAL_POWER;
            case "emerald"   -> Bonus.AP;
            case "diamond"   -> Bonus.MELEE_POWER;
            case "netherite" -> Bonus.ARMOR_PEN;
            case "redstone"  -> Bonus.RANGED_POWER;
            case "amethyst"  -> Bonus.REGEN;
            case "quartz"    -> Bonus.MAX_HP;
            default -> null;
        };
    }

    /** Get description for a trim material's per-piece bonus. */
    public static String getMaterialDescription(String materialId) {
        return switch (materialId) {
            case "iron"      -> "+1 Defense per piece";
            case "copper"    -> "+1 Speed per piece";
            case "gold"      -> "+1 Luck per piece";
            case "lapis"     -> "+1 Magic Power per piece";
            case "emerald"   -> "+1 AP per piece";
            case "diamond"   -> "+1 Melee Power per piece";
            case "netherite" -> "+1 Armor Penetration per piece";
            case "redstone"  -> "+1 Ranged Power per piece";
            case "amethyst"  -> "+1 HP Regen per piece";
            case "quartz"    -> "+2 Max HP per piece";
            case "resin"     -> "+1 Melee Power per piece";
            default -> "";
        };
    }

    /** Map trim pattern ID to full-set bonus. */
    private static SetBonus getSetBonus(String patternId) {
        return switch (patternId) {
            case "sentry"    -> SetBonus.OVERWATCH;
            case "dune"      -> SetBonus.SANDSTORM;
            case "coast"     -> SetBonus.TIDAL;
            case "wild"      -> SetBonus.FERAL;
            case "ward"      -> SetBonus.FORTRESS;
            case "eye"       -> SetBonus.ALL_SEEING;
            case "vex"       -> SetBonus.ETHEREAL;
            case "tide"      -> SetBonus.OCEAN_BLESSING;
            case "snout"     -> SetBonus.BRUTE_FORCE;
            case "rib"       -> SetBonus.INFERNAL;
            case "spire"     -> SetBonus.FORTUNE_PEAK;
            case "wayfinder" -> SetBonus.PATHFINDER;
            case "shaper"    -> SetBonus.TERRAFORMER;
            case "silence"   -> SetBonus.PHANTOM;
            case "raiser"    -> SetBonus.RALLY;
            case "host"      -> SetBonus.SYMBIOTE;
            case "flow"      -> SetBonus.CURRENT;
            case "bolt"      -> SetBonus.THUNDERSTRIKE;
            default -> SetBonus.NONE;
        };
    }

    /** Display name for the full-set bonus. */
    private static String getSetBonusName(String patternId) {
        return switch (patternId) {
            case "sentry"    -> "Overwatch";
            case "dune"      -> "Sandstorm";
            case "coast"     -> "Tidal";
            case "wild"      -> "Feral";
            case "ward"      -> "Fortress";
            case "eye"       -> "All-Seeing";
            case "vex"       -> "Ethereal";
            case "tide"      -> "Ocean's Blessing";
            case "snout"     -> "Brute Force";
            case "rib"       -> "Infernal";
            case "spire"     -> "Fortune's Peak";
            case "wayfinder" -> "Pathfinder";
            case "shaper"    -> "Terraformer";
            case "silence"   -> "Phantom";
            case "raiser"    -> "Rally";
            case "host"      -> "Symbiote";
            case "flow"      -> "Current";
            case "bolt"      -> "Thunderstrike";
            default -> "";
        };
    }

    /** Get the description for a per-piece bonus. */
    public static String getPerPieceDescription(String patternId) {
        return switch (patternId) {
            case "sentry"    -> "+1 Ranged Power per piece";
            case "dune"      -> "+1 Blunt Power per piece";
            case "coast"     -> "+1 Water Power per piece";
            case "wild"      -> "+1 AP per piece";
            case "ward"      -> "+1 Defense per piece";
            case "eye"       -> "+1 Attack Range per piece";
            case "vex"       -> "Ignore 1 enemy DEF per piece";
            case "tide"      -> "+1 HP regen per 2 turns per piece";
            case "snout"     -> "+1 Cleaving Power per piece";
            case "rib"       -> "+1 Magic Power per piece";
            case "spire"     -> "+1 Luck per piece";
            case "wayfinder" -> "+1 Speed per piece";
            case "shaper"    -> "+1 Defense per piece";
            case "silence"   -> "+1 stealth range per piece";
            case "raiser"    -> "+1 ally damage per piece";
            case "host"      -> "+2 max HP per piece";
            case "flow"      -> "+1 Speed per piece";
            case "bolt"      -> "+1 Slashing Power per piece";
            default -> "";
        };
    }

    /** Get the description for a full-set bonus. */
    public static String getSetBonusDescription(SetBonus bonus) {
        return switch (bonus) {
            case OVERWATCH      -> "Counter-attack ranged enemies that hit you";
            case SANDSTORM      -> "Enemies within 2 tiles lose 1 Speed";
            case TIDAL          -> "Water tiles heal 1 HP/turn";
            case FERAL          -> "Kill streak: 1.3x damage per streak level (resets if no kills on your turn)";
            case FORTRESS       -> "50% less damage when you didn't move this turn";
            case ALL_SEEING     -> "See all enemy stats and their next action";
            case ETHEREAL       -> "20% chance to dodge incoming attacks";
            case OCEAN_BLESSING -> "Full heal when dropping below 25% HP (once per combat)";
            case BRUTE_FORCE    -> "Melee attacks splash to adjacent enemies";
            case INFERNAL       -> "Fire attacks deal +3 bonus damage";
            case FORTUNE_PEAK   -> "Double emerald rewards";
            case PATHFINDER     -> "Movement ignores obstacle tiles";
            case TERRAFORMER    -> "Place a free barrier block each turn";
            case PHANTOM        -> "Invisible for first 2 turns (enemies don't act)";
            case RALLY          -> "Tamed allies get +2 Speed and +1 Attack";
            case SYMBIOTE       -> "Heal 1 HP for each enemy killed";
            case CURRENT        -> "Killing an enemy refunds 1 AP";
            case THUNDERSTRIKE  -> "Critical hits stun the target for 1 turn";
            case NONE           -> "";
        };
    }

    // ==================== BOSS DROP HELPERS ====================

    /**
     * Get trim template items that can drop from a boss in the given dimension.
     * Returns the Item for the smithing template.
     *
     * Overworld bosses:  Sentry, Dune, Coast, Wild, Wayfinder, Shaper, Raiser, Host
     * Nether bosses:     Ward, Snout, Rib, Eye, Snout
     * End bosses:        Spire, Vex, Silence
     * Trial chambers:    Flow, Bolt
     * Ocean/Tide:        Tide
     */
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
