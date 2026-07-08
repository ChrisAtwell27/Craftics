package com.crackedgames.craftics.client;

import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.compat.simplyswords.SimplySwordsCompat;
import com.crackedgames.craftics.compat.simplyswords.SimplySwordsUniques;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Builds the Craftics tooltip block for Simply Swords weapons. The caller wipes the
 * mod's own tooltip body first (unique-effect fluff, gem-socket lines, attack-speed
 * attributes), so the player only ever sees the in-Craftics behavior.
 * Uniques get the full "legendary" treatment; standard tiers get the compact combat block.
 */
public final class SimplySwordsTooltips {

    private SimplySwordsTooltips() {}

    /** Namespace gate. Pure - the Craftics registration check happens at the call site. */
    public static boolean isSimplySwords(Identifier id) {
        return SimplySwordsCompat.NAMESPACE.equals(id.getNamespace());
    }

    /** Append the Craftics block. Stats read live from the WeaponRegistry (config retunes apply). */
    public static void appendLines(Item item, String path, List<Text> lines) {
        WeaponEntry entry = WeaponRegistry.getOrNull(item);
        if (entry == null) return;
        boolean unique = SimplySwordsUniques.isUnique(item);

        lines.add(Text.empty());
        if (unique) {
            lines.add(Text.literal("§6§l⚔ Legendary Boss Weapon"));
        } else {
            lines.add(Text.literal("§6§lCraftics Combat:"));
        }

        int dmg = entry.attackPower().getAsInt();
        int ap = entry.apCost();
        int range = entry.range();
        var dt = entry.damageType();
        StringBuilder stat = new StringBuilder();
        stat.append("§c").append(dmg).append(" DMG §7| Range ").append(range).append(" §7| ");
        if (ap > 1) stat.append("§c");
        stat.append(ap).append(" AP §7| ").append(dt.color).append(dt.displayName);
        lines.add(Text.literal(stat.toString()));

        if (unique) {
            String[] block = uniqueLines(path);
            if (block != null) {
                lines.add(Text.literal("§6✦ §e§l" + block[0] + "§r§7: " + block[1]));
                lines.add(Text.literal("§8§o\"" + block[2] + "\""));
            }
            lines.add(Text.literal("§5Rare drop from bosses"));
        } else {
            String type = SimplySwordsCompat.weaponType(path);
            for (String line : typeEffectLines(type)) {
                lines.add(Text.literal("§e • " + line));
            }
        }
    }

    /** Effect description per standard weapon type - must mirror SimplySwordsCompat's abilities. */
    private static String[] typeEffectLines(String type) {
        if (type == null) return new String[0];
        return switch (type) {
            case "longsword" -> new String[]{"10% chance to sweep an adjacent enemy"};
            case "katana" -> new String[]{"25% (+Slashing) chance to inflict Bleed"};
            case "rapier" -> new String[]{"15% riposte: a second thrust at full damage"};
            case "cutlass" -> new String[]{"20% chance to sweep an adjacent enemy"};
            case "sai" -> new String[]{
                "§6Dual-wield two sais: §esecond hit at 75% for 1 AP"};
            case "twinblade" -> new String[]{"Back blade always follows up at 50% damage"};
            case "spear" -> new String[]{
                "Reach (2 tiles), lower base damage",
                "§e+50% damage per tile walked before attacking"};
            case "warglaive" -> new String[]{
                "§6Dual-wield two warglaives: §ecleaves 2 adjacent enemies at 50%"};
            case "chakram" -> new String[]{
                "Thrown - range 3, always returns (no ammo)",
                "Ricochets to a nearby enemy for 50% damage"};
            case "halberd" -> new String[]{"Reach (2 tiles) + 15% adjacent sweep"};
            case "scythe" -> new String[]{"Reaping arc: half damage + Bleed across the sweep"};
            case "claymore", "glaive" -> new String[]{"Wide cleave arc at half damage"};
            case "greataxe" -> new String[]{"15% (+Cleaving) to permanently shatter armor"};
            case "greathammer" -> new String[]{"3x3 shockwave, knockback + stun roll"};
            default -> new String[0];
        };
    }

    /**
     * {effect name, effect description, flavor line} per unique - must mirror
     * {@link SimplySwordsUniques}' server-side behavior.
     */
    private static String[] uniqueLines(String path) {
        return switch (path) {
            // Fire
            case "emberblade" -> new String[]{"Ember Ire",
                "every hit sets the target ablaze (2 turns)",
                "It never stopped smoldering."};
            case "molten_edge" -> new String[]{"Molten Roar",
                "25%: eruption - splash burns everything adjacent for half damage",
                "Forged in a caldera's throat."};
            case "hearthflame" -> new String[]{"Cinder Slam",
                "3x3 slam at half damage; everything caught catches fire",
                "The warmth of home, weaponized."};
            case "brimstone_claymore" -> new String[]{"Brimstone Cleave",
                "wide arc at half damage; the whole arc ignites (2 turns)",
                "It smells of sulfur and endings."};
            case "sunfire" -> new String[]{"Solar Flare",
                "30%: +3 searing damage and the target is dazzled (-1 ATK)",
                "Staring at it is not recommended."};
            case "wickpiercer" -> new String[]{"Candlelight",
                "+3 damage and ignites targets still at full health",
                "The first cut snuffs the light."};
            case "flamewind" -> new String[]{"Flame Dash",
                "40%: the cut bleeds and burns",
                "A katana quenched in a firestorm."};
            case "emberlash" -> new String[]{"Ember Lash",
                "reach 2; ignites, 25% to drag the target a tile closer",
                "The whip cracks in cinders."};
            case "soulpyre" -> new String[]{"Soul Pyre",
                "heals you for 25% of damage dealt",
                "The pyre burns on borrowed souls."};
            // Ice & water
            case "frostfall" -> new String[]{"Frost Fury",
                "soaks + slows every hit; 20% to flash-freeze (stun)",
                "Winter follows the blade."};
            case "icewhisper" -> new String[]{"Cold Snap",
                "40% to slow; 25% for +2 crystallizing damage",
                "You hear it before you feel it."};
            case "dreadtide" -> new String[]{"Riptide Pull",
                "soaks every hit; 25% the undertow drags the target adjacent",
                "The tide always collects."};
            case "livyatan" -> new String[]{"Depth Charge",
                "3x3 wave at half damage; everything hit is soaked",
                "Carved from something that should have stayed sunken."};
            case "chompolotl" -> new String[]{"Chomp",
                "30%: soaks the target and the axolotl spirit heals you",
                "It is very cute. It is very hungry."};
            // Storm
            case "mjolnir" -> new String[]{"Thunderstrike",
                "30%: lightning for +4 and a half-damage shockwave around the target",
                "Whosoever holds this hammer..."};
            case "stormbringer" -> new String[]{"Storm Surge",
                "25%: chains lightning to the 2 nearest enemies for half damage",
                "The storm goes where it is brought."};
            case "thunderbrand" -> new String[]{"Static Charge",
                "33%: the stored charge discharges for +3",
                "Your hair stands up holding it."};
            case "storms_edge" -> new String[]{"Gale Slash",
                "every hit shoves the target back a tile",
                "The wind takes their footing."};
            case "tempest" -> new String[]{"Cyclone",
                "30%: hurls the target 2 tiles and batters for +2",
                "The eye of the storm fits in one hand."};
            case "whisperwind" -> new String[]{"Zephyr",
                "30%: the wind echoes your cut for +50% damage",
                "You never hear the second cut."};
            // Nature & toxin
            case "toxic_longsword" -> new String[]{"Plaguebearer",
                "every wound festers - Poison II for 2 turns",
                "The plague found a handle."};
            case "bramblethorn" -> new String[]{"Thorn Lash",
                "pierces the tile behind; thorns poison the target",
                "It grows back sharper."};
            case "hiveheart" -> new String[]{"The Swarm",
                "30% (+Pet): bees sting up to 3 nearby enemies (scales with Pet)",
                "Bee-loved by its wielder."};
            case "waxweaver" -> new String[]{"Wax Coating",
                "50% to slow; 15% the wax sets and stuns",
                "Everything it touches, it keeps."};
            // Soul & shadow
            case "shadowsting" -> new String[]{"Venom Strike",
                "poisons every hit; +3 ambush damage vs unhurt prey",
                "The shadow strikes first."};
            case "wraithfang" -> new String[]{"Soul Rend",
                "heals you for 30% of damage; 20% to sap 1 ATK",
                "It feeds so you don't have to."};
            case "soulrender" -> new String[]{"Rend Souls",
                "cleave arc at half damage; +2 HP per enemy caught",
                "Every soul torn is a breath returned."};
            case "soulstealer" -> new String[]{"Siphon",
                "heals you for 25% of damage; 15% to steal 1 ATK",
                "What it takes, it keeps."};
            case "soulkeeper" -> new String[]{"Keeper's Pact",
                "heals you for 20% of damage dealt",
                "It holds them gently. Forever."};
            case "slumbering_lichblade" -> new String[]{"Slumbering Soul",
                "heals you for 15% of damage dealt",
                "It dreams of its master."};
            case "waking_lichblade" -> new String[]{"Waking Hunger",
                "heals you for 25% of damage; 15% to wither",
                "It stirs. It remembers."};
            case "awakened_lichblade" -> new String[]{"Awakened Hunger",
                "heals you for 33% of damage; 25% withering grasp",
                "It is awake. It is hungry."};
            // Arcane
            case "arcanethyst" -> new String[]{"Prismatic Echo",
                "30%: the crystal rings, repeating half your hit (+Special)",
                "It remembers every blow in violet."};
            case "magiblade" -> new String[]{"Arcane Edge",
                "+1 damage per 2 Special affinity points, every hit",
                "The spell never ends; it just sharpens."};
            case "magic_estoc" -> new String[]{"Mana Thrust",
                "25%: punches clean through to the enemy behind",
                "En garde, then straight through."};
            case "magiscythe" -> new String[]{"Arcane Reap",
                "arcane arc: half damage +Special across the sweep",
                "It harvests more than wheat."};
            case "magispear" -> new String[]{"Mana Lance",
                "reach 2; always pierces the tile behind the target",
                "Distance is a suggestion."};
            case "enigma" -> new String[]{"Paradox",
                "every hit rolls a random effect: burn, chill, poison, confuse, or +3",
                "Nobody knows what it does next. Including it."};
            case "twilight" -> new String[]{"Duskfall",
                "25%: shadow pours over the target (-1 ATK, confused)",
                "The day ends where it lands."};
            case "stars_edge" -> new String[]{"Starfall",
                "25%: starlight lances down for +3 and half-damage stardust around",
                "A shard of the night sky, still falling."};
            case "caelestis" -> new String[]{"Divine Light",
                "25%: +2 holy damage and restores 2 HP to you",
                "Judgement, gently applied."};
            // Sculk
            case "watcher_claymore" -> new String[]{"Sonic Rend",
                "30%: a warden's shriek tears through for +4",
                "It watched. It waited. It's done waiting."};
            case "watching_warglaive" -> new String[]{"Echoing Slash",
                "30%: the sculk repeats your cut for +50% damage",
                "Every strike is remembered."};
            // Curios
            case "ribboncleaver" -> new String[]{"Ribbon Cut",
                "Bleed x2 every hit; 20% the ribbon flicks to a neighbor",
                "It wraps its gifts in red."};
            case "twisted_blade" -> new String[]{"Twist Reality",
                "25%: the target is bewildered (may attack its allies)",
                "Which way was forward again?"};
            case "harbinger" -> new String[]{"Doomsday",
                "20%: fate collects 10% of the target's max HP early",
                "It only ever brings one message."};
            case "sword_on_a_stick" -> new String[]{"Bonk",
                "reach 2; 10% to bonk the target silly (stun)",
                "It's a sword. On a stick."};
            default -> null;
        };
    }
}
