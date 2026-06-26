package com.crackedgames.craftics.client;

import com.crackedgames.craftics.api.registry.HybridSetEntry;
import com.crackedgames.craftics.api.registry.HybridSetRegistry;
import com.crackedgames.craftics.combat.ArmorClassTable;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.client.guide.GuideTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
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

    // ARGB colors for each damage type BAR (kept vibrant - reads fine on the
    // parchment bar track).
    private static final int COLOR_SLASHING    = 0xFFFF5555; // red
    private static final int COLOR_CLEAVING = 0xFFFFAA00; // orange
    private static final int COLOR_BLUNT    = 0xFF888888; // gray
    private static final int COLOR_WATER    = 0xFF5555FF; // blue
    private static final int COLOR_SPECIAL    = 0xFFFF55FF; // pink
    private static final int COLOR_PET      = 0xFF55FF55; // green
    private static final int COLOR_RANGED   = 0xFF55FFFF; // cyan
    private static final int COLOR_PHYSICAL = 0xFFAAAAAA; // light gray

    // Darkened variants for LABEL/ICON text so light-on-parchment stays legible.
    // Water stays as-is (already dark enough); everything else is deepened.
    private static final int LABEL_SLASHING = 0xFFB02020; // deep red
    private static final int LABEL_CLEAVING = 0xFFB06A00; // deep orange
    private static final int LABEL_BLUNT    = 0xFF4A4A4A; // dark gray
    private static final int LABEL_WATER    = 0xFF3838C8; // blue (kept)
    private static final int LABEL_SPECIAL  = 0xFFB030B0; // deep magenta
    private static final int LABEL_PET      = 0xFF2E8B2E; // deep green
    private static final int LABEL_RANGED   = 0xFF1F8A8A; // deep teal
    private static final int LABEL_PHYSICAL = 0xFF555555; // dark gray

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final int BTN = 11;
    private static final int STRIP_W = 26;
    /** Minecraft inventory background height; panels match it so they read as the
     *  same size as the inventory and center against it. */
    private static final int INV_H = 166;

    private static final int PANEL_W = 120;
    // Content height pieces: 4 inset + 13 title + 4 rule + 8 bars x 18 + 4 pad = 169.
    // A set/hybrid label, when shown, adds one 11px row (computed in panelHeight).
    private static final int PANEL_H_BASE = 169;
    private static final int LABEL_ROW = 11;

    /** 1.0 if the panel's left edge has room; otherwise a shrink factor. */
    public static float fitScale(int screenWidth, int screenHeight) {
        int[] L = computeLayout(screenWidth, screenHeight);
        if (L[0] >= 6) return 1.0f;
        int avail = (screenWidth / 2) - 90 - 6;
        return Math.max(0.5f, avail / (float) (L[2] + 6));
    }

    /** Minimize-button rect {x, y, w, h} for the affinity panel. */
    public static int[] buttonRect(int screenWidth, int screenHeight) {
        float s = fitScale(screenWidth, screenHeight);
        int ox, oy; int[] base;
        if (CombatState.isAffinityPanelCollapsed()) {
            int[] c = collapsedLayout(screenWidth, screenHeight);
            ox = c[0]; oy = c[1];
            base = new int[]{c[0] + c[2] - BTN, c[1], BTN, BTN};
        } else {
            int[] L = computeLayout(screenWidth, screenHeight);
            ox = L[0]; oy = L[1];
            base = new int[]{L[0] + L[2] - BTN, L[1] + 2, BTN, BTN};
        }
        if (s == 1.0f) return base;
        int x = ox + Math.round((base[0] - ox) * s);
        int y = oy + Math.round((base[1] - oy) * s);
        return new int[]{x, y, Math.round(BTN * s), Math.round(BTN * s)};
    }

    /** Collapsed icon-strip rect {x, y, w, h}. Matches the inventory's height so
     *  the thin sidebar reads as the same size as the inventory, anchored to the
     *  same left edge as the expanded panel and vertically centered. */
    private static int[] collapsedLayout(int screenWidth, int screenHeight) {
        int x = (screenWidth / 2) - 90 - STRIP_W - 6;
        int y = (screenHeight / 2) - INV_H / 2;
        return new int[]{x, y, STRIP_W, INV_H};
    }

    private static void drawAffinityButton(DrawContext ctx, TextRenderer tr, int bx, int by, boolean collapsed) {
        ctx.fill(bx, by, bx + BTN, by + BTN, GuideTheme.PARCH_EDGE);
        ctx.fill(bx, by, bx + BTN, by + 1, GuideTheme.GOLD_DIM);
        String sym = collapsed ? "+" : "–";
        int sw = tr.getWidth(sym);
        ctx.drawText(tr, Text.literal(sym), bx + (BTN - sw) / 2, by + 2, GuideTheme.INK, false);
    }

    /** Unicode glyph for a damage type, borrowed from the matching Affinity enum
     *  (which carries per-type icons). Falls back to the first letter. */
    private static String affinityGlyph(DamageType type) {
        try {
            return PlayerProgression.Affinity.valueOf(type.name()).icon;
        } catch (IllegalArgumentException e) {
            return type.displayName.substring(0, 1);
        }
    }

    /** True when the panel will draw a "Set:" or "Hybrid:" label row, which only
     *  happens for a full vanilla set or a recognized two-material hybrid combo. */
    private static boolean hasSetLabel() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        if (!getSetDisplayName(getArmorSet(player)).isEmpty()) return true;
        String[] worn = new String[4];
        int i = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            worn[i++] = ArmorClassTable.armorSetKeyOf(player.getEquippedStack(slot).getItem());
        }
        return HybridSetRegistry.resolve(worn) != null;
    }

    /** Panel content height, shrinking when there is no set/hybrid label row so
     *  there's no dead space under the last bar. */
    private static int panelHeight() {
        return PANEL_H_BASE + (hasSetLabel() ? LABEL_ROW : 0);
    }

    /** Content rect {x, y, w, h} anchored left of the inventory GUI and vertically
     *  centered against the 166px inventory background. */
    public static int[] computeLayout(int screenWidth, int screenHeight) {
        int h = panelHeight();
        int x = (screenWidth / 2) - 90 - PANEL_W - 6;
        int y = (screenHeight / 2) - h / 2;
        return new int[]{x, y, PANEL_W, h};
    }

    public static void render(DrawContext ctx, int screenWidth, int screenHeight, int mouseX, int mouseY) {
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

        if (CombatState.isAffinityPanelCollapsed()) {
            int[] CL = collapsedLayout(screenWidth, screenHeight);
            int cx = CL[0], cy0 = CL[1], cw = CL[2], ch = CL[3];
            float cScale = fitScale(screenWidth, screenHeight);
            boolean cScaled = cScale != 1.0f;
            if (cScaled) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate((float) cx, (float) cy0, 0);
                ctx.getMatrices().scale(cScale, cScale, 1.0f);
                ctx.getMatrices().translate((float) -cx, (float) -cy0, 0);
            }
            GuideTheme.drawPanel(ctx, cx, cy0, cw, ch);
            int[] cbr = buttonRect(screenWidth, screenHeight);
            drawAffinityButton(ctx, tr, cbr[0], cbr[1], true);

            DamageType[] cTypes = { DamageType.SLASHING, DamageType.CLEAVING, DamageType.BLUNT,
                DamageType.RANGED, DamageType.WATER, DamageType.SPECIAL, DamageType.PHYSICAL, DamageType.PET };
            int[] cColors = { LABEL_SLASHING, LABEL_CLEAVING, LABEL_BLUNT,
                LABEL_RANGED, LABEL_WATER, LABEL_SPECIAL, LABEL_PHYSICAL, LABEL_PET };
            // Space the icon rows evenly through the strip below the button, each
            // glyph horizontally centered in the strip.
            int top = cy0 + BTN + 4;
            int bottom = cy0 + ch - 4;
            int n = cTypes.length;
            int slot = (bottom - top) / n;
            for (int i = 0; i < cTypes.length; i++) {
                int bonusHalf = computeBonus(armorCounts, trimBonuses, cTypes[i]);
                String glyph = affinityGlyph(cTypes[i]);
                int gx = cx + (cw - tr.getWidth(glyph)) / 2;
                int rowY = top + i * slot + (slot - 8) / 2;
                ctx.drawText(tr, Text.literal(glyph), gx, rowY, cColors[i], false);
                if (mouseX >= cx && mouseX < cx + cw && mouseY >= rowY - 3 && mouseY < rowY + 11) {
                    String tip = cTypes[i].displayName + ": +" + DamageType.formatAffinityHalfPoints(bonusHalf);
                    ctx.drawTooltip(tr, Text.literal(tip), mouseX, mouseY);
                }
            }
            if (cScaled) ctx.getMatrices().pop();
            return;
        }

        int[] L = computeLayout(screenWidth, screenHeight);
        int panelX = L[0];
        int panelY = L[1];
        int panelW = L[2];
        float scale = fitScale(screenWidth, screenHeight);
        boolean scaled = scale != 1.0f;
        int originX = L[0], originY = L[1];
        if (scaled) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate((float) originX, (float) originY, 0);
            ctx.getMatrices().scale(scale, scale, 1.0f);
            ctx.getMatrices().translate((float) -originX, (float) -originY, 0);
        }
        GuideTheme.drawPanel(ctx, panelX, panelY, panelW, L[3]);
        panelX += 4;
        panelY += 4;
        panelW -= 8;

        // Title (kept short so it clears the minimize button on the same row)
        ctx.drawText(tr, "§l⚔ Affinities", panelX, panelY, GuideTheme.GOLD, false);
        panelY += 13;

        // Divider
        GuideTheme.drawRule(ctx, panelX, panelY, panelW);
        panelY += 4;

        // Armor set / hybrid label
        String setName = getSetDisplayName(armorSet);
        if (!setName.isEmpty()) {
            ctx.drawText(tr, "Set: " + setName, panelX, panelY, GuideTheme.INK_SOFT, false);
            panelY += 11;
        } else {
            // Not a full set - show the hybrid subclass name if a two-material combo is worn.
            HybridSetEntry hybrid = HybridSetRegistry.resolve(wornMaterials);
            if (hybrid != null) {
                ctx.drawText(tr, "Hybrid: §d" + hybrid.className(),
                    panelX, panelY, GuideTheme.INK_SOFT, false);
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
        int[] labelColors = { LABEL_SLASHING, LABEL_CLEAVING, LABEL_BLUNT,
            LABEL_RANGED, LABEL_WATER, LABEL_SPECIAL, LABEL_PHYSICAL, LABEL_PET };

        for (int i = 0; i < types.length; i++) {
            DamageType type = types[i];
            int bonusHalf = computeBonus(armorCounts, trimBonuses, type);

            // Label (darkened so it reads on parchment; bar keeps the vibrant color)
            String label = type.displayName;
            ctx.drawText(tr, label, panelX, panelY, labelColors[i], false);

            // Bonus number on right
            if (bonusHalf > 0) {
                String bonusStr = "+" + DamageType.formatAffinityHalfPoints(bonusHalf);
                int bonusWidth = tr.getWidth(bonusStr);
                ctx.drawText(tr, bonusStr, panelX + panelW - bonusWidth, panelY, GuideTheme.INK, false);
            }

            panelY += 9;

            // Bar background
            ctx.fill(panelX, panelY, panelX + barMaxWidth, panelY + barHeight, GuideTheme.PARCH_SHADE);

            // Bar fill
            if (bonusHalf > 0) {
                int fillWidth = Math.min(barMaxWidth, (int)((bonusHalf / (float) maxBonusHalf) * barMaxWidth));
                fillWidth = Math.max(3, fillWidth); // minimum visible width
                int barColor = colors[i];
                ctx.fill(panelX, panelY, panelX + fillWidth, panelY + barHeight, barColor);
                // Shine on top pixel row
                ctx.fill(panelX, panelY, panelX + fillWidth, panelY + 1,
                    GuideTheme.brighten(barColor, 60));
            }

            panelY += barHeight + (lineHeight - barHeight - 9) + 4;
        }

        int[] ebr = buttonRect(screenWidth, screenHeight);
        drawAffinityButton(ctx, tr, ebr[0], ebr[1], false);
        if (scaled) ctx.getMatrices().pop();
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
}
