package com.crackedgames.craftics.client;

import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.compat.simplybows.SimplyBowsCompat;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Builds the Craftics tooltip block for Simply Bows uniques. The caller wipes the mod's
 * own tooltip body first (its ability blurb, upgrade slots, rune etchings), so the player
 * only ever sees the in-Craftics behavior.
 */
public final class SimplyBowsTooltips {

    private SimplyBowsTooltips() {}

    /** Namespace gate. Pure - the Craftics registration check happens at the call site. */
    public static boolean isSimplyBows(Identifier id) {
        return SimplyBowsCompat.NAMESPACE.equals(id.getNamespace());
    }

    /**
     * True for the mod's bow-upgrade components: enchanted strings, reinforced frames, and
     * the four rune etchings. They are a crafting system Craftics does not run, so their
     * tooltips promise power that never arrives in combat.
     */
    public static boolean isUpgradeComponent(Identifier id) {
        return isSimplyBows(id) && id.getPath().startsWith("upgrades/");
    }

    /** Replace an upgrade component's tooltip with an honest one. */
    public static void appendUpgradeLines(List<Text> lines) {
        lines.add(Text.empty());
        lines.add(Text.literal("§6§lCraftics Combat:"));
        lines.add(Text.literal("§7No effect in battle."));
        lines.add(Text.literal("§8Bow upgrades and rune etchings are not simulated;"));
        lines.add(Text.literal("§8each legendary bow has its own fixed ability."));
    }

    /** Append the Craftics block. Stats read live from the WeaponRegistry (config retunes apply). */
    public static void appendLines(Item item, String path, List<Text> lines) {
        WeaponEntry entry = WeaponRegistry.getOrNull(item);
        if (entry == null) return;

        lines.add(Text.empty());
        lines.add(Text.literal("§6§l⚔ Legendary Boss Bow"));

        StringBuilder stat = new StringBuilder();
        stat.append("§c").append(entry.attackPower().getAsInt()).append(" DMG §7| Range ")
            .append(entry.range()).append(" §7| ");
        if (entry.apCost() > 1) stat.append("§c");
        stat.append(entry.apCost()).append(" AP §7| ").append(entry.damageType().color)
            .append(entry.damageType().displayName);
        if (entry.secondaryDamageType() != null) {
            stat.append("§7 + ").append(entry.secondaryDamageType().color)
                .append(entry.secondaryDamageType().displayName);
        }
        lines.add(Text.literal(stat.toString()));
        if (entry.secondaryDamageType() != null) {
            lines.add(Text.literal("§8Hybrid: the second affinity scales at half weight"));
        }

        String[] block = uniqueLines(path);
        if (block != null) {
            lines.add(Text.literal("§6✦ §e§l" + block[0] + "§r§7: " + block[1]));
            if (entry.targetlessCast() != null) {
                lines.add(Text.literal("§b➤ Hits center the effect on the enemy; aim at open ground to place it there"));
            }
            lines.add(Text.literal("§8§o\"" + block[2] + "\""));
        }
        lines.add(Text.literal("§5Rare drop from bosses"));
    }

    /**
     * {effect name, effect description, flavor line} per bow - must mirror
     * {@code SimplyBowsUniques}' server-side behavior.
     */
    private static String[] uniqueLines(String path) {
        return switch (path) {
            case "vine_bow/vine_bow" -> new String[]{"Flower Field",
                "grows a 3x3 field for 4 turns around whatever you hit; it saps and bewilders enemies standing in it and mends your party",
                "It plants a garden in the middle of a war."};
            case "ice_bow/ice_bow" -> new String[]{"Frost Fan",
                "each hit fans out 3 homing frost bolts for 2 Water damage that chill whatever they find",
                "Winter follows the string."};
            case "bubble_bow/bubble_bow" -> new String[]{"Bubble Burst",
                "hits Soak the target and leave a column under it; any enemy walking into a column is Soaked and thrown clear",
                "The water remembers where you stood."};
            case "bee_bow/bee_bow" -> new String[]{"The Hive",
                "poisons every hit, and 35% of the time the arrow bursts into 1-2 bee allies",
                "Loosed, not fired."};
            case "blossom_bow/blossom_bow" -> new String[]{"Blossom Storm",
                "rains arrows across a quarter of the arena's tiles for 3 Ranged damage each; a struck enemy is always caught in it",
                "Spring arrives all at once."};
            case "earth_bow/earth_bow" -> new String[]{"Tremor",
                "stuns the target and everything within 2 tiles, which also takes half damage",
                "The ground answers before the arrow lands."};
            case "echo_bow/echo_bow" -> new String[]{"Echoing Volley",
                "two phantom bows loose at the nearest other enemies for half your damage",
                "You never shoot alone."};
            default -> null;
        };
    }
}
