package com.crackedgames.craftics.client;

import com.crackedgames.craftics.api.registry.HybridSetEntry;
import com.crackedgames.craftics.api.registry.HybridSetRegistry;
import com.crackedgames.craftics.combat.ArmorClassTable;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.PlayerProgression;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
//? if <=1.21.1 {
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.item.trim.ArmorTrimPattern;
//?} else {
/*import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
*///?}
import net.minecraft.registry.entry.RegistryEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders a damage type affinity panel on the left side of the inventory.
 * Shows bar graphs for each damage type based on current equipment bonuses.
 */
public class DamageTypePanel {

    // ARGB colors for each damage type bar (matching DamageType color codes)
    private static final int COLOR_SLASHING    = 0xFFFF5555; // red
    private static final int COLOR_CLEAVING = 0xFFFFAA00; // orange
    private static final int COLOR_BLUNT    = 0xFF888888; // gray
    private static final int COLOR_WATER    = 0xFF5555FF; // blue
    private static final int COLOR_SPECIAL    = 0xFFFF55FF; // pink
    private static final int COLOR_PET      = 0xFF55FF55; // green
    private static final int COLOR_RANGED   = 0xFF55FFFF; // cyan
    private static final int COLOR_PHYSICAL = 0xFFAAAAAA; // light gray

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static void render(DrawContext ctx, int screenWidth, int screenHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        TextRenderer tr = client.textRenderer;

        // Compute bonuses
        String armorSet = getArmorSet(player);
        Map<String, Integer> trimBonuses = scanTrimBonuses(player);

        // Per-piece armor affinity: count each worn piece by its armor-set material.
        // armorSetKeyOf normalizes the registry path (e.g. "golden" -> "gold").
        Map<String, Integer> armorCounts = new HashMap<>();
        String[] wornMaterials = new String[4];
        int materialSlot = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            String mat = ArmorClassTable.armorSetKeyOf(player.getEquippedStack(slot).getItem());
            wornMaterials[materialSlot++] = mat;
            if (mat != null) armorCounts.merge(mat, 1, Integer::sum);
        }

        // Panel position: left side of inventory
        int panelW = 120;
        int panelH = 185;
        int panelX = (screenWidth / 2) - 90 - panelW - 6;
        int panelY = (screenHeight / 2) - 80;

        // Background
        ctx.fill(panelX - 4, panelY - 4, panelX + panelW + 4, panelY + panelH + 4, 0xCC000000);
        ctx.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0xCC1A1A2E);

        // Title
        ctx.drawTextWithShadow(tr, "§6§l⚔ Damage Affinities", panelX, panelY, 0xFFFFAA00);
        panelY += 13;

        // Divider
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF444444);
        panelY += 4;

        // Armor set / hybrid label
        String setName = getSetDisplayName(armorSet);
        if (!setName.isEmpty()) {
            ctx.drawTextWithShadow(tr, "§7Set: " + setName, panelX, panelY, 0xFFAAAAAA);
            panelY += 11;
        } else {
            // Not a full set - show the hybrid subclass name if a two-material combo is worn.
            HybridSetEntry hybrid = HybridSetRegistry.resolve(wornMaterials);
            if (hybrid != null) {
                ctx.drawTextWithShadow(tr, "§7Hybrid: §d" + hybrid.className(),
                    panelX, panelY, 0xFFAAAAAA);
                panelY += 11;
            }
        }

        // Bar graph for each damage type
        int barMaxWidth = panelW - 4;
        int barHeight = 8;
        int lineHeight = 14;
        int maxBonusHalf = 16; // max expected bonus for scaling bars (16 half-points = 8)

        DamageType[] types = { DamageType.SLASHING, DamageType.CLEAVING, DamageType.BLUNT,
            DamageType.RANGED, DamageType.WATER, DamageType.SPECIAL, DamageType.PHYSICAL, DamageType.PET };
        int[] colors = { COLOR_SLASHING, COLOR_CLEAVING, COLOR_BLUNT,
            COLOR_RANGED, COLOR_WATER, COLOR_SPECIAL, COLOR_PHYSICAL, COLOR_PET };

        for (int i = 0; i < types.length; i++) {
            DamageType type = types[i];
            int bonusHalf = computeBonus(armorCounts, trimBonuses, type);

            // Label
            String label = type.displayName;
            ctx.drawTextWithShadow(tr, label, panelX, panelY, colors[i]);

            // Bonus number on right
            if (bonusHalf > 0) {
                String bonusStr = "+" + DamageType.formatAffinityHalfPoints(bonusHalf);
                int bonusWidth = tr.getWidth(bonusStr);
                ctx.drawTextWithShadow(tr, bonusStr, panelX + panelW - bonusWidth, panelY, 0xFFFFFFFF);
            }

            panelY += 9;

            // Bar background
            ctx.fill(panelX, panelY, panelX + barMaxWidth, panelY + barHeight, 0xFF222222);

            // Bar fill
            if (bonusHalf > 0) {
                int fillWidth = Math.min(barMaxWidth, (int)((bonusHalf / (float) maxBonusHalf) * barMaxWidth));
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

    /**
     * Total affinity for a damage type, in <b>half-points</b> (each armor piece is
     * worth 0.5 affinity = 1 half-point). Armor affinity is per-piece - every worn
     * piece counts; every other source (trim, addon scan, level-up, mob head) is a
     * whole affinity point and is doubled into half-points so the units match.
     * Combat damage is the whole-point total × {@link DamageType#DAMAGE_PER_AFFINITY_POINT}.
     */
    private static int computeBonus(Map<String, Integer> armorCounts,
                                    Map<String, Integer> trimBonuses, DamageType type) {
        // Per-piece armor affinity, already in half-points.
        int halfPoints = DamageType.affinityFromCounts(armorCounts, type);

        // Every other source is a whole affinity point - doubled into half-points.
        int wholePoints = 0;

        String bonusKey = switch (type) {
            case SLASHING -> "SWORD_POWER";
            case CLEAVING -> "CLEAVING_POWER";
            case BLUNT    -> "BLUNT_POWER";
            case WATER    -> "WATER_POWER";
            case SPECIAL  -> "SPECIAL_POWER";
            case PET      -> "ALLY_DAMAGE";
            case RANGED   -> "RANGED_POWER";
            case PHYSICAL -> null;
        };
        if (bonusKey != null) {
            wholePoints += trimBonuses.getOrDefault(bonusKey, 0);
        }
        // Generic melee power stacks with melee types
        if (type == DamageType.SLASHING || type == DamageType.CLEAVING || type == DamageType.BLUNT) {
            wholePoints += trimBonuses.getOrDefault("MELEE_POWER", 0);
        }

        // Addon equipment scanner bonuses (synced from server)
        if (bonusKey != null) {
            wholePoints += CombatState.getAddonBonus(bonusKey);
        }
        if (type == DamageType.SLASHING || type == DamageType.CLEAVING || type == DamageType.BLUNT) {
            wholePoints += CombatState.getAddonBonus("MELEE_POWER");
        }

        // Affinity points from level-up choices (1 point per selection)
        try {
            int affinityOrdinal = PlayerProgression.Affinity.valueOf(type.name()).ordinal();
            wholePoints += CombatState.getAffinityPoints(affinityOrdinal);
        } catch (IllegalArgumentException ignored) {}

        // Mob head bonus contributes 1 affinity point for matching type.
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            wholePoints += DamageType.getMobHeadAffinityPoints(
                mc.player.getEquippedStack(EquipmentSlot.HEAD), type);
        }

        return halfPoints + wholePoints * 2;
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

        // Copper Age Backport - runtime-resolved items so we don't need a hard
        // classpath dependency on the optional mod.
        Item copperHead  = com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.copperHelmet();
        Item copperChest = com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.copperChestplate();
        Item copperLegs  = com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.copperLeggings();
        Item copperFeet  = com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.copperBoots();
        if (copperHead != null && head == copperHead && chest == copperChest
            && legs == copperLegs && feet == copperFeet) return "copper";

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
            //? if <=1.21.1 {
            RegistryEntry<ArmorTrimPattern> pattern = trim.getPattern();
            //?} else {
            /*RegistryEntry<ArmorTrimPattern> pattern = trim.pattern();
            *///?}
            String patternId = pattern.getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            String bonusKey = getTrimBonusKey(patternId);
            if (bonusKey != null) {
                bonuses.merge(bonusKey, 1, Integer::sum);
            }

            // Material bonus
            //? if <=1.21.1 {
            String materialId = trim.getMaterial().getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            //?} else {
            /*String materialId = trim.material().getKey().map(k -> k.getValue().getPath()).orElse("unknown");
            *///?}
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
            case "rib"       -> "SPECIAL_POWER";
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
            case "lapis"     -> "SPECIAL_POWER";
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
            case "leather"   -> "§eBrawler";
            case "chainmail" -> "§7Rogue";
            case "iron"      -> "§fGuard";
            case "gold"      -> "§6Gambler";
            case "diamond"   -> "§bKnight";
            case "netherite" -> "§4Juggernaut";
            case "turtle"    -> "§2Aquatic";
            case "copper"    -> "§6Marksman";
            default -> "";
        };
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
