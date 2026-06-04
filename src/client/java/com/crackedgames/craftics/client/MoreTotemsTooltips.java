package com.crackedgames.craftics.client;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Craftics-flavored tooltips for the MoreTotems mod's totems. Used by {@link CombatTooltips}
 * to replace the mod's original tooltips with what each totem actually does in Craftics combat.
 * Keep in sync with {@code compat/moretotems/MoreTotemsCompat} and the revive helpers in
 * {@code CombatManager}.
 */
public final class MoreTotemsTooltips {

    private MoreTotemsTooltips() {}

    /** True if the registry id namespace is the MoreTotems mod. */
    public static boolean isMoreTotem(net.minecraft.util.Identifier id) {
        return id != null && "moretotems".equals(id.getNamespace());
    }

    /** Append Craftics tooltip lines for the given totem path. */
    public static void appendLines(String path, List<Text> lines) {
        List<String> body = describe(path);
        lines.add(Text.empty());
        lines.add(Text.literal("\u00a76\u00a7lCraftics Combat:"));
        if (body.isEmpty()) {
            lines.add(Text.literal("\u00a78  (no Craftics effect)"));
            return;
        }
        for (String line : body) {
            lines.add(Text.literal(line));
        }
    }

    private static List<String> describe(String path) {
        List<String> out = new ArrayList<>();
        switch (path) {
            case "explosive_totem_of_undying" -> {
                out.add(active("On death: revive at 50% HP"));
                out.add(active("4x4 explosion: 50% max HP + biome bonus to enemies"));
            }
            case "skeletal_totem_of_undying" -> {
                out.add(active("On death: revive at 50% HP"));
                out.add(active("Marks every enemy for 2 turns (2x damage taken)"));
            }
            case "teleporting_totem_of_undying" -> {
                out.add(active("On death: revive at 50% HP"));
                out.add(active("Teleport to the safest tile (farthest from enemies)"));
            }
            case "ghastly_totem_of_undying" -> {
                out.add(active("On death: revive at 50% HP"));
                out.add(active("Sets every enemy on fire for 5 turns"));
                out.add(stat("Fire Resistance for 5 turns"));
            }
            case "stinging_totem_of_undying" -> {
                out.add(active("On death: revive at 50% HP"));
                out.add(active("Summons 5 allied bees (poison stingers)"));
            }
            case "tentacled_totem_of_undying" -> {
                out.add(active("On death: revive at 50% HP"));
                out.add(active("Blinds every enemy for 2 turns (they fumble attacks)"));
            }
            case "rotting_totem_of_undying" -> {
                out.add(active("On death: revive at 50% HP"));
                out.add(active("Summons 3 allied zombies"));
            }
            default -> {}
        }
        return out;
    }

    private static String stat(String text)   { return "\u00a7a  + " + text; }
    private static String active(String text) { return "\u00a7e  \u2022 " + text; }
}
