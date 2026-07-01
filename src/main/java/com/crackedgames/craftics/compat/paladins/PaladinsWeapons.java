package com.crackedgames.craftics.compat.paladins;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.DamageType;
import net.minecraft.item.Item;

import java.util.function.IntSupplier;

/** Registers Paladins melee weapons (claymore/great_hammer/mace) into Craftics combat. */
final class PaladinsWeapons {

    private PaladinsWeapons() {}

    /** Tiers to probe. Not all (tier x type) combos exist; registration skips missing ids. */
    private static final String[] TIERS = {
        "wooden", "stone", "iron", "golden", "diamond", "netherite", "aeternium", "ruby", "aether"
    };

    static void register() {
        for (String tier : TIERS) {
            registerOne(tier, "claymore", DamageType.SLASHING, claymoreDmg(tier));
            registerOne(tier, "great_hammer", DamageType.BLUNT, hammerDmg(tier));
            registerOne(tier, "mace", DamageType.BLUNT, maceDmg(tier));
        }
    }

    private static void registerOne(String tier, String type, DamageType dt, IntSupplier power) {
        Item item = PaladinsCompat.lookupItem(tier + "_" + type);
        if (item == null) return;
        WeaponRegistry.register(item, WeaponEntry.builder(item)
            .damageType(dt).attackPower(power).apCost(1).range(1).breakChance(0.01)
            .build());
    }

    // Claymore: a heavy two-hander, use the sword curve. Extra tiers scale off netherite.
    private static IntSupplier claymoreDmg(String tier) {
        return () -> swordDmg(tier) + 1; // slightly above sword: it is a big two-hander
    }

    private static IntSupplier hammerDmg(String tier) {
        return () -> axeDmg(tier) + 1;
    }

    private static IntSupplier maceDmg(String tier) {
        return () -> axeDmg(tier);
    }

    private static int swordDmg(String tier) {
        return switch (tier) {
            case "wooden" -> CrafticsMod.CONFIG.dmgWoodenSword();
            case "stone" -> CrafticsMod.CONFIG.dmgStoneSword();
            case "iron" -> CrafticsMod.CONFIG.dmgIronSword();
            case "golden" -> CrafticsMod.CONFIG.dmgGoldenSword();
            case "diamond" -> CrafticsMod.CONFIG.dmgDiamondSword();
            case "netherite" -> CrafticsMod.CONFIG.dmgNetheriteSword();
            case "aeternium" -> CrafticsMod.CONFIG.dmgNetheriteSword() + 2;
            case "ruby" -> CrafticsMod.CONFIG.dmgNetheriteSword() + 2;
            case "aether" -> CrafticsMod.CONFIG.dmgNetheriteSword() + 2;
            default -> 1;
        };
    }

    private static int axeDmg(String tier) {
        return switch (tier) {
            case "wooden" -> CrafticsMod.CONFIG.dmgWoodenAxe();
            case "stone" -> CrafticsMod.CONFIG.dmgStoneAxe();
            case "iron" -> CrafticsMod.CONFIG.dmgIronAxe();
            case "golden" -> CrafticsMod.CONFIG.dmgGoldenAxe();
            case "diamond" -> CrafticsMod.CONFIG.dmgDiamondAxe();
            case "netherite" -> CrafticsMod.CONFIG.dmgNetheriteAxe();
            case "aeternium" -> CrafticsMod.CONFIG.dmgNetheriteAxe() + 1;
            case "ruby" -> CrafticsMod.CONFIG.dmgNetheriteAxe() + 1;
            case "aether" -> CrafticsMod.CONFIG.dmgNetheriteAxe() + 1;
            default -> 1;
        };
    }
}
