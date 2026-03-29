package com.crackedgames.craftics.client;

import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.TrimEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders a damage type affinity panel on the left side of the inventory.
 * Shows bar graphs for each damage type based on current equipment bonuses.
 */
public class DamageTypePanel {

    // ARGB colors for each damage type bar (matching DamageType color codes)
    private static final int COLOR_SWORD    = 0xFFFF5555; // red
    private static final int COLOR_CLEAVING = 0xFFFFAA00; // orange
    private static final int COLOR_BLUNT    = 0xFF888888; // gray
    private static final int COLOR_WATER    = 0xFF5555FF; // blue
    private static final int COLOR_MAGIC    = 0xFFFF55FF; // pink
    private static final int COLOR_PET      = 0xFF55FF55; // green
    private static final int COLOR_RANGED   = 0xFF55FFFF; // cyan
    private static final int COLOR_PHYSICAL = 0xFFAAAAAA; // light gray

    // Cached partial set bonuses for mixed armor (computed each frame in render)
    private static Map<DamageType, Integer> cachedPartialBonuses = new HashMap<>();

    public static void render(DrawContext ctx, int screenWidth, int screenHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        TextRenderer tr = client.textRenderer;

        // Compute bonuses
        String armorSet = getArmorSet(player);
        Map<String, Integer> trimBonuses = scanTrimBonuses(player);

        // Compute partial set bonuses for mixed armor (2+ pieces of same material = +1)
        cachedPartialBonuses.clear();
        if ("mixed".equals(armorSet)) {
            Map<String, Integer> materialCounts = new HashMap<>();
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                String mat = getArmorMaterial(player.getEquippedStack(slot).getItem());
                if (mat != null) materialCounts.merge(mat, 1, Integer::sum);
            }
            for (var entry : materialCounts.entrySet()) {
                if (entry.getValue() >= 2) {
                    DamageType affinity = switch (entry.getKey()) {
                        case "leather" -> DamageType.PHYSICAL;
                        case "chainmail" -> DamageType.SWORD;
                        case "iron" -> DamageType.CLEAVING;
                        case "gold" -> DamageType.MAGIC;
                        case "diamond" -> DamageType.BLUNT;
                        default -> null;
                    };
                    if (affinity != null) cachedPartialBonuses.put(affinity, 1);
                }
            }
        }

        // Panel position: left side of inventory
        int panelW = 120;
        int panelH = 155;
        int panelX = (screenWidth / 2) - 90 - panelW - 6;
        int panelY = (screenHeight / 2) - 80;

        // Background
        ctx.fill(panelX - 4, panelY - 4, panelX + panelW + 4, panelY + panelH + 4, 0xCC000000);
        ctx.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0xCC1A1A2E);

        // Title
        ctx.drawTextWithShadow(tr, "\u00a76\u00a7l\u2694 Damage Affinities", panelX, panelY, 0xFFFFAA00);
        panelY += 13;

        // Divider
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF444444);
        panelY += 4;

        // Armor set label
        String setName = getSetDisplayName(armorSet);
        if (!setName.isEmpty()) {
            ctx.drawTextWithShadow(tr, "\u00a77Set: " + setName, panelX, panelY, 0xFFAAAAAA);
            panelY += 11;
        }

        // Bar graph for each damage type
        int barMaxWidth = panelW - 4;
        int barHeight = 8;
        int lineHeight = 14;
        int maxBonus = 8; // max expected bonus for scaling bars

        DamageType[] types = { DamageType.SWORD, DamageType.CLEAVING, DamageType.BLUNT,
            DamageType.RANGED, DamageType.WATER, DamageType.MAGIC, DamageType.PHYSICAL, DamageType.PET };
        int[] colors = { COLOR_SWORD, COLOR_CLEAVING, COLOR_BLUNT,
            COLOR_RANGED, COLOR_WATER, COLOR_MAGIC, COLOR_PHYSICAL, COLOR_PET };

        for (int i = 0; i < types.length; i++) {
            DamageType type = types[i];
            int bonus = computeBonus(armorSet, trimBonuses, type);

            // Label
            String label = type.displayName;
            ctx.drawTextWithShadow(tr, label, panelX, panelY, colors[i]);

            // Bonus number on right
            if (bonus > 0) {
                String bonusStr = "+" + bonus;
                int bonusWidth = tr.getWidth(bonusStr);
                ctx.drawTextWithShadow(tr, bonusStr, panelX + panelW - bonusWidth, panelY, 0xFFFFFFFF);
            }

            panelY += 9;

            // Bar background
            ctx.fill(panelX, panelY, panelX + barMaxWidth, panelY + barHeight, 0xFF222222);

            // Bar fill
            if (bonus > 0) {
                int fillWidth = Math.min(barMaxWidth, (int)((bonus / (float) maxBonus) * barMaxWidth));
                fillWidth = Math.max(3, fillWidth); // minimum visible width
                int barColor = colors[i];
                ctx.fill(panelX, panelY, panelX + fillWidth, panelY + barHeight, barColor);
                // Shine on top pixel row
                ctx.fill(panelX, panelY, panelX + fillWidth, panelY + 1,
                    brighten(barColor, 60));
            }

            panelY += barHeight + (lineHeight - barHeight - 9) + 4;
        }
    }

    /** Compute total bonus for a damage type from armor set + trims + partial pieces (client-side). */
    private static int computeBonus(String armorSet, Map<String, Integer> trimBonuses, DamageType type) {
        int bonus = 0;

        // Full armor set bonus
        bonus += switch (armorSet) {
            case "leather"   -> type == DamageType.PHYSICAL ? 2 : 0;
            case "chainmail" -> type == DamageType.SWORD ? 2 : 0;
            case "iron"      -> type == DamageType.CLEAVING ? 2 : 0;
            case "gold"      -> type == DamageType.MAGIC ? 2 : 0;
            case "diamond"   -> type == DamageType.BLUNT ? 2 : 0;
            case "netherite" -> 1;
            case "turtle"    -> type == DamageType.WATER ? 3 : 0;
            default -> 0;
        };

        // Partial set bonus: 2+ pieces of same material gives +1 to its affinity type (even without full set)
        if ("mixed".equals(armorSet)) {
            bonus += cachedPartialBonuses.getOrDefault(type, 0);
        }

        // Trim bonuses
        String bonusKey = switch (type) {
            case SWORD    -> "SWORD_POWER";
            case CLEAVING -> "CLEAVING_POWER";
            case BLUNT    -> "BLUNT_POWER";
            case WATER    -> "WATER_POWER";
            case MAGIC    -> "MAGIC_POWER";
            case PET      -> "ALLY_DAMAGE";
            case RANGED   -> "RANGED_POWER";
            default       -> null;
        };
        if (bonusKey != null) {
            bonus += trimBonuses.getOrDefault(bonusKey, 0);
        }
        // Generic melee power stacks with melee types
        if (type == DamageType.SWORD || type == DamageType.CLEAVING || type == DamageType.BLUNT) {
            bonus += trimBonuses.getOrDefault("MELEE_POWER", 0);
        }

        // Affinity points from level-up choices
        int affinityOrdinal = switch (type) {
            case SWORD -> 0;
            case CLEAVING -> 1;
            case BLUNT -> 2;
            case RANGED -> 3;
            case WATER -> 4;
            case MAGIC -> 5;
            case PHYSICAL -> 6;
            case PET -> -1;
        };
        if (affinityOrdinal >= 0) {
            bonus += CombatState.getAffinityPoints(affinityOrdinal);
        }

        return bonus;
    }

    /** Detect armor set from client player (mirrors PlayerCombatStats.getArmorSet). */
    private static String getArmorSet(ClientPlayerEntity player) {
        Item head = player.getEquippedStack(EquipmentSlot.HEAD).getItem();
        Item chest = player.getEquippedStack(EquipmentSlot.CHEST).getItem();
        Item legs = player.getEquippedStack(EquipmentSlot.LEGS).getItem();
        Item feet = player.getEquippedStack(EquipmentSlot.FEET).getItem();

        if (head == Items.TURTLE_HELMET) return "turtle";
        if (head == Items.LEATHER_HELMET && chest == Items.LEATHER_CHESTPLATE
            && legs == Items.LEATHER_LEGGINGS && feet == Items.LEATHER_BOOTS) return "leather";
        if (head == Items.CHAINMAIL_HELMET && chest == Items.CHAINMAIL_CHESTPLATE
            && legs == Items.CHAINMAIL_LEGGINGS && feet == Items.CHAINMAIL_BOOTS) return "chainmail";
        if (head == Items.IRON_HELMET && chest == Items.IRON_CHESTPLATE
            && legs == Items.IRON_LEGGINGS && feet == Items.IRON_BOOTS) return "iron";
        if (head == Items.GOLDEN_HELMET && chest == Items.GOLDEN_CHESTPLATE
            && legs == Items.GOLDEN_LEGGINGS && feet == Items.GOLDEN_BOOTS) return "gold";
        if (head == Items.DIAMOND_HELMET && chest == Items.DIAMOND_CHESTPLATE
            && legs == Items.DIAMOND_LEGGINGS && feet == Items.DIAMOND_BOOTS) return "diamond";
        if (head == Items.NETHERITE_HELMET && chest == Items.NETHERITE_CHESTPLATE
            && legs == Items.NETHERITE_LEGGINGS && feet == Items.NETHERITE_BOOTS) return "netherite";
        return "mixed";
    }

    /** Scan trim bonuses from client player armor (mirrors TrimEffects.scan per-piece logic). */
    private static Map<String, Integer> scanTrimBonuses(ClientPlayerEntity player) {
        Map<String, Integer> bonuses = new HashMap<>();
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

        for (EquipmentSlot slot : slots) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;

            ArmorTrim trim = stack.get(DataComponentTypes.TRIM);
            if (trim == null) continue;

            // Pattern bonus
            RegistryEntry<ArmorTrimPattern> pattern = trim.getPattern();
            String patternId = pattern.getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            String bonusKey = getTrimBonusKey(patternId);
            if (bonusKey != null) {
                bonuses.merge(bonusKey, 1, Integer::sum);
            }

            // Material bonus
            String materialId = trim.getMaterial().getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            String matBonusKey = getMaterialBonusKey(materialId);
            if (matBonusKey != null) {
                bonuses.merge(matBonusKey, 1, Integer::sum);
            }
        }
        return bonuses;
    }

    /** Map trim pattern to bonus key (mirrors TrimEffects.getPerPieceBonus). */
    private static String getTrimBonusKey(String patternId) {
        return switch (patternId) {
            case "sentry"    -> "RANGED_POWER";
            case "dune"      -> "BLUNT_POWER";
            case "coast"     -> "WATER_POWER";
            case "wild"      -> "AP";
            case "ward"      -> "DEFENSE";
            case "eye"       -> "ATTACK_RANGE";
            case "vex"       -> "ARMOR_PEN";
            case "tide"      -> "REGEN";
            case "snout"     -> "CLEAVING_POWER";
            case "rib"       -> "MAGIC_POWER";
            case "spire"     -> "LUCK";
            case "wayfinder" -> "SPEED";
            case "shaper"    -> "DEFENSE";
            case "silence"   -> "STEALTH_RANGE";
            case "raiser"    -> "ALLY_DAMAGE";
            case "host"      -> "MAX_HP";
            case "flow"      -> "SPEED";
            case "bolt"      -> "SWORD_POWER";
            default -> null;
        };
    }

    /** Map trim material to bonus key (mirrors TrimEffects.getMaterialBonus). */
    private static String getMaterialBonusKey(String materialId) {
        return switch (materialId) {
            case "iron"      -> "DEFENSE";
            case "copper"    -> "SPEED";
            case "gold"      -> "LUCK";
            case "lapis"     -> "MAGIC_POWER";
            case "emerald"   -> "AP";
            case "diamond"   -> "MELEE_POWER";
            case "netherite" -> "ARMOR_PEN";
            case "redstone"  -> "RANGED_POWER";
            case "amethyst"  -> "REGEN";
            case "quartz"    -> "MAX_HP";
            default -> null;
        };
    }

    private static String getSetDisplayName(String armorSet) {
        return switch (armorSet) {
            case "leather"   -> "\u00a7eBrawler";
            case "chainmail" -> "\u00a77Rogue";
            case "iron"      -> "\u00a7fGuard";
            case "gold"      -> "\u00a76Gambler";
            case "diamond"   -> "\u00a7bKnight";
            case "netherite" -> "\u00a74Juggernaut";
            case "turtle"    -> "\u00a72Aquatic";
            default -> "";
        };
    }

    /** Detect which armor material a single piece belongs to (for partial set detection). */
    private static String getArmorMaterial(Item item) {
        if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE
            || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS) return "leather";
        if (item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE
            || item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS) return "chainmail";
        if (item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE
            || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS) return "iron";
        if (item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE
            || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS) return "gold";
        if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
            || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) return "diamond";
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE
            || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS) return "netherite";
        return null;
    }

    /** Brighten an ARGB color by adding to RGB channels. */
    private static int brighten(int argb, int amount) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
