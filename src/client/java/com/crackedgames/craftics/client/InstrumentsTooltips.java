package com.crackedgames.craftics.client;

import com.crackedgames.craftics.compat.instruments.InstrumentDef;
import com.crackedgames.craftics.compat.instruments.InstrumentsCompat;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Craftics combat tooltip block for instrument items. */
public final class InstrumentsTooltips {

    private InstrumentsTooltips() {}

    public static boolean isInstrument(Identifier id) {
        return InstrumentsCompat.isInstrument(id);
    }

    public static void appendLines(Item item, List<Text> lines) {
        InstrumentDef def = InstrumentsCompat.defFor(item);
        if (def == null) return;
        boolean attack = def.role() == InstrumentDef.Role.ATTACK;

        lines.add(Text.empty());
        lines.add(Text.literal("§6§lCraftics Combat:"));

        StringBuilder stat = new StringBuilder();
        if (attack) stat.append("§c").append(def.baseDamage()).append(" DMG §7| ");
        else stat.append("§aSupport §7| ");
        stat.append(shapeName(def.shape())).append(" §7| ");
        if (def.apCost() > 1) stat.append("§c");
        stat.append(def.apCost()).append(" AP §7| §dSpecial");
        lines.add(Text.literal(stat.toString()));

        for (String line : effectLines(def)) {
            TooltipWrap.addWrapped(lines, " ", "§e• " + line);
        }
        if (def.signature() != InstrumentDef.Signature.NONE) {
            lines.add(Text.literal("§5 ✦ " + signatureLine(def.signature())));
        }
    }

    private static String shapeName(InstrumentDef.Shape s) {
        return switch (s) {
            case RING1 -> "Ring (around you)";
            case RING2 -> "Ring (2 out)";
            case FILLED_DISC2 -> "Burst (within 2)";
            case STAR -> "Star";
            case PLUS -> "Cross";
            case DIAGONALS -> "Diagonals";
            case EXPANDING_PULSE -> "Expanding pulse";
            case CONE -> "Cone (facing)";
            case SCATTER -> "Scattered notes";
            case FULL_ARENA -> "Whole arena";
        };
    }

    private static String[] effectLines(InstrumentDef def) {
        List<String> out = new ArrayList<>();
        for (InstrumentDef.Effect fx : def.effects()) {
            if (fx.type() == null) continue;
            String name = fx.type().displayName;
            String lvl = fx.amplifier() > 0 ? " " + roman(fx.amplifier() + 1) : "";
            // Confusion is not guaranteed - it rolls confusionApplyChance. Show the % so
            // the tooltip matches the actual behavior (nerf). Other effects still apply.
            String chance = fx.type() == com.crackedgames.craftics.combat.CombatEffects.EffectType.CONFUSION
                ? " (" + Math.round(com.crackedgames.craftics.CrafticsMod.CONFIG.confusionApplyChance() * 100)
                    + "% chance, " + fx.turns() + " turns)"
                : " (" + fx.turns() + " turns)";
            out.add((def.role() == InstrumentDef.Role.ATTACK ? "Inflicts " : "Grants ")
                + name + lvl + chance);
        }
        return out.toArray(new String[0]);
    }

    private static String signatureLine(InstrumentDef.Signature s) {
        return switch (s) {
            case KNOCKBACK -> "Knocks struck enemies back 1 tile";
            case FLAT_HEAL -> "Instantly heals friendlies +6 HP";
            case ARENA_HEAL -> "Heals every friendly +4 HP";
            case CLEANSE -> "Cleanses all debuffs off friendlies";
            case NONE -> "";
        };
    }

    private static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> String.valueOf(n); };
    }
}
