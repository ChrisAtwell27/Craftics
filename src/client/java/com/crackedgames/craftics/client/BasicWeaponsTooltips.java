package com.crackedgames.craftics.client;

import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;

/** Builds the Craftics combat tooltip block for basicweapons weapons. */
public final class BasicWeaponsTooltips {

    private BasicWeaponsTooltips() {}

    /** Namespace + recognized type suffix. (Bronze also matches; the CombatTooltips gate adds isRegistered.) */
    public static boolean isBasicWeapon(Identifier id) {
        return BasicWeaponsCompat.NAMESPACE.equals(id.getNamespace())
            && BasicWeaponsCompat.weaponType(id.getPath()) != null;
    }

    /**
     * Append the Craftics block. Reads live DMG/AP/Range from the WeaponRegistry so the
     * stat line matches the vanilla weapon tooltips and reflects config retunes.
     * mightLevel <= 0 means "no Might line".
     */
    public static void appendLines(Item item, String path, int mightLevel, List<Text> lines) {
        String type = BasicWeaponsCompat.weaponType(path);
        if (type == null) return;
        WeaponEntry entry = WeaponRegistry.getOrNull(item);
        if (entry == null) return;
        int dmg = entry.attackPower().getAsInt();
        int ap = entry.apCost();
        int range = entry.range();
        var dt = entry.damageType();

        lines.add(Text.empty());
        lines.add(Text.literal("§6§lCraftics Combat:"));
        // Stat line matches the vanilla weaponStatLine format: "<dmg> DMG | Range N | N AP | Type"
        StringBuilder stat = new StringBuilder();
        stat.append("§c").append(dmg).append(" DMG §7| Range ").append(range).append(" §7| ");
        if (ap > 1) stat.append("§c");
        stat.append(ap).append(" AP §7| ").append(dt.color).append(dt.displayName);
        lines.add(Text.literal(stat.toString()));

        for (String line : effectLines(type)) {
            TooltipWrap.addWrapped(lines, " ", "§e• " + line);
        }
        if (BasicWeaponsCompat.isBluntType(type) && mightLevel > 0) {
            lines.add(Text.literal("§b • Might: +" + mightLevel + " dmg, +"
                + (mightLevel * 5) + "% stun"));
        }
    }

    /** Effect description lines per type. The dagger gets an extra line spelling out the double hit. */
    private static String[] effectLines(String type) {
        return switch (type) {
            case "dagger" -> new String[]{
                "§6Dual-wield two daggers: §esecond hit at 75% for 1 AP",
                "About 1.75x damage when paired"
            };
            case "spear" -> new String[]{
                "Reach (2 tiles), lower base damage",
                "§e" + com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat
                    .spearMomentumLine()
            };
            case "quarterstaff" -> new String[]{"Reach (2 tiles) + adjacent sweep"};
            case "club" -> new String[]{"Chance to slow on hit"};
            case "hammer" -> new String[]{"Knockback + stun + shockwave"};
            case "glaive" -> new String[]{"Reach (2 tiles) + wide cleave arc at half damage"};
            default -> new String[0];
        };
    }
}
