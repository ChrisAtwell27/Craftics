package com.crackedgames.craftics.api;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.ArmorSetEntry;
import com.crackedgames.craftics.api.registry.ArmorSetRegistry;
import com.crackedgames.craftics.api.registry.EnchantmentRegistry;
import com.crackedgames.craftics.api.registry.EventEntry;
import com.crackedgames.craftics.api.registry.EventRegistry;
import com.crackedgames.craftics.api.registry.TrimMaterialEntry;
import com.crackedgames.craftics.api.registry.TrimMaterialRegistry;
import com.crackedgames.craftics.api.registry.TrimPatternEntry;
import com.crackedgames.craftics.api.registry.TrimPatternRegistry;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.TrimEffects;

public final class VanillaContent {
    private VanillaContent() {}

    public static void registerAll() {
        registerArmorSets();
        registerTrimPatterns();
        registerTrimMaterials();
        registerEvents();
        registerEnchantments();
    }

    private static void registerArmorSets() {
        // Values from PlayerCombatStats.getSetBonusDescription() and DamageType.getArmorSetBonus()
        ArmorSetRegistry.register(ArmorSetEntry.builder("leather")
            .damageBonus(DamageType.PHYSICAL, 2)
            .description("§eBrawler: +2 Physical dmg, kill streak damage bonus")
            .build());
        ArmorSetRegistry.register(ArmorSetEntry.builder("chainmail")
            .damageBonus(DamageType.SLASHING, 2)
            .speedBonus(1).apCostReduction(1)
            .description("§7Rogue: +1 Speed, attacks cost -1 AP (min 1)")
            .build());
        ArmorSetRegistry.register(ArmorSetEntry.builder("iron")
            .damageBonus(DamageType.CLEAVING, 2)
            .defenseBonus(2)
            .description("§fGuard: +2 Defense, immune to knockback")
            .build());
        ArmorSetRegistry.register(ArmorSetEntry.builder("gold")
            .damageBonus(DamageType.SPECIAL, 2)
            .description("§6Gambler: +3 Luck crit chance, +1 emerald per kill")
            .build());
        ArmorSetRegistry.register(ArmorSetEntry.builder("diamond")
            .damageBonus(DamageType.BLUNT, 2)
            .defenseBonus(3).attackBonus(1)
            .description("§bKnight: +3 Defense, +1 Attack")
            .build());
        ArmorSetRegistry.register(ArmorSetEntry.builder("netherite")
            .allDamageBonus(1)
            .defenseBonus(4).attackBonus(2)
            .description("§4Juggernaut: +4 Defense, +2 Attack, immune to fire damage")
            .build());
        ArmorSetRegistry.register(ArmorSetEntry.builder("turtle")
            .damageBonus(DamageType.WATER, 3)
            .description("§2Aquatic: Water tiles are walkable, +1 HP regen per turn, +1 Range when on water")
            .build());
    }

    private static void registerTrimPatterns() {
        TrimPatternRegistry.register(new TrimPatternEntry("sentry",
            TrimEffects.Bonus.RANGED_POWER, "+1 Ranged Power per piece",
            TrimEffects.SetBonus.OVERWATCH, "Overwatch",
            "Counter-attack ranged enemies that hit you"));
        TrimPatternRegistry.register(new TrimPatternEntry("dune",
            TrimEffects.Bonus.BLUNT_POWER, "+1 Blunt Power per piece",
            TrimEffects.SetBonus.SANDSTORM, "Sandstorm",
            "Enemies within 2 tiles lose 1 Speed"));
        TrimPatternRegistry.register(new TrimPatternEntry("coast",
            TrimEffects.Bonus.WATER_POWER, "+1 Water Power per piece",
            TrimEffects.SetBonus.TIDAL, "Tidal",
            "Water tiles heal 1 HP/turn"));
        TrimPatternRegistry.register(new TrimPatternEntry("wild",
            TrimEffects.Bonus.AP, "+1 AP per piece",
            TrimEffects.SetBonus.FERAL, "Feral",
            "Kill streak: 1.3x damage per streak level (resets if no kills on your turn)"));
        TrimPatternRegistry.register(new TrimPatternEntry("ward",
            TrimEffects.Bonus.DEFENSE, "+1 Defense per piece",
            TrimEffects.SetBonus.FORTRESS, "Fortress",
            "50% less damage when you didn't move this turn"));
        TrimPatternRegistry.register(new TrimPatternEntry("eye",
            TrimEffects.Bonus.ATTACK_RANGE, "+1 Attack Range per piece",
            TrimEffects.SetBonus.ALL_SEEING, "Eagle Eye",
            "Ranged attacks have +30% crit chance"));
        TrimPatternRegistry.register(new TrimPatternEntry("vex",
            TrimEffects.Bonus.ARMOR_PEN, "Ignore 1 enemy DEF per piece",
            TrimEffects.SetBonus.ETHEREAL, "Ethereal",
            "20% chance to dodge incoming attacks"));
        TrimPatternRegistry.register(new TrimPatternEntry("tide",
            TrimEffects.Bonus.REGEN, "+1 HP regen per 2 turns per piece",
            TrimEffects.SetBonus.OCEAN_BLESSING, "Ocean's Blessing",
            "Full heal when dropping below 25% HP (once per combat)"));
        TrimPatternRegistry.register(new TrimPatternEntry("snout",
            TrimEffects.Bonus.CLEAVING_POWER, "+1 Cleaving Power per piece",
            TrimEffects.SetBonus.BRUTE_FORCE, "Brute Force",
            "Melee attacks splash to adjacent enemies"));
        TrimPatternRegistry.register(new TrimPatternEntry("rib",
            TrimEffects.Bonus.SPECIAL_POWER, "+1 Special Power per piece",
            TrimEffects.SetBonus.INFERNAL, "Infernal",
            "Fire attacks deal +3 bonus damage"));
        TrimPatternRegistry.register(new TrimPatternEntry("spire",
            TrimEffects.Bonus.LUCK, "+1 Luck per piece",
            TrimEffects.SetBonus.FORTUNE_PEAK, "Fortune's Peak",
            "Double emerald rewards"));
        TrimPatternRegistry.register(new TrimPatternEntry("wayfinder",
            TrimEffects.Bonus.SPEED, "+1 Speed per piece",
            TrimEffects.SetBonus.PATHFINDER, "Pathfinder",
            "Movement ignores obstacle tiles"));
        TrimPatternRegistry.register(new TrimPatternEntry("shaper",
            TrimEffects.Bonus.DEFENSE, "+1 Defense per piece",
            TrimEffects.SetBonus.TERRAFORMER, "Earthshatter",
            "Moving 3+ tiles deals 2 damage to all enemies adjacent to your destination"));
        TrimPatternRegistry.register(new TrimPatternEntry("silence",
            TrimEffects.Bonus.STEALTH_RANGE, "+1 stealth range per piece",
            TrimEffects.SetBonus.PHANTOM, "Phantom",
            "Invisible for first 2 turns (enemies don't act)"));
        TrimPatternRegistry.register(new TrimPatternEntry("raiser",
            TrimEffects.Bonus.ALLY_DAMAGE, "+1 ally damage per piece",
            TrimEffects.SetBonus.RALLY, "Rally",
            "Tamed allies get +2 Speed and +1 Attack"));
        TrimPatternRegistry.register(new TrimPatternEntry("host",
            TrimEffects.Bonus.MAX_HP, "+2 max HP per piece",
            TrimEffects.SetBonus.SYMBIOTE, "Symbiote",
            "Heal 1 HP for each enemy killed"));
        TrimPatternRegistry.register(new TrimPatternEntry("flow",
            TrimEffects.Bonus.SPEED, "+1 Speed per piece",
            TrimEffects.SetBonus.CURRENT, "Current",
            "Killing an enemy refunds 1 AP"));
        TrimPatternRegistry.register(new TrimPatternEntry("bolt",
            TrimEffects.Bonus.SWORD_POWER, "+1 Slashing Power per piece",
            TrimEffects.SetBonus.THUNDERSTRIKE, "Thunderstrike",
            "Critical hits stun the target for 1 turn"));
    }

    private static void registerTrimMaterials() {
        TrimMaterialRegistry.register(new TrimMaterialEntry("iron",      TrimEffects.Bonus.DEFENSE,    1, "+1 Defense per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("copper",    TrimEffects.Bonus.SPEED,      1, "+1 Speed per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("gold",      TrimEffects.Bonus.LUCK,       1, "+1 Luck per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("lapis",     TrimEffects.Bonus.SPECIAL_POWER, 1, "+1 Special Power per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("emerald",   TrimEffects.Bonus.AP,         1, "+1 AP per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("diamond",   TrimEffects.Bonus.MELEE_POWER, 1, "+1 Melee Power per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("netherite", TrimEffects.Bonus.ARMOR_PEN,  1, "+1 Armor Penetration per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("redstone",  TrimEffects.Bonus.RANGED_POWER, 1, "+1 Ranged Power per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("amethyst",  TrimEffects.Bonus.REGEN,      1, "+1 HP Regen per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("quartz",    TrimEffects.Bonus.MAX_HP,     2, "+2 Max HP per piece"));
        TrimMaterialRegistry.register(new TrimMaterialEntry("resin",     TrimEffects.Bonus.ALLY_DAMAGE, 1, "+1 Ally Damage per piece"));
    }

    private static void registerEnchantments() {
        // The existing vanilla enchantment system (Sharpness, Smite, Protection, etc.) is already
        // handled by direct calls in PlayerCombatStats and WeaponAbility.
        // This registry is for ADDON enchantments that want to add Craftics stat bonuses.
        //
        // Example of how addon enchantments would be registered:
        // EnchantmentRegistry.register("mymod:fortify", ctx -> {
        //     ctx.getModifiers().add(TrimEffects.Bonus.DEFENSE, ctx.getLevel());
        // });
    }

    private static void registerEvents() {
        // Trader probability comes from config at runtime
        float traderChance = CrafticsMod.CONFIG.traderSpawnChance();

        EventRegistry.register(new EventEntry("craftics:ominous_trial", "Ominous Trial", 0.05f, 10, true, null));
        EventRegistry.register(new EventEntry("craftics:trial_chamber", "Trial Chamber", 0.10f, 0, true, null));
        EventRegistry.register(new EventEntry("craftics:ambush", "Ambush", 0.10f, 0, false, null));
        EventRegistry.register(new EventEntry("craftics:shrine", "Shrine", 0.07f, 0, false, null));
        EventRegistry.register(new EventEntry("craftics:traveler", "Traveler", 0.06f, 0, false, null));
        EventRegistry.register(new EventEntry("craftics:treasure_vault", "Treasure Vault", 0.04f, 0, true, null));
        EventRegistry.register(new EventEntry("craftics:dig_site", "Dig Site", 0.06f, 0, true, null));
        EventRegistry.register(new EventEntry("craftics:trader", "Trader", traderChance, 0, true, null));
        // NOTE: Crafting Station is a built-in, dispatched directly in
        // CombatManager.rollEvent. Do NOT register it as an addon entry — that
        // would surface a "craftics:crafting_station" entry in /craftics
        // force_event tab completion that routes through the addon path with
        // a null handler and silently fails.
    }
}
