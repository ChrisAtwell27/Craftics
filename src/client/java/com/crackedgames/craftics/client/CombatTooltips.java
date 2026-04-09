package com.crackedgames.craftics.client;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.Map;

/**
 * Adds Craftics combat tooltips to every relevant item.
 * Every item with combat functionality gets a tooltip explaining its effect.
 */
public class CombatTooltips implements ItemTooltipCallback {

    @Override
    public void getTooltip(net.minecraft.item.ItemStack stack, net.minecraft.item.Item.TooltipContext ctx,
                           net.minecraft.item.tooltip.TooltipType type, java.util.List<Text> lines) {
        Item item = stack.getItem();

        // Goat horn: scan existing tooltip lines for the variant name
        // (Minecraft adds the instrument name as a subtitle line like "Seek")
        if (item == Items.GOAT_HORN) {
            // Check all existing tooltip lines for variant keywords
            for (Text line : lines) {
                String lineStr = line.getString();
                String hornTip = getHornVariantTooltip(lineStr);
                if (hornTip != null) {
                    lines.add(Text.empty());
                    lines.add(Text.literal("\u00a76\u00a7lCraftics Combat:"));
                    lines.add(Text.literal(hornTip));
                    return;
                }
            }
            // Also check custom name (our created horns)
            String displayName = stack.getName().getString();
            String hornTip = getHornVariantTooltip(displayName);
            if (hornTip != null) {
                lines.add(Text.empty());
                lines.add(Text.literal("\u00a76\u00a7lCraftics Combat:"));
                lines.add(Text.literal(hornTip));
                return;
            }
        }

        // Potions and tipped arrows: read the actual effect from POTION_CONTENTS
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION
                || item == Items.TIPPED_ARROW) {
            addPotionTooltip(stack, item, lines);
        }

        // Smithing template: show trim combat effects for the template item
        String templatePattern = getSmithingTemplatePattern(item);
        if (templatePattern != null) {
            String perPiece = getTrimPerPieceDescription(templatePattern);
            if (!perPiece.isEmpty()) {
                lines.add(Text.empty());
                lines.add(Text.literal("\u00a7b\u00a7lTrim Combat Bonus:"));
                lines.add(Text.literal("\u00a7b  " + perPiece));
                String setName = getTrimSetBonusName(templatePattern);
                String setDesc = getTrimSetBonusDescription(templatePattern);
                if (!setName.isEmpty()) {
                    lines.add(Text.literal("\u00a78  Full Set (\u00a7e" + setName + "\u00a78): " + setDesc));
                }
            }
        }

        // The Move feather has a custom name; plain loot feathers should not get a tooltip
        String tip = (item == net.minecraft.item.Items.FEATHER && !stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME))
            ? null : getTooltipFor(item);
        if (tip != null) {
            lines.add(Text.empty());
            lines.add(Text.literal("\u00a76\u00a7lCraftics Combat:"));
            for (String line : tip.split("\n")) {
                lines.add(Text.literal(line));
            }
        }

        // Per-enchantment combat tooltips (works on weapons, armor, and enchanted books)
        addEnchantmentTooltips(stack, lines);

        // Armor trim combat effects tooltip (on already-trimmed armor)
        net.minecraft.item.trim.ArmorTrim trim = stack.get(net.minecraft.component.DataComponentTypes.TRIM);
        if (trim != null) {
            String patternId = trim.getPattern().getKey()
                .map(k -> k.getValue().getPath()).orElse("");
            if (patternId.isEmpty()) {
                try { patternId = trim.getPattern().value().assetId().getPath(); } catch (Exception ignored) {}
            }
            String materialId = trim.getMaterial().getKey()
                .map(k -> k.getValue().getPath()).orElse("");
            String perPiece = getTrimPerPieceDescription(patternId);
            String matDesc = getMaterialDescription(materialId);
            if (!perPiece.isEmpty() || !matDesc.isEmpty()) {
                lines.add(Text.empty());
                lines.add(Text.literal("\u00a7b\u00a7lTrim Combat Bonus:"));
                if (!perPiece.isEmpty()) {
                    lines.add(Text.literal("\u00a7b  Pattern: " + perPiece));
                }
                if (!matDesc.isEmpty()) {
                    lines.add(Text.literal("\u00a7e  Material: " + matDesc));
                }
                String setName = getTrimSetBonusName(patternId);
                String setDesc = getTrimSetBonusDescription(patternId);
                if (!setName.isEmpty()) {
                    lines.add(Text.literal("\u00a78  Full Set (\u00a7e" + setName + "\u00a78): " + setDesc));
                }
            }
        }
    }

    /**
     * Read enchantments from item and add per-enchant combat tooltips.
     * Reads both ENCHANTMENTS (on gear) and STORED_ENCHANTMENTS (on enchanted books).
     */
    private static void addEnchantmentTooltips(net.minecraft.item.ItemStack stack, java.util.List<Text> lines) {
        // Check both regular enchantments and stored enchantments (books)
        var enchants = stack.get(net.minecraft.component.DataComponentTypes.ENCHANTMENTS);
        var stored = stack.get(net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS);

        var combined = new java.util.ArrayList<java.util.Map.Entry<String, Integer>>();

        if (enchants != null && !enchants.isEmpty()) {
            for (var entry : enchants.getEnchantmentEntries()) {
                String id = entry.getKey().getKey().map(k -> k.getValue().getPath()).orElse("");
                if (!id.isEmpty()) combined.add(java.util.Map.entry(id, entry.getIntValue()));
            }
        }
        if (stored != null && !stored.isEmpty()) {
            for (var entry : stored.getEnchantmentEntries()) {
                String id = entry.getKey().getKey().map(k -> k.getValue().getPath()).orElse("");
                if (!id.isEmpty()) combined.add(java.util.Map.entry(id, entry.getIntValue()));
            }
        }

        if (combined.isEmpty()) return;

        boolean headerAdded = false;
        for (var entry : combined) {
            String effect = getEnchantEffect(entry.getKey(), entry.getValue());
            if (effect != null) {
                if (!headerAdded) {
                    lines.add(Text.empty());
                    lines.add(Text.literal("\u00a7d\u00a7lCraftics Enchant Effects:"));
                    headerAdded = true;
                }
                lines.add(Text.literal(effect));
            }
        }
    }

    /**
     * Get the combat effect description for a specific enchantment and level.
     */
    private static String getEnchantEffect(String enchantId, int level) {
        return switch (enchantId) {
            // Weapon damage
            case "sharpness" -> "\u00a7c\u2694 Sharpness " + toRoman(level) + ": \u00a77+" + level + " melee damage";
            case "smite" -> "\u00a7e\u2694 Smite " + toRoman(level) + ": \u00a77+" + (level * 2) + " damage vs undead";
            case "bane_of_arthropods" -> "\u00a72\u2694 Bane " + toRoman(level) + ": \u00a77+" + (level * 2) + " damage vs spiders";
            case "power" -> "\u00a7c\u27B3 Power " + toRoman(level) + ": \u00a77+" + level + " ranged damage";
            case "impaling" -> "\u00a7b\u2694 Impaling " + toRoman(level) + ": \u00a77+" + (level * 2) + " damage vs aquatic";
            case "density" -> "\u00a78\u2694 Density " + toRoman(level) + ": \u00a77+" + level + " AoE damage (mace)";
            case "breach" -> "\u00a74\u2694 Breach " + toRoman(level) + ": \u00a77Ignore " + level + " enemy DEF";

            // Weapon utility
            case "fire_aspect" -> "\u00a76\u2604 Fire Aspect " + toRoman(level) + ": \u00a77Ignite target for " + (level * 4) + "s";
            case "knockback" -> "\u00a7e\u2B05 Knockback " + toRoman(level) + ": \u00a77Push target " + level + " extra tile(s)";
            case "looting" -> "\u00a7a\u2728 Looting " + toRoman(level) + ": \u00a77+" + level + " bonus loot drops";
            case "sweeping" -> "\u00a77\u2694 Sweeping Edge " + toRoman(level) + ": \u00a77Cleave deals " + (50 + level * 17) + "% to adjacent";
            case "wind_burst" -> "\u00a7b\u2B05 Wind Burst " + toRoman(level) + ": \u00a77+" + level + " knockback range";

            // Bow/crossbow
            case "flame" -> "\u00a76\u2604 Flame: \u00a77Arrows ignite targets";
            case "infinity" -> "\u00a7e\u221E Infinity: \u00a77Unlimited arrows (no ammo cost)";
            case "punch" -> "\u00a7e\u2B05 Punch " + toRoman(level) + ": \u00a77Arrows push target " + level + " tile(s)";
            case "piercing" -> "\u00a78\u27B3 Piercing " + toRoman(level) + ": \u00a77Bolt hits " + (level + 1) + " targets";
            case "multishot" -> "\u00a7d\u27B3 Multishot: \u00a77Fires 2 extra bolts diagonally";
            case "quick_charge" -> "\u00a7a\u26A1 Quick Charge " + toRoman(level) + ": \u00a77Crossbow costs " + Math.max(1, 4 - level) + " AP";

            // Armor defense
            case "protection" -> "\u00a79\u26E8 Protection " + toRoman(level) + ": \u00a77+" + (level / 2 + 1) + " defense";
            case "blast_protection" -> "\u00a76\u26E8 Blast Prot " + toRoman(level) + ": \u00a77-" + level + " explosion damage";
            case "projectile_protection" -> "\u00a7b\u26E8 Proj Prot " + toRoman(level) + ": \u00a77-" + level + " ranged damage taken";
            case "fire_protection" -> "\u00a7c\u26E8 Fire Prot " + toRoman(level) + ": \u00a77-" + level + " fire damage taken";

            // Armor utility
            case "thorns" -> "\u00a74\u2748 Thorns " + toRoman(level) + ": \u00a77" + (level * 15) + "% chance reflect " + level + " damage";
            case "respiration" -> "\u00a7b\u2B58 Respiration " + toRoman(level) + ": \u00a77+" + level + " turns underwater";
            case "feather_falling" -> "\u00a7a\u2193 Feather Falling " + toRoman(level) + ": \u00a77-" + level + " fall/knockback damage";
            case "depth_strider" -> "\u00a73\u2248 Depth Strider " + toRoman(level) + ": \u00a77+" + level + " speed on water tiles";
            case "frost_walker" -> "\u00a7b\u2744 Frost Walker " + toRoman(level) + ": \u00a77Water tiles become walkable ice";
            case "soul_speed" -> "\u00a75\u2605 Soul Speed " + toRoman(level) + ": \u00a77+" + level + " speed on soul sand";
            case "swift_sneak" -> "\u00a78\u2605 Swift Sneak " + toRoman(level) + ": \u00a77No speed penalty when sneaking";

            // Tool enchants with combat use
            case "efficiency" -> "\u00a7e\u26CF Efficiency " + toRoman(level) + ": \u00a77Break obstacles faster";
            case "silk_touch" -> "\u00a7a\u2728 Silk Touch: \u00a77Obstacle drops itself when broken";
            case "fortune" -> "\u00a7a\u2728 Fortune " + toRoman(level) + ": \u00a77+" + level + " bonus drops from obstacles";
            case "unbreaking" -> "\u00a77\u26E8 Unbreaking " + toRoman(level) + ": \u00a77" + (100 / (level + 1)) + "% durability use";
            case "mending" -> "\u00a7a\u2764 Mending: \u00a77Repairs with XP on kill";

            // Trident
            case "loyalty" -> "\u00a7b\u21BA Loyalty " + toRoman(level) + ": \u00a77Trident returns after throwing";
            case "riptide" -> "\u00a73\u2B06 Riptide " + toRoman(level) + ": \u00a77Dash in a line, hit + knockback " + (1 + level) + " all enemies";
            case "channeling" -> "\u00a7e\u26A1 Channeling " + toRoman(level) + ": \u00a77Lightning on throw (" + (4 + level * 2) + " dmg, 2x if Soaked, chains to " + Math.max(0, level - 1) + ")";

            default -> null;
        };
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    /**
     * Add specific potion/tipped arrow tooltip based on the actual effect.
     */
    private static void addPotionTooltip(net.minecraft.item.ItemStack stack, Item item, java.util.List<Text> lines) {
        var contents = stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return;

        // Determine the base type label
        String typeLabel;
        if (item == Items.POTION) typeLabel = "\u00a7d1 AP \u00a77- Drink potion";
        else if (item == Items.SPLASH_POTION) typeLabel = "\u00a7d1 AP \u00a77- Throw at target";
        else if (item == Items.LINGERING_POTION) typeLabel = "\u00a751 AP \u00a77- Create effect cloud";
        else typeLabel = "\u00a7dSpecial Ammo \u00a77- Applies effect on hit";

        lines.add(Text.empty());
        lines.add(Text.literal("\u00a76\u00a7lCraftics Combat:"));
        lines.add(Text.literal(typeLabel));

        // Read each status effect on this potion/arrow
        boolean hasEffect = false;
        for (net.minecraft.entity.effect.StatusEffectInstance effect : contents.getEffects()) {
            String effectId = effect.getEffectType().getKey()
                .map(k -> k.getValue().getPath()).orElse("");
            int amplifier = effect.getAmplifier();
            String desc = getPotionEffectDescription(effectId, amplifier, item == Items.TIPPED_ARROW);
            if (desc != null) {
                lines.add(Text.literal(desc));
                hasEffect = true;
            }
        }

        if (!hasEffect) {
            // Try reading from the potion registry entry name
            contents.potion().ifPresent(potionEntry -> {
                String potionId = potionEntry.getKey()
                    .map(k -> k.getValue().getPath()).orElse("");
                String desc = getPotionIdDescription(potionId, item == Items.TIPPED_ARROW);
                if (desc != null) {
                    lines.add(Text.literal(desc));
                }
            });
        }
    }

    /**
     * Get combat description for a specific status effect.
     */
    private static String getPotionEffectDescription(String effectId, int amplifier, boolean isArrow) {
        String prefix = isArrow ? "\u00a77  On hit: " : "\u00a77  ";
        String lvl = amplifier > 0 ? " " + toRoman(amplifier + 1) : "";
        return switch (effectId) {
            case "speed" -> prefix + "\u00a7bSpeed" + lvl + ": \u00a77+2 movement for 3 turns";
            case "slowness" -> prefix + "\u00a77Slowness" + lvl + ": \u00a77-" + (1 + amplifier) + " movement for 2 turns";
            case "strength" -> prefix + "\u00a7cStrength" + lvl + ": \u00a77+" + (3 + amplifier * 2) + " attack for 3 turns";
            case "weakness" -> prefix + "\u00a78Weakness" + lvl + ": \u00a77-2 attack for 2 turns";
            case "instant_health" -> prefix + "\u00a7aHealing" + lvl + ": \u00a77Restore " + (4 + amplifier * 4) + " HP instantly";
            case "instant_damage" -> prefix + "\u00a74Harming" + lvl + ": \u00a77Deal " + (4 + amplifier * 4) + " damage";
            case "regeneration" -> prefix + "\u00a7dRegen" + lvl + ": \u00a77+2 HP/turn for 3 turns";
            case "resistance" -> prefix + "\u00a79Resistance" + lvl + ": \u00a77+2 defense for 3 turns";
            case "fire_resistance" -> prefix + "\u00a76Fire Res: \u00a77Immune to fire for 3 turns, \u00a75+1 Special Power";
            case "poison" -> prefix + "\u00a72Poison" + lvl + ": \u00a77-" + (1 + amplifier) + " HP/turn for 3 turns";
            case "invisibility" -> prefix + "\u00a77Invisibility: \u00a77Enemies skip your turn for 2 turns";
            case "night_vision" -> prefix + "\u00a7eNight Vision: \u00a77See in darkness (no combat effect)";
            case "absorption" -> prefix + "\u00a76Absorption" + lvl + ": \u00a77+" + (4 + amplifier * 4) + " bonus HP";
            case "luck" -> prefix + "\u00a7aLuck" + lvl + ": \u00a77+" + (1 + amplifier) + " crit chance for 3 turns";
            case "jump_boost" -> prefix + "\u00a7aLeaping" + lvl + ": \u00a77+1 movement for 3 turns";
            case "water_breathing" -> prefix + "\u00a73Water Breathing: \u00a77No drowning damage, \u00a73+2 Water Power";
            case "haste" -> prefix + "\u00a7eHaste" + lvl + ": \u00a77+1 AP for 3 turns";
            case "mining_fatigue" -> prefix + "\u00a78Mining Fatigue: \u00a77-1 AP for 2 turns";
            case "levitation" -> prefix + "\u00a7dLevitation: \u00a77-1 movement for 2 turns";
            case "slow_falling" -> prefix + "\u00a7fSlow Falling: \u00a77No knockback for 3 turns";
            case "wither" -> prefix + "\u00a78Wither" + lvl + ": \u00a77-2 HP/turn for 3 turns";
            case "blindness" -> prefix + "\u00a78Blindness: \u00a77-2 attack range for 2 turns";
            default -> null;
        };
    }

    /**
     * Fallback: get description from potion registry ID (e.g., "strong_healing", "long_swiftness").
     */
    private static String getPotionIdDescription(String potionId, boolean isArrow) {
        String prefix = isArrow ? "\u00a77  On hit: " : "\u00a77  ";
        // Strip "long_" and "strong_" prefixes, match base potion
        String base = potionId.replace("long_", "").replace("strong_", "");
        boolean strong = potionId.startsWith("strong_");
        String lvl = strong ? " II" : "";
        return switch (base) {
            case "swiftness" -> prefix + "\u00a7bSpeed" + lvl + ": \u00a77+2 movement for 3 turns";
            case "slowness" -> prefix + "\u00a77Slowness" + lvl + ": \u00a77-1 movement for 2 turns";
            case "strength" -> prefix + "\u00a7cStrength" + lvl + ": \u00a77+" + (strong ? 5 : 3) + " attack for 3 turns";
            case "weakness" -> prefix + "\u00a78Weakness: \u00a77-2 attack for 2 turns";
            case "healing" -> prefix + "\u00a7aHealing" + lvl + ": \u00a77Restore " + (strong ? 8 : 4) + " HP instantly";
            case "harming" -> prefix + "\u00a74Harming" + lvl + ": \u00a77Deal " + (strong ? 8 : 4) + " damage";
            case "regeneration" -> prefix + "\u00a7dRegen" + lvl + ": \u00a77+2 HP/turn for 3 turns";
            case "fire_resistance" -> prefix + "\u00a76Fire Res: \u00a77Immune to fire for 3 turns, \u00a75+1 Special Power";
            case "poison" -> prefix + "\u00a72Poison" + lvl + ": \u00a77-1 HP/turn for 3 turns";
            case "invisibility" -> prefix + "\u00a77Invisibility: \u00a77Enemies skip you for 2 turns";
            case "night_vision" -> prefix + "\u00a7eNight Vision: \u00a77(no combat effect)";
            case "leaping" -> prefix + "\u00a7aLeaping" + lvl + ": \u00a77+1 movement for 3 turns";
            case "water_breathing" -> prefix + "\u00a73Water Breathing: \u00a77No drowning damage, \u00a73+2 Water Power";
            case "turtle_master" -> prefix + "\u00a72Turtle Master: \u00a77+4 DEF, -2 movement for 3 turns";
            case "luck" -> prefix + "\u00a7aLuck: \u00a77+1 crit chance for 3 turns";
            case "slow_falling" -> prefix + "\u00a7fSlow Falling: \u00a77No knockback for 3 turns";
            default -> prefix + "\u00a78" + potionId.replace("_", " ") + " (no combat effect)";
        };
    }

    private static String getSmithingTemplatePattern(Item item) {
        if (item == Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE) return "sentry";
        if (item == Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE) return "dune";
        if (item == Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE) return "coast";
        if (item == Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE) return "wild";
        if (item == Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE) return "ward";
        if (item == Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE) return "eye";
        if (item == Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE) return "vex";
        if (item == Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE) return "tide";
        if (item == Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE) return "snout";
        if (item == Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE) return "rib";
        if (item == Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE) return "spire";
        if (item == Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE) return "wayfinder";
        if (item == Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE) return "shaper";
        if (item == Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE) return "silence";
        if (item == Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE) return "raiser";
        if (item == Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE) return "host";
        if (item == Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE) return "flow";
        if (item == Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE) return "bolt";
        return null;
    }

    private static String getHornVariantTooltip(String nameStr) {
        if (nameStr.contains("Ponder")) return "\u00a77Ponder Horn \u2014 \u00a77\u00a7oA contemplative note... \u00a79+2 Defense for 3 turns \u00a78(AP: 1)";
        if (nameStr.contains("Sing")) return "\u00a7eSing Horn \u2014 \u00a7e\u00a7oAn uplifting melody! \u00a7a+2 HP regen for 3 turns \u00a78(AP: 1)";
        if (nameStr.contains("Seek")) return "\u00a76Seek Horn \u2014 \u00a76\u00a7oA rallying cry! \u00a7c+3 Attack for 3 turns \u00a78(AP: 2)";
        if (nameStr.contains("Feel")) return "\u00a7dFeel Horn \u2014 \u00a7d\u00a7oA soothing hum... \u00a7b+2 Speed for 3 turns \u00a78(AP: 1)";
        if (nameStr.contains("Admire")) return "\u00a7bAdmire Horn \u2014 \u00a7b\u00a7oA piercing blast! \u00a77All enemies -2 Attack for 2 turns \u00a78(AP: 2)";
        if (nameStr.contains("Call")) return "\u00a7aCall Horn \u2014 \u00a7a\u00a7oA thunderous bellow! \u00a73All enemies -1 Speed for 2 turns \u00a78(AP: 2)";
        if (nameStr.contains("Yearn")) return "\u00a75Yearn Horn \u2014 \u00a75\u00a7oA haunting wail! \u00a72All enemies Poisoned for 3 turns \u00a78(AP: 3)";
        if (nameStr.contains("Dream")) return "\u00a73Dream Horn \u2014 \u00a73\u00a7oAn ethereal whisper... \u00a76Fire Resistance for 4 turns \u00a78(AP: 2)";
        return null;
    }

    private static String getTrimPerPieceDescription(String patternId) {
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
            case "rib"       -> "+1 Special Power per piece";
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

    private static String getTrimSetBonusName(String patternId) {
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

    private static String getTrimSetBonusDescription(String patternId) {
        return switch (patternId) {
            case "sentry"    -> "Counter-attack ranged enemies that hit you";
            case "dune"      -> "Enemies within 2 tiles lose 1 Speed";
            case "coast"     -> "Water tiles heal 1 HP/turn";
            case "wild"      -> "Kill streak: 1.3x damage per streak level";
            case "ward"      -> "50% less damage when you didn't move this turn";
            case "eye"       -> "See all enemy stats and their next action";
            case "vex"       -> "20% chance to dodge incoming attacks";
            case "tide"      -> "Full heal when dropping below 25% HP (once per combat)";
            case "snout"     -> "Melee attacks splash to adjacent enemies";
            case "rib"       -> "Fire attacks deal +3 bonus damage";
            case "spire"     -> "Double emerald rewards";
            case "wayfinder" -> "Movement ignores obstacle tiles";
            case "shaper"    -> "Place a free barrier block each turn";
            case "silence"   -> "Invisible for first 2 turns (enemies don't act)";
            case "raiser"    -> "Tamed allies get +2 Speed and +1 Attack";
            case "host"      -> "Heal 1 HP for each enemy killed";
            case "flow"      -> "Killing an enemy refunds 1 AP";
            case "bolt"      -> "Critical hits stun the target for 1 turn";
            default -> "";
        };
    }

    private static String getTooltipFor(Item item) {
        // ── Weapons ──
        if (item == Items.WOODEN_SWORD) return "\u00a7c3 DMG \u00a77| Range 1 | 1 AP | \u00a7cSlashing\n\u00a7e\u2694 Sweep: \u00a7710% chance to hit adjacent enemy";
        if (item == Items.STONE_SWORD) return "\u00a7c4 DMG \u00a77| Range 1 | 1 AP | \u00a7cSlashing\n\u00a7e\u2694 Sweep: \u00a7710% chance to hit adjacent enemy";
        if (item == Items.IRON_SWORD) return "\u00a7c5 DMG \u00a77| Range 1 | 1 AP | \u00a7cSlashing\n\u00a7e\u2694 Sweep: \u00a7710% chance to hit adjacent enemy";
        if (item == Items.DIAMOND_SWORD) return "\u00a7c6 DMG \u00a77| Range 1 | 1 AP | \u00a7cSlashing\n\u00a7e\u2694 Sweep + \u00a7630% Crit \u00a77(double damage)";
        if (item == Items.GOLDEN_SWORD) return "\u00a7c3 DMG \u00a77| Range 1 | 1 AP | \u00a7cSlashing\n\u00a7e\u2694 Sweep: \u00a7710% chance to hit adjacent enemy";
        if (item == Items.NETHERITE_SWORD) return "\u00a7c7 DMG \u00a77| Range 1 | 1 AP | \u00a7cSlashing\n\u00a7e\u2694 Sweep + \u00a74Execute \u00a77(3x if target <30% HP)";

        // Axes
        if (item == Items.WOODEN_AXE) return "\u00a7c4 DMG \u00a77| Range 1 | \u00a7c2 AP \u00a77| \u00a76Cleaving\n\u00a76\u2716 Armor Crush: \u00a775% chance to ignore armor";
        if (item == Items.STONE_AXE) return "\u00a7c5 DMG \u00a77| Range 1 | \u00a7c2 AP \u00a77| \u00a76Cleaving\n\u00a76\u2716 Armor Crush: \u00a775% chance to ignore armor";
        if (item == Items.IRON_AXE) return "\u00a7c6 DMG \u00a77| Range 1 | \u00a7c2 AP \u00a77| \u00a76Cleaving\n\u00a76\u2716 Armor Crush: \u00a775% chance to ignore armor";
        if (item == Items.DIAMOND_AXE) return "\u00a7c7 DMG \u00a77| Range 1 | \u00a7c2 AP \u00a77| \u00a76Cleaving\n\u00a76\u2716 Armor Crush: \u00a775% chance to ignore armor";
        if (item == Items.GOLDEN_AXE) return "\u00a7c4 DMG \u00a77| Range 1 | \u00a7c2 AP \u00a77| \u00a76Cleaving\n\u00a76\u2716 Armor Crush: \u00a775% chance to ignore armor";
        if (item == Items.NETHERITE_AXE) return "\u00a7c7 DMG \u00a77| Range 1 | \u00a7c2 AP \u00a77| \u00a76Cleaving\n\u00a76\u2716 Armor Crush: \u00a775% chance to ignore armor";

        // Mace
        if (item == Items.MACE) return "\u00a7c7 DMG \u00a77| Range 1 | \u00a7c2 AP \u00a77| \u00a78Blunt\n\u00a76AoE: \u00a77Half damage to all in 3x3\n\u00a76Knockback: \u00a77Pushes target back\n\u00a7dDensity: \u00a77+AoE dmg | \u00a74Breach: \u00a77Armor pen | \u00a7bWind Burst: \u00a77+KB range";
        if (item == Items.STICK) return "\u00a7c2 DMG \u00a77| Range 1 | 1 AP | \u00a78Blunt\n\u00a78\u2716 Stun: \u00a775% chance to stun target\n\u00a7c\u26A0 10% break chance per attack";
        if (item == Items.BAMBOO) return "\u00a7c3 DMG \u00a77| Range 1 | 1 AP | \u00a78Blunt\n\u00a78\u2716 Stun: \u00a775% chance to stun target\n\u00a7c\u26A0 5% break chance per attack";
        if (item == Items.BLAZE_ROD) return "\u00a7c4 DMG \u00a77| Range 1 | 1 AP | \u00a78Blunt\n\u00a76\u2716 Fire: \u00a77+1 fire dmg | \u00a78Stun: \u00a775% chance\n\u00a7e\u26A0 1% break chance per attack";
        if (item == Items.BREEZE_ROD) return "\u00a7c4 DMG \u00a77| Range 1 | 1 AP | \u00a78Blunt\n\u00a7b\u2716 Knockback: \u00a77Push back 1 | \u00a78Stun: \u00a775% chance\n\u00a7e\u26A0 1% break chance per attack";

        // Hoes — Special type (low damage, effects/utility)
        if (item == Items.WOODEN_HOE) return "\u00a7c1 DMG \u00a77| Range 1 | 1 AP | \u00a7dSpecial\n\u00a77Weak but channels special energy";
        if (item == Items.STONE_HOE) return "\u00a7c1 DMG \u00a77| Range 1 | 1 AP | \u00a7dSpecial\n\u00a77Weak but channels special energy";
        if (item == Items.IRON_HOE) return "\u00a7c2 DMG \u00a77| Range 1 | 1 AP | \u00a7dSpecial\n\u00a7d\u2728 Special weapon: \u00a77Low damage, boosted by Special affinity";
        if (item == Items.GOLDEN_HOE) return "\u00a7c2 DMG \u00a77| Range 1 | 1 AP | \u00a7dSpecial\n\u00a7d\u2728 Special weapon: \u00a77Enchanted gold channels power";
        if (item == Items.DIAMOND_HOE) return "\u00a7c3 DMG \u00a77| Range 1 | 1 AP | \u00a7dSpecial\n\u00a7d\u2728 Special weapon: \u00a77Strong special conduit";
        if (item == Items.NETHERITE_HOE) return "\u00a7c3 DMG \u00a77| Range 1 | 1 AP | \u00a7dSpecial\n\u00a7d\u2728 Special weapon: \u00a77Ultimate special conduit";

        // Shovels — Pet type (boosted by Pet affinity)
        if (item == Items.WOODEN_SHOVEL) return "\u00a7c2 DMG \u00a77| Range 1 | 1 AP | \u00a7aPet\n\u00a7a\uD83D\uDC3E Pet weapon: \u00a77Boosted by Pet affinity";
        if (item == Items.STONE_SHOVEL) return "\u00a7c3 DMG \u00a77| Range 1 | 1 AP | \u00a7aPet\n\u00a7a\uD83D\uDC3E Pet weapon: \u00a77Solid companion blade";
        if (item == Items.IRON_SHOVEL) return "\u00a7c4 DMG \u00a77| Range 1 | 1 AP | \u00a7aPet\n\u00a7a\uD83D\uDC3E Pet weapon: \u00a77Reliable pet synergy";
        if (item == Items.GOLDEN_SHOVEL) return "\u00a7c3 DMG \u00a77| Range 1 | 1 AP | \u00a7aPet\n\u00a7a\uD83D\uDC3E Pet weapon: \u00a77Golden beast bond";
        if (item == Items.DIAMOND_SHOVEL) return "\u00a7c5 DMG \u00a77| Range 1 | 1 AP | \u00a7aPet\n\u00a7a\uD83D\uDC3E Pet weapon: \u00a77Strong pet synergy";
        if (item == Items.NETHERITE_SHOVEL) return "\u00a7c6 DMG \u00a77| Range 1 | 1 AP | \u00a7aPet\n\u00a7a\uD83D\uDC3E Pet weapon: \u00a77Ultimate beastmaster blade";

        // Ranged
        if (item == Items.BOW) return "\u00a7c5 DMG \u00a77| \u00a7bRange 3 \u00a77| 1 AP | \u00a7bRanged\n\u00a77Consumes arrows. Tipped arrows apply effects.\n\u00a7dPower: \u00a77+1 DMG/lvl | \u00a76Flame: \u00a77Ignite | \u00a7eInfinity: \u00a77Free ammo";
        if (item == Items.CROSSBOW) return "\u00a7c6 DMG \u00a77| \u00a7bRange 4 \u00a77| \u00a7c4 AP \u00a77| \u00a7bRanged\n\u00a7bPierce: \u00a77Bolt hits targets behind for 50%\n\u00a77Consumes arrows. Tipped arrows apply effects.";
        if (item == Items.TRIDENT) return "\u00a7c8 DMG \u00a77| \u00a73Water\n\u00a77Melee (1 AP) when adjacent.\n\u00a77Throw (2 AP) in straight/diagonal lines.\n\u00a77Thrown trident lodges in ground \u2014 retrieve manually.\n\u00a7bLoyalty: \u00a77auto-returns | \u00a73Riptide: \u00a77dash | \u00a7eChanneling: \u00a77lightning";

        // ── Food ──
        if (item == Items.APPLE) return "\u00a7a+2 HP \u00a77| 1 AP";
        if (item == Items.BREAD) return "\u00a7a+3 HP \u00a77| 1 AP";
        if (item == Items.COOKED_BEEF) return "\u00a7a+5 HP \u00a77| 1 AP";
        if (item == Items.COOKED_PORKCHOP) return "\u00a7a+5 HP \u00a77| 1 AP";
        if (item == Items.COOKED_CHICKEN) return "\u00a7a+3 HP \u00a77| 1 AP";
        if (item == Items.COOKED_MUTTON) return "\u00a7a+4 HP \u00a77| 1 AP";
        if (item == Items.COOKED_COD) return "\u00a7a+3 HP \u00a77| 1 AP";
        if (item == Items.COOKED_SALMON) return "\u00a7a+4 HP \u00a77| 1 AP";
        if (item == Items.BAKED_POTATO) return "\u00a7a+3 HP \u00a77| 1 AP";
        if (item == Items.COOKIE) return "\u00a7a+1 HP \u00a77| 1 AP";
        if (item == Items.PUMPKIN_PIE) return "\u00a7a+4 HP \u00a77| 1 AP";
        if (item == Items.MELON_SLICE) return "\u00a7a+1 HP \u00a77| 1 AP";
        if (item == Items.SWEET_BERRIES) return "\u00a7a+1 HP \u00a77| 1 AP";
        if (item == Items.GLOW_BERRIES) return "\u00a7a+1 HP \u00a77| 1 AP";
        if (item == Items.GOLDEN_CARROT) return "\u00a7a+4 HP \u00a77| 1 AP";
        if (item == Items.GOLDEN_APPLE) return "\u00a7a+8 HP \u00a77| 1 AP | \u00a7e+Absorption";
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return "\u00a7a+FULL HP \u00a77| 1 AP\n\u00a7e+Absorption IV +Resistance +Regen II";
        if (item == Items.HONEY_BOTTLE) return "\u00a7a+3 HP \u00a77| 1 AP";
        if (item == Items.SUSPICIOUS_STEW) return "\u00a7a+4 HP \u00a77| 1 AP";
        if (item == Items.CHORUS_FRUIT) return "\u00a7a+2 HP \u00a77| 1 AP";
        if (item == Items.DRIED_KELP) return "\u00a7a+1 HP \u00a77| 1 AP";
        if (item == Items.MUSHROOM_STEW) return "\u00a7a+4 HP \u00a77| 1 AP";
        if (item == Items.BEETROOT_SOUP) return "\u00a7a+4 HP \u00a77| 1 AP";
        if (item == Items.RABBIT_STEW) return "\u00a7a+6 HP \u00a77| 1 AP \u00a77(Best food!)";
        // Raw meats
        if (item == Items.BEEF) return "\u00a7a+2 HP \u00a77| 1 AP \u00a78(Raw)";
        if (item == Items.PORKCHOP) return "\u00a7a+2 HP \u00a77| 1 AP \u00a78(Raw)";
        if (item == Items.CHICKEN) return "\u00a7a+1 HP \u00a77| 1 AP \u00a78(Raw)";
        if (item == Items.MUTTON) return "\u00a7a+2 HP \u00a77| 1 AP \u00a78(Raw)";
        if (item == Items.COD) return "\u00a7a+1 HP \u00a77| 1 AP \u00a78(Raw)";
        if (item == Items.SALMON) return "\u00a7a+1 HP \u00a77| 1 AP \u00a78(Raw)";
        if (item == Items.RABBIT) return "\u00a7a+2 HP \u00a77| 1 AP \u00a78(Raw)";
        if (item == Items.COOKED_RABBIT) return "\u00a7a+3 HP \u00a77| 1 AP";
        if (item == Items.TROPICAL_FISH) return "\u00a7a+1 HP \u00a77| 1 AP \u00a78(Raw)";
        if (item == Items.POTATO) return "\u00a7a+1 HP \u00a77| 1 AP \u00a78(Raw)";
        if (item == Items.CARROT) return "\u00a7a+2 HP \u00a77| 1 AP";
        if (item == Items.BEETROOT) return "\u00a7a+1 HP \u00a77| 1 AP";
        // Risky foods
        if (item == Items.POISONOUS_POTATO) return "\u00a7a+1 HP \u00a77| 1 AP \u00a7c(Risky!)";
        if (item == Items.SPIDER_EYE) return "\u00a7a+1 HP \u00a77| 1 AP \u00a7c(Risky!)";
        if (item == Items.ROTTEN_FLESH) return "\u00a7a+2 HP \u00a77| 1 AP \u00a7c(Risky!)";
        if (item == Items.PUFFERFISH) return "\u00a7b1 AP \u00a77- Water AoE throwable (Tier 2)\n\u00a7c3 DMG \u00a77| Radius 2 | \u00a73Water\n\u00a73Soaked II \u00a77+ \u00a72Poison I";

        // ── Potions ──
        // Potions handled dynamically in getTooltip — these are fallbacks for potions without POTION_CONTENTS
        if (item == Items.POTION) return null;
        if (item == Items.SPLASH_POTION) return null;
        if (item == Items.LINGERING_POTION) return null;

        // ── Throwables ──
        if (item == Items.SNOWBALL) return "\u00a7b1 AP \u00a77- Throw at enemy\n\u00a7bKnockback: \u00a77Push enemy 1 tile away";
        if (item == Items.EGG) return "\u00a7b1 AP \u00a77- Throw at enemy\n\u00a7c1 DMG \u00a77+ minor annoyance";
        if (item == Items.ENDER_PEARL) return "\u00a751 AP \u00a77- Click any empty tile\n\u00a75Teleport instantly! \u00a7c(Costs 2 HP)";
        if (item == Items.FIRE_CHARGE) return "\u00a761 AP \u00a77- Ranged fire attack\n\u00a7c4 DMG \u00a77+ sets enemy on fire";

        // ── Water AoE Throwables ──
        if (item == Items.TURTLE_EGG) return "\u00a7b1 AP \u00a77- Water AoE throwable (Tier 1)\n\u00a7c2 DMG \u00a77| Radius 1 | \u00a73Water\n\u00a73Soaked I";
        if (item == Items.NAUTILUS_SHELL) return "\u00a7b1 AP \u00a77- Water AoE throwable (Tier 3)\n\u00a7c4 DMG \u00a77| Radius 2 | \u00a73Water\n\u00a73Soaked III \u00a77+ \u00a7dConfusion I";
        if (item == Items.HEART_OF_THE_SEA) return "\u00a7b1 AP \u00a77- Water AoE throwable (Tier 4)\n\u00a7c5 DMG \u00a77| Radius 3 | \u00a73Water\n\u00a73Soaked IV \u00a77+ \u00a7dConfusion II";

        // ── Coral Weapons (Water-type melee) ──
        if (item == Items.TUBE_CORAL) return "\u00a7c3 DMG \u00a77| Range 1 | 1 AP | \u00a73Water\n\u00a73\u2716 Soaked: \u00a77-1 Speed, 2x lightning dmg (1 turn)\n\u00a7e\u26A0 1% break chance per attack";
        if (item == Items.BRAIN_CORAL) return "\u00a7c5 DMG \u00a77| Range 1 | 1 AP | \u00a73Water\n\u00a7d\u2716 Confuse: \u00a7740% chance enemy attacks allies\n\u00a7e\u26A0 1% break chance per attack";
        if (item == Items.BUBBLE_CORAL) return "\u00a7c3 DMG \u00a77| Range 1 | 1 AP | \u00a73Water\n\u00a7b\u2716 Knockback: \u00a77Bubble burst pushes enemy 1 tile\n\u00a7e\u26A0 1% break chance per attack";
        if (item == Items.FIRE_CORAL) return "\u00a7c7 DMG \u00a77| Range 1 | 1 AP | \u00a73Water\n\u00a76\u2716 Searing: \u00a77+3 bonus DMG to burning enemies\n\u00a7e\u26A0 1% break chance per attack";
        if (item == Items.HORN_CORAL) return "\u00a7c6 DMG \u00a77| Range 1 | 1 AP | \u00a73Water\n\u00a7e\u2716 Pierce: \u00a77Ignores 3 armor for 1 turn\n\u00a7e\u26A0 1% break chance per attack";
        // Dead corals
        if (item == Items.DEAD_TUBE_CORAL || item == Items.DEAD_BRAIN_CORAL
            || item == Items.DEAD_BUBBLE_CORAL || item == Items.DEAD_FIRE_CORAL
            || item == Items.DEAD_HORN_CORAL
            || item == Items.DEAD_TUBE_CORAL_FAN || item == Items.DEAD_BRAIN_CORAL_FAN
            || item == Items.DEAD_BUBBLE_CORAL_FAN || item == Items.DEAD_FIRE_CORAL_FAN
            || item == Items.DEAD_HORN_CORAL_FAN)
            return "\u00a7c2 DMG \u00a77| Range 1 | 1 AP | \u00a73Water\n\u00a77\u2716 Weakened: \u00a77Saps enemy ATK by 2 for 1 turn\n\u00a7c\u26A0 5% break chance per attack";
        // Coral fans
        if (item == Items.TUBE_CORAL_FAN || item == Items.BRAIN_CORAL_FAN
            || item == Items.BUBBLE_CORAL_FAN || item == Items.FIRE_CORAL_FAN
            || item == Items.HORN_CORAL_FAN)
            return "\u00a7c1 DMG \u00a77| Range 1 | 1 AP | \u00a73Water\n\u00a73\u2716 Splash: \u00a77Hits all enemies adjacent to target\n\u00a7e\u26A0 3% break chance per attack";

        // ── Utility Items ──
        if (item == Items.SHIELD) return "\u00a79Passive: \u00a77+1 DEF when in offhand\n\u00a79Block: \u00a7725% chance to fully block an attack\n\u00a77No AP cost \u2014 equip in offhand slot";
        if (item == Items.TOTEM_OF_UNDYING) return "\u00a76Passive: \u00a77Auto-activates on fatal hit\n\u00a77Restores 50% HP + Regen II\n\u00a77Consumed from inventory automatically";
        if (item == Items.MILK_BUCKET) return "\u00a7f1 AP \u00a77- Clears ALL status effects\n\u00a77Good and bad effects removed. Returns bucket.";
        if (item == Items.TNT) return "\u00a7c1 AP \u00a77- Place TNT on target tile\n\u00a7eExplodes next round!\n\u00a7c8/5/3 DMG \u00a77in AoE (distance-based)\n\u00a7cSelf-damage if within 2 tiles!";
        if (item == Items.COBWEB) return "\u00a771 AP \u00a77- Throw at enemy\n\u00a77Stuns target \u2014 they skip next turn";
        if (item == Items.FLINT_AND_STEEL) return "\u00a761 AP \u00a77- Set enemy on fire\n\u00a7c2 DMG \u00a77+ burns for 5 seconds\n\u00a77Uses durability";
        if (item == Items.FISHING_ROD) return "\u00a7b3 AP \u00a77- Cast into adjacent water tile\n\u00a77Random loot! Fish, treasure, rare items\n\u00a77Must stand next to water";
        if (item == Items.SADDLE) return "\u00a7e\u00a7lMount Item\n\u00a77Tame a horse/donkey/camel with its\n\u00a77breeding item + saddle for +3 Speed!";
        if (item == Items.SPYGLASS) return "\u00a7e1 AP \u00a77- Target an enemy\n\u00a77Reveals HP, ATK, DEF, Range, Speed";
        if (item == Items.COMPASS) return "\u00a761 AP \u00a77- Reveals all enemy positions\n\u00a77Shows grid coordinates of every enemy";
        if (item == Items.RECOVERY_COMPASS) return "\u00a76Passive: \u00a77Consumed on death\n\u00a77Saves your full inventory one time instead of losing it";
        if (item == Items.BELL) return "\u00a762 AP \u00a77- Ring at target tile\n\u00a77Stuns ALL enemies within 2 tiles";
        if (item == Items.ANVIL) return "\u00a781 AP \u00a77- Drop on enemy\n\u00a7c5 DMG \u00a77| Consumed on use";
        if (item == Items.HONEY_BLOCK) return "\u00a7e1 AP \u00a77- Place sticky trap\n\u00a77Enemies that step on it lose all movement";
        if (item == Items.POWDER_SNOW_BUCKET) return "\u00a7b1 AP \u00a77- Freeze an enemy\n\u00a7c1 DMG \u00a77+ stun (skip next turn)";
        if (item == Items.JUKEBOX) return "\u00a7d2 AP \u00a77- Play music\n\u00a77Buffs all ally pets +1 Speed\n\u00a77Consumed on use";
        if (item == Items.GOAT_HORN) return "\u00a7c1 AP \u00a77- Taunt all enemies\n\u00a77All enemies will target you next turn\n\u00a77Draw aggro from low-HP allies";
        if (item == Items.ECHO_SHARD) return "\u00a751 AP \u00a77- Echo teleport\n\u00a77Return to start-of-turn position\n\u00a77Consumed on use";
        if (item == Items.BRUSH) return "\u00a7e1 AP \u00a77- Excavate adjacent tile\n\u00a77Dig up random loot (gold, gems, etc.)\n\u00a77Uses durability";
        if (item == Items.LANTERN) return "\u00a7e1 AP \u00a77- Place light source\n\u00a77Reveals hidden/invisible enemies in 3 tiles";
        if (item == Items.LIGHTNING_ROD) return "\u00a7e1 AP \u00a77- Place on tile\n\u00a77Strikes next turn: 4 DMG to all within 1 tile\n\u00a77Consumed after striking";
        if (item == Items.CACTUS) return "\u00a721 AP \u00a77- Place wall trap\n\u00a77Pricks adjacent enemies for 1 DMG/turn";
        if (item == Items.CAMPFIRE) return "\u00a761 AP \u00a77- Place healing zone\n\u00a77Heals 1 HP/turn when adjacent to it";
        if (item == Items.SCAFFOLDING) return "\u00a7a1 AP \u00a77- Place elevated tile\n\u00a77+1 range for ranged attacks from this tile";
        if (item == Items.CAKE) return "\u00a7d1 AP \u00a77- Place healing tile\n\u00a77Heals 2 HP when stepped on (3 uses)";
        if (item == Items.SPORE_BLOSSOM) return "\u00a7d1 AP \u00a77- AoE slow cloud\n\u00a77Enemies within 3 tiles get -1 Speed";
        if (item == Items.HAY_BLOCK) return "\u00a7a1 AP \u00a77- Throw to ally pet\n\u00a77Heals ally 4 HP";

        // Terrain manipulation
        if (item == Items.WATER_BUCKET) return "\u00a7b1 AP \u00a77- Place water tile\n\u00a77Creates fishable/boat-traversable water";
        if (item == Items.SPONGE) return "\u00a7e1 AP \u00a77- Absorb adjacent water\n\u00a77Removes a water tile next to you";
        if (item == Items.LAVA_BUCKET) return "\u00a761 AP \u00a77- Place lava on tile\n\u00a77Enemies on it take 3 fire DMG/turn";

        // Pickaxes
        if (item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.IRON_PICKAXE
            || item == Items.DIAMOND_PICKAXE || item == Items.GOLDEN_PICKAXE || item == Items.NETHERITE_PICKAXE)
            return "\u00a771 AP \u00a77- Break adjacent obstacle\n\u00a77Makes blocked tile walkable\n\u00a77Uses durability";

        // ── Armor ──
        // Leather
        if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE
            || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS)
            return "\u00a7eSet Bonus (full set): Brawler\n\u00a77+2 Physical dmg\n\u00a7cKill streak: +30% dmg per kill (max 3 stacks)\n\u00a77Type Affinity: \u00a77+2 Physical Power";
        // Chainmail
        if (item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE
            || item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS)
            return "\u00a77Set Bonus (full set): Rogue\n\u00a77+1 Speed, attacks cost -1 AP (min 1)\n\u00a7cType Affinity: \u00a77+2 Slashing Power";
        // Iron
        if (item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE
            || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS)
            return "\u00a7fSet Bonus (full set): Guard\n\u00a77+2 Defense, knockback immune\n\u00a76Type Affinity: \u00a77+2 Cleaving Power";
        // Gold
        if (item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE
            || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS)
            return "\u00a76Set Bonus (full set): Gambler\n\u00a77+15% crit chance, +1 emerald/kill\n\u00a75Type Affinity: \u00a77+2 Special Power";
        // Diamond
        if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
            || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS)
            return "\u00a7bSet Bonus (full set): Knight\n\u00a77+3 Defense, +1 Attack\n\u00a78Type Affinity: \u00a77+2 Blunt Power";
        // Netherite
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE
            || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS)
            return "\u00a74Set Bonus (full set): Juggernaut\n\u00a77+4 Defense, +2 Attack, fire immune\n\u00a7eType Affinity: \u00a77+1 All Damage Types";
        // Turtle
        if (item == Items.TURTLE_HELMET)
            return "\u00a72Set Bonus: Aquatic\n\u00a77Water tiles walkable, +1 HP regen/turn\n\u00a77+1 Range when standing on water\n\u00a73Type Affinity: \u00a77+3 Water Power";

        // Horse armor
        if (item == Items.LEATHER_HORSE_ARMOR) return "\u00a7ePet Armor: \u00a77+1 DEF for tamed mount";
        if (item == Items.IRON_HORSE_ARMOR) return "\u00a7fPet Armor: \u00a77+2 DEF for tamed mount";
        if (item == Items.GOLDEN_HORSE_ARMOR) return "\u00a76Pet Armor: \u00a77+1 DEF, +1 ATK for tamed mount";
        if (item == Items.DIAMOND_HORSE_ARMOR) return "\u00a7bPet Armor: \u00a77+3 DEF, +1 ATK for tamed mount";
        if (item == Items.WOLF_ARMOR) return "\u00a7ePet Armor: \u00a77+2 DEF, +1 ATK for tamed wolf";

        // ── Breeding/Taming Items ──
        if (item == Items.BONE) return "\u00a7a1 AP \u00a77- Use on wolf to tame\n\u00a77Tamed wolves fight alongside you!";
        if (item == Items.WHEAT) return "\u00a7a1 AP \u00a77- Feed to cow/sheep/goat\n\u00a77Passive mobs: befriend and send to hub";
        if (item == Items.WHEAT_SEEDS) return "\u00a7a1 AP \u00a77- Feed to chicken/parrot\n\u00a77Passive mobs: befriend and send to hub";

        // ── Arrows ──
        if (item == Items.ARROW) return "\u00a78Ammo for Bow and Crossbow\n\u00a77Consumed per shot (Infinity skips)";
        // Tipped arrows handled dynamically in getTooltip
        if (item == Items.TIPPED_ARROW) return null;
        if (item == Items.SPECTRAL_ARROW) return "\u00a78WIP \u00a77- Not yet implemented in combat";

        // ── Boats ──
        if (item == Items.OAK_BOAT || item == Items.SPRUCE_BOAT || item == Items.BIRCH_BOAT
            || item == Items.JUNGLE_BOAT || item == Items.ACACIA_BOAT || item == Items.DARK_OAK_BOAT
            || item == Items.MANGROVE_BOAT || item == Items.CHERRY_BOAT || item == Items.BAMBOO_RAFT)
            return "\u00a7bWater Travel \u00a77- Consumed on first water entry\n\u00a77Allows crossing water tiles during combat";

        // ── Banners ──
        if (item.toString().contains("banner"))
            return "\u00a751 AP \u00a77- Plant defense zone\n\u00a77+2 DEF for player/allies within 2 tiles";

        // ── Move item ──
        if (item == Items.FEATHER) return "\u00a7aSelect to enter Move Mode\n\u00a77Click tiles to move your character";

        // ── Trial/Event items ──
        if (item == Items.TRIAL_KEY) return "\u00a76Trial Chamber reward\n\u00a77Rare drop from trial chamber events";
        if (item == Items.OMINOUS_TRIAL_KEY) return "\u00a74Ominous Trial Chamber reward\n\u00a77Legendary drop from trial chambers";
        if (item == Items.BREEZE_ROD) return "\u00a7bBreeze drop\n\u00a77Crafting material from trial chambers";
        if (item == Items.HEAVY_CORE) return "\u00a78Mace crafting component\n\u00a77Rare trial chamber drop";
        if (item == Items.WIND_CHARGE) return "\u00a78WIP \u00a77- Not yet implemented in combat";

        // ── Enchanting related ──
        // Enchanted books handled dynamically below
        // if (item == Items.ENCHANTED_BOOK) — removed, uses per-enchant tooltips now

        // ── Emerald (currency) ──
        if (item == Items.EMERALD) return "\u00a72Craftics Currency\n\u00a77Spend at wandering traders between levels";

        // ── Guide Book ──
        if (item.toString().contains("guide_book")) return "\u00a72Right-click or press G to open\n\u00a77Contains combat guides, enemy bestiary, tips";

        // ── Pottery Sherd Spells ──
        if (item == Items.EXPLORER_POTTERY_SHERD) return "\u00a7d[2 AP] Phase Step \u00a77\u2014 Teleport 4 tiles + reveal enemy stats";
        if (item == Items.FRIEND_POTTERY_SHERD) return "\u00a7d[2 AP] Guardian Spirit \u00a77\u2014 Heal 5 HP, buff ally pet (+3 ATK)";
        if (item == Items.HEART_POTTERY_SHERD) return "\u00a7d[3 AP] Mending Light \u00a77\u2014 Heal 10 HP + Regen II (3 turns)";
        if (item == Items.SCRAPE_POTTERY_SHERD) return "\u00a7d[3 AP] Corrode \u00a77\u2014 3 dmg + reduce DEF by 5 (3 turns)";
        if (item == Items.ANGLER_POTTERY_SHERD) return "\u00a73[3 AP] Riptide Hook \u00a77\u2014 Pull 2 tiles + 4 dmg (+3 if adjacent)";
        if (item == Items.HEARTBREAK_POTTERY_SHERD) return "\u00a7d[3 AP] Shatter Will \u00a77\u2014 3 dmg + -4 ATK, -3 SPD (2 turns)";
        if (item == Items.SHEAF_POTTERY_SHERD) return "\u00a72[3 AP] Entangle \u00a77\u2014 Stun target + slow nearby enemies";
        if (item == Items.MINER_POTTERY_SHERD) return "\u00a78[3 AP] Earthen Spike \u00a77\u2014 7 BLUNT dmg (+4 near obstacle)";
        if (item == Items.DANGER_POTTERY_SHERD) return "\u00a7d[3 AP] Hex Trap \u00a77\u2014 Invisible trap: 8 dmg + stun on trigger";
        if (item == Items.BLADE_POTTERY_SHERD) return "\u00a7d[4 AP] Phantom Slash \u00a77\u2014 8 dmg + 5 cleave to adjacent enemy";
        if (item == Items.BURN_POTTERY_SHERD) return "\u00a76[4 AP] Immolation \u00a77\u2014 6 fire + burn (3t), splash 3 dmg + burn";
        if (item == Items.SNORT_POTTERY_SHERD) return "\u00a78[4 AP] Tectonic Charge \u00a77\u2014 KB 3 tiles, 3 dmg/tile, wall slam +6";
        if (item == Items.SHELTER_POTTERY_SHERD) return "\u00a77[4 AP] Stone Aegis \u00a77\u2014 Resistance II (4t) + Absorption II (3t)";
        if (item == Items.FLOW_POTTERY_SHERD) return "\u00a73[4 AP] Tidal Surge \u00a77\u2014 5 WATER dmg + KB 2 to all within 2 tiles";
        if (item == Items.MOURNER_POTTERY_SHERD) return "\u00a75[4 AP] Soul Drain \u00a77\u2014 7 dmg, heal for damage dealt";
        if (item == Items.BREWER_POTTERY_SHERD) return "\u00a7d[4 AP] Alchemist's Surge \u00a77\u2014 4 random buffs (3 turns each)";
        if (item == Items.PLENTY_POTTERY_SHERD) return "\u00a7a[4 AP] Bountiful Harvest \u00a77\u2014 Heal 10 HP + restore 5 AP";
        if (item == Items.ARCHER_POTTERY_SHERD) return "\u00a7b[5 AP] Spectral Volley \u00a77\u2014 7 RANGED dmg + 4 AoE splash";
        if (item == Items.HOWL_POTTERY_SHERD) return "\u00a77[5 AP] Dread Howl \u00a77\u2014 4 dmg + stun all within 3 tiles";
        if (item == Items.ARMS_UP_POTTERY_SHERD) return "\u00a76[5 AP] War Cry \u00a77\u2014 STR III (+9 ATK) + SPD II (3 turns)";
        if (item == Items.PRIZE_POTTERY_SHERD) return "\u00a76[5 AP] Fortune's Favor \u00a77\u2014 Next attack = TRIPLE damage + Luck II";
        if (item == Items.SKULL_POTTERY_SHERD) return "\u00a74[6 AP] Death Mark \u00a77\u2014 Execute <40% HP or 5 dmg + Wither III";
        if (item == Items.GUSTER_POTTERY_SHERD) return "\u00a7e[4 AP] Chain Lightning \u00a77\u2014 8 dmg, chains within 2 tiles (2x on Soaked)";

        return null;
    }

    private static String getMaterialDescription(String materialId) {
        return switch (materialId) {
            case "iron"      -> "+1 Defense per piece";
            case "copper"    -> "+1 Speed per piece";
            case "gold"      -> "+1 Luck per piece";
            case "lapis"     -> "+1 Special Power per piece";
            case "emerald"   -> "+1 AP per piece";
            case "diamond"   -> "+1 Melee Power per piece";
            case "netherite" -> "+1 Armor Penetration per piece";
            case "redstone"  -> "+1 Ranged Power per piece";
            case "amethyst"  -> "+1 HP Regen per piece";
            case "quartz"    -> "+2 Max HP per piece";
            default -> "";
        };
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register(new CombatTooltips());
    }
}
