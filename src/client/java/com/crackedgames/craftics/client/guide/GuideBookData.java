package com.crackedgames.craftics.client.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All guide book content. Categories contain entries, entries contain pages.
 * Tracks which entries are unlocked (for bestiary discovery).
 */
public class GuideBookData {

    public record Page(String title, String text) {}
    public record Entry(String name, String iconItem, List<Page> pages) {}
    public record Category(String name, String iconItem, String description, List<Entry> entries) {}

    private static final List<Category> CATEGORIES = new ArrayList<>();
    private static final java.util.Set<String> unlockedEntries = new java.util.HashSet<>();

    static {
        buildContent();
        // All categories unlocked except Bestiary (index 1) and Armor Trims (unlocks on acquire)
        for (int i = 0; i < CATEGORIES.size(); i++) {
            String catName = CATEGORIES.get(i).name();
            if (catName.equals("Enemy Bestiary") || catName.equals("Armor Trims")) continue;
            for (Entry e : CATEGORIES.get(i).entries()) unlockedEntries.add(e.name());
        }
    }

    public static List<Category> getCategories() { return CATEGORIES; }

    public static boolean isUnlocked(String entryName) {
        return unlockedEntries.contains(entryName);
    }

    public static void unlock(String entryName) {
        unlockedEntries.add(entryName);
    }

    /** Unlock the Armor Trims guide entry (called when player receives a trim template). */
    public static void unlockTrims() {
        unlock("How Trims Work");
    }

    /** Unlock a bestiary entry by mob type ID (e.g. "minecraft:zombie", "minecraft:ender_dragon") */
    public static void unlockMob(String entityTypeId) {
        String raw = entityTypeId;
        int colon = raw.indexOf(':');
        if (colon >= 0) raw = raw.substring(colon + 1);
        // Convert "ender_dragon" → "Ender Dragon", "zombified_piglin" → "Zombified Piglin"
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
        }
        unlock(sb.toString());
    }

    /** Shorthand for creating a bestiary mob entry. Uses mob name as both entry name and page title. */
    private static Entry mob(String name, String description) {
        return new Entry(name, "minecraft:barrier", List.of(new Page(name, description)));
    }

    /** Get the total number of bestiary entries. */
    public static int getBestiaryTotal() {
        for (Category cat : CATEGORIES) {
            if (cat.name().equals("Enemy Bestiary")) return cat.entries().size();
        }
        return 0;
    }

    /** Get the number of unlocked bestiary entries. */
    public static int getBestiaryUnlocked() {
        int count = 0;
        for (Category cat : CATEGORIES) {
            if (cat.name().equals("Enemy Bestiary")) {
                for (Entry e : cat.entries()) {
                    if (isUnlocked(e.name())) count++;
                }
                return count;
            }
        }
        return 0;
    }

    private static void buildContent() {
        CATEGORIES.clear();

        // === COMBAT BASICS ===
        List<Entry> basics = new ArrayList<>();
        basics.add(new Entry("How Combat Works", "minecraft:clock", List.of(
            new Page("Turn-Based Combat",
                "Combat in Craftics is turn-based. You and enemies take turns on a grid arena.\n\n" +
                "Each turn you have Action Points (AP) and Speed to spend.\n\n" +
                "AP lets you attack, use items, or perform abilities.\n" +
                "Speed determines how many tiles you can move."),
            new Page("Your Turn",
                "On your turn:\n" +
                "- Left-click a tile to move there\n" +
                "- Left-click an enemy to attack\n" +
                "- Right-click to use items\n" +
                "- Press R to end your turn\n\n" +
                "Moving costs Speed points. Attacking costs 1 AP.\n" +
                "You can move AND attack in the same turn!"),
            new Page("Enemy Turns",
                "After you end your turn, each enemy acts. They may:\n" +
                "- Move toward you\n" +
                "- Attack if in range\n" +
                "- Use special abilities\n" +
                "- Flee if wounded\n\n" +
                "Watch enemy behavior patterns to plan your strategy!")
        )));
        basics.add(new Entry("Tile Types", "minecraft:grass_block", List.of(
            new Page("Arena Tiles",
                "Normal Tiles - Walk freely on grass, stone, sand, etc.\n\n" +
                "Obstacles - Trees, rocks, and walls block movement.\n\n" +
                "Water Tiles - Require a boat in your inventory to cross. " +
                "Enemies cannot cross water unless aquatic (like Drowned).")
        )));
        basics.add(new Entry("Weapons & Items", "minecraft:diamond_sword", List.of(
            new Page("Melee Weapons",
                "Swords & Axes - Attack adjacent enemies (range 1).\n\n" +
                "Damage scales with tier:\n" +
                "- Wood/Gold: base damage\n" +
                "- Stone: +1\n" +
                "- Iron: +2\n" +
                "- Diamond: +3\n" +
                "- Netherite: +4"),
            new Page("Ranged Weapons",
                "Bows & Crossbows - Range 3-4. Require arrows.\n\n" +
                "Tridents - Melee (range 1) or thrown (range 3).\n\n" +
                "Eggs & Snowballs - Low damage utility items.")
        )));
        CATEGORIES.add(new Category("Combat Basics", "minecraft:iron_sword",
            "Learn the fundamentals of grid combat.", basics));

        // === ENEMY BESTIARY ===
        List<Entry> enemies = new ArrayList<>();
        // Passive mobs
        enemies.add(mob("Cow", "Speed: 2 | Passive\n\nHarmless farm animal. Drops beef on defeat."));
        enemies.add(mob("Pig", "Speed: 2 | Passive\n\nHarmless farm animal. Drops porkchop on defeat."));
        enemies.add(mob("Sheep", "Speed: 2 | Passive\n\nHarmless farm animal. Drops mutton on defeat."));
        enemies.add(mob("Chicken", "Speed: 2 | Passive\n\nHarmless bird. Drops chicken on defeat."));
        enemies.add(mob("Horse", "Speed: 2 | Passive\n\nCan be tamed with golden items + saddle for +3 Speed mount!"));
        enemies.add(mob("Wolf", "Speed: 3 | Passive until hit\n\nTame with bone to gain an ally! Weak to Magic."));
        enemies.add(mob("Cat", "Speed: 2 | Passive\n\nTame with fish. Creepers avoid cats!"));
        enemies.add(mob("Fox", "Speed: 2 | Passive\n\nShy and quick. Will flee from you."));
        enemies.add(mob("Rabbit", "Speed: 2 | Passive\n\nTiny and fast. Harmless."));
        enemies.add(mob("Goat", "Speed: 2 | Passive\n\nCharges and rams! Weak to Sword."));
        enemies.add(mob("Parrot", "Speed: 2 | Passive\n\nColorful companion. Tame with seeds."));
        enemies.add(mob("Bee", "Speed: 2 | Passive until hit\n\nStings once then dies. Weak but annoying in groups."));
        enemies.add(mob("Cod", "Speed: 2 | Passive\n\nAquatic. Only found in water biomes."));
        // Hostile - Overworld
        enemies.add(mob("Zombie", "Speed: 2 | Range: 1\n\nBasic melee grunt. Baby zombies are faster. Armed variants hit harder.\n\nWeak to: Blunt, Cleaving"));
        enemies.add(mob("Husk", "Speed: 2 | Range: 1\n\nDesert zombie variant. Tougher in dry heat.\n\nWeak to: Blunt, Water"));
        enemies.add(mob("Drowned", "Speed: 2 | Range: 1-3\n\nAquatic zombie. Some carry tridents for ranged attacks.\n\nWeak to: Blunt, Cleaving\nResist: Water"));
        enemies.add(mob("Skeleton", "Speed: 2 | Range: 3\n\nRanged archer. Keeps distance and retreats if you close in.\n\nWeak to: Blunt\nResist: Ranged"));
        enemies.add(mob("Stray", "Speed: 2 | Range: 3\n\nFrozen skeleton variant. Slowness arrows!\n\nWeak to: Blunt\nResist: Ranged, Water"));
        enemies.add(mob("Creeper", "Speed: 2 | Special: Explode\n\nSneaks close then detonates! Massive AoE damage to everything nearby.\n\nWeak to: Ranged, Cleaving\nResist: Sword"));
        enemies.add(mob("Spider", "Speed: 3 | Size: 2x2\n\nFast 2x2 predator that pounces! Takes up 4 tiles.\n\nWeak to: Cleaving, Magic\nResist: Blunt"));
        enemies.add(mob("Pillager", "Speed: 2 | Range: 3\n\nCrossbow-wielding raider. Fires from distance.\n\nWeak to: Sword, Cleaving\nResist: Ranged"));
        enemies.add(mob("Vindicator", "Speed: 3 | Range: 1\n\nAxe-wielding berserker. Charges in aggressively!\n\nWeak to: Ranged\nResist: Cleaving"));
        enemies.add(mob("Evoker", "Speed: 2 | Range: 4\n\nSummons vex fangs from distance. High priority target!"));
        enemies.add(mob("Phantom", "Speed: 4 | Special: Swoop\n\nFlying enemy that swoops from distance, hits, and retreats.\n\nWeak to: Ranged\nResist: Sword"));
        // Hostile - Nether
        enemies.add(mob("Blaze", "Speed: 2 | Range: 3\n\nHovering ranged attacker. Fireball deals area damage.\n\nWeak to: Water\nResist: Magic, Ranged"));
        enemies.add(mob("Ghast", "Speed: 1 | Range: 5\n\nSlow but extreme range. Fireball explosions!\n\nWeak to: Ranged, Sword\nResist: Magic"));
        enemies.add(mob("Magma Cube", "Speed: 3 | Range: 1\n\nBouncy slime. Splits on death!\n\nWeak to: Water\nResist: Blunt"));
        enemies.add(mob("Wither Skeleton", "Speed: 3 | Range: 1\n\nRelentless pursuer with stone sword.\n\nWeak to: Blunt, Water\nResist: Magic"));
        enemies.add(mob("Hoglin", "Speed: 3 | Range: 1\n\nCharging beast. Hits hard!\n\nWeak to: Magic\nResist: Blunt"));
        enemies.add(mob("Piglin", "Speed: 2 | Range: 3\n\nCrossbow or gold sword. Trades if you have gold!\n\nWeak to: Magic, Water\nResist: Ranged"));
        enemies.add(mob("Zombified Piglin", "Speed: 2 | Range: 1\n\nNeutral until hit, then swarms!"));
        // Hostile - End
        enemies.add(mob("Enderman", "Speed: 5 | Special: Teleport\n\nTeleports anywhere! Very hard to run from.\n\nWeak to: Water, Magic\nResist: Ranged"));
        enemies.add(mob("Shulker", "Speed: 1 | Range: 4\n\nStationary turret. Levitation projectiles!\n\nWeak to: Blunt\nResist: Ranged, Sword"));
        enemies.add(mob("Witch", "Speed: 2 | Range: 4\n\nThrows harmful potions from long range.\n\nWeak to: Sword, Cleaving\nResist: Magic\nImmune: Water"));
        // Bosses
        enemies.add(mob("Warden", "Speed: 3 | BOSS | Special: Sonic Boom\n\nDeep Dark boss. Sonic boom ignores defense. Massive HP.\n\nWeak to: Ranged\nResist: Blunt, Sword"));
        enemies.add(mob("Ender Dragon", "Speed: 4 | BOSS | Special: Devastating Swoop\n\nFinal boss. Massive, fast, devastating. Triggers NG+!\n\nWeak to: Ranged, Magic\nResist: Water"));
        CATEGORIES.add(new Category("Enemy Bestiary", "minecraft:zombie_head",
            "Know your foes. Entries unlock as you encounter them.", enemies));

        // === LEVELING & STATS ===
        List<Entry> progression = new ArrayList<>();
        progression.add(new Entry("Leveling Up", "minecraft:experience_bottle", List.of(
            new Page("How to Level Up",
                "Defeat a Biome Boss (final level of each biome) to gain a Level Up.\n\n" +
                "Each level grants 1 Stat Point to allocate freely."),
            new Page("Emeralds",
                "Winning combat earns Emeralds. Amount scales with biome difficulty.\n\n" +
                "Boss bonus: +3 emeralds.\n\n" +
                "Spend at the Wandering Trader between levels!")
        )));
        progression.add(new Entry("Stats Guide", "minecraft:nether_star", List.of(
            new Page("Combat Stats",
                "Speed (base 3) - Tiles moved per turn. +1/point.\n\n" +
                "Action Points (base 3) - Actions per turn. +1/point.\n\n" +
                "Melee Power - +1 melee damage/point.\n\n" +
                "Ranged Power - +1 ranged damage/point."),
            new Page("Defensive & Utility",
                "Vitality - +2 max HP/point.\n\n" +
                "Defense - -1 incoming damage/point.\n\n" +
                "Luck - Better loot & crit chance.\n\n" +
                "Resourceful - +1 emerald/level & trader discounts."),
            new Page("Build Tips",
                "Glass Cannon: Max Power + AP for burst.\n\n" +
                "Tank: Vitality + Defense to survive.\n\n" +
                "Speedster: Speed + AP to outmaneuver.\n\n" +
                "Merchant: Resourceful + Luck for loot.\n\n" +
                "There's no wrong build - experiment!")
        )));
        CATEGORIES.add(new Category("Leveling & Stats", "minecraft:experience_bottle",
            "Grow stronger through combat.", progression));

        // === EQUIPMENT & ENCHANTMENTS ===
        List<Entry> equipment = new ArrayList<>();
        equipment.add(new Entry("Armor Sets", "minecraft:diamond_chestplate", List.of(
            new Page("Set Bonuses",
                "Wear a full matching armor set for combat bonuses!\n\n" +
                "Leather (Brawler): +2 Speed, +1 AP, +2 Physical dmg, 2x kill streak multiplier\n" +
                "Chainmail (Rogue): +1 Speed, -1 AP cost, +2 Sword dmg\n" +
                "Iron (Guard): +2 DEF, KB immune, +2 Cleaving dmg\n" +
                "Gold (Gambler): +15% crit, +1 emerald/kill, +2 Magic dmg"),
            new Page("Advanced Sets",
                "Diamond (Knight): +3 DEF, +1 ATK, +2 Blunt dmg\n" +
                "Netherite (Juggernaut): +4 DEF, +2 ATK, fire immune, +1 ALL dmg types\n" +
                "Turtle Helmet (Aquatic): Walk on water, +1 HP regen, +3 Water dmg\n\n" +
                "Each set has a Damage Type affinity - check the Damage Affinities panel in your inventory!")
        )));
        equipment.add(new Entry("Shield", "minecraft:shield", List.of(
            new Page("Shield Mechanics",
                "Equip a shield in your OFFHAND slot.\n\n" +
                "Passive: +2 Defense at all times.\n\n" +
                "Brace: When you End Turn (R), shield auto-braces for +5 total DEF during the enemy phase.\n\n" +
                "No AP cost - purely strategic! End turn early to tank hits.")
        )));
        equipment.add(new Entry("Enchantments", "minecraft:enchanted_book", List.of(
            new Page("Weapon Enchantments",
                "Enchantments are ACTIVE in combat!\n\n" +
                "Sharpness: +1 ATK per level\n" +
                "Smite/Bane: +2 ATK per level\n" +
                "Fire Aspect: Ignites enemies\n" +
                "Knockback: Extended push\n\n" +
                "Enchant your gear at an enchanting table before combat!"),
            new Page("Armor & Bow Enchantments",
                "Protection (all types): +1 DEF per 2 levels\n" +
                "Thorns: 15%/lvl chance to reflect damage\n\n" +
                "Power: +1 ranged ATK/lvl\n" +
                "Flame: Ignite with arrows\n" +
                "Infinity: Free arrow ammo\n\n" +
                "Mace: Density (+AoE), Breach (armor pen), Wind Burst (+KB range)")
        )));
        equipment.add(new Entry("Tipped Arrows", "minecraft:tipped_arrow", List.of(
            new Page("Arrow Effects",
                "Tipped arrows are consumed before regular arrows.\n\n" +
                "Poison: +2 bonus damage\n" +
                "Slowness: -2 enemy speed\n" +
                "Weakness: Stuns enemy\n" +
                "Harming: +4 bonus damage\n" +
                "Healing: Restores 4 HP to you\n" +
                "Fire Res: 3 turns protection\n\n" +
                "Infinity does NOT save tipped arrows!")
        )));
        CATEGORIES.add(new Category("Equipment", "minecraft:diamond_chestplate",
            "Armor sets, enchantments, and gear.", equipment));

        // === PETS & ALLIES ===
        List<Entry> pets = new ArrayList<>();
        pets.add(new Entry("Taming Mobs", "minecraft:bone", List.of(
            new Page("How to Tame",
                "Use a mob's breeding item on them during combat!\n\n" +
                "Combat allies (wolf, cat, horse, etc.) fight alongside you.\n" +
                "Passive mobs (cow, sheep, pig) are sent to your hub.\n\n" +
                "Each mob type has specific food:\n" +
                "Wolf: Bone/Beef | Cat: Fish | Horse: Golden Apple/Carrot"),
            new Page("Ally AI",
                "Tamed allies act during the enemy turn:\n" +
                "- Attack nearest enemy in range\n" +
                "- Retreat when HP drops below 2 hearts\n" +
                "- Move toward threats automatically\n\n" +
                "Heal allies with Hay Bales (+4 HP)!")
        )));
        pets.add(new Entry("Mounts", "minecraft:saddle", List.of(
            new Page("Mounting Pets",
                "Tame a horse/donkey/camel while holding a saddle.\n" +
                "The saddle is consumed and you mount immediately!\n\n" +
                "+3 bonus Speed while mounted.\n\n" +
                "The mount moves with you automatically.\n" +
                "Mountable: Horse, Donkey, Mule, Camel, Skeleton/Zombie Horse")
        )));
        pets.add(new Entry("Pet Armor", "minecraft:diamond_horse_armor", List.of(
            new Page("Pet Equipment",
                "Equip armor on pets BEFORE taming them.\n\n" +
                "Horse Armor bonuses:\n" +
                "Leather: +1 DEF\n" +
                "Iron: +2 DEF\n" +
                "Gold: +1 DEF, +1 ATK\n" +
                "Diamond: +3 DEF, +1 ATK\n\n" +
                "Wolf Armor: +2 DEF, +1 ATK")
        )));
        CATEGORIES.add(new Category("Pets & Allies", "minecraft:bone",
            "Tame mobs to fight alongside you.", pets));

        // === ITEMS & ABILITIES ===
        List<Entry> items = new ArrayList<>();
        items.add(new Entry("Combat Items", "minecraft:anvil", List.of(
            new Page("Offensive Items",
                "Anvil (1 AP): 5 DMG, drop on enemy\n" +
                "TNT (1 AP): AoE explosion, self-damage risk\n" +
                "Bell (2 AP): Stun all enemies in 2 tiles\n" +
                "Crossbow (2 AP): 3 DMG, 4-tile range, pierces\n" +
                "Trident (2 AP): 4 DMG, 3-tile range, returns\n" +
                "Spore Blossom (1 AP): AoE -1 Speed to enemies"),
            new Page("Defensive & Utility",
                "Totem (Passive): Auto-revive on death!\n" +
                "Goat Horn (1 AP): Taunt all enemies\n" +
                "Echo Shard (1 AP): Teleport back to start\n" +
                "Spyglass (1 AP): See enemy stats\n" +
                "Compass (1 AP): Reveal all positions\n" +
                "Brush (1 AP): Dig random loot from tiles")
        )));
        items.add(new Entry("Tile Effects", "minecraft:campfire", List.of(
            new Page("Placed Items",
                "Some items create lasting tile effects:\n\n" +
                "Lava Bucket: 3 fire DMG/turn to enemies\n" +
                "Campfire: +1 HP/turn when adjacent\n" +
                "Honey Block: Enemies lose all movement\n" +
                "Lightning Rod: 4 AoE DMG next turn\n" +
                "Cactus: 1 DMG/turn to adjacent enemies\n" +
                "Banner: +2 DEF to allies within 2 tiles\n" +
                "Cake: Heals 2 HP, 3 uses")
        )));
        items.add(new Entry("Terrain Tools", "minecraft:iron_pickaxe", List.of(
            new Page("Modify the Arena",
                "Water Bucket: Place water tile (fishable!)\n" +
                "Sponge: Absorb adjacent water\n" +
                "Pickaxe: Break obstacle, make walkable\n" +
                "Scaffolding: +1 range from this tile\n" +
                "Fishing Rod (3 AP): Random loot from water\n\n" +
                "Boats: Cross water tiles (consumed on entry)")
        )));
        CATEGORIES.add(new Category("Items & Abilities", "minecraft:anvil",
            "Every usable item explained.", items));

        // === ARMOR TRIMS ===
        List<Entry> trims = new ArrayList<>();
        trims.add(new Entry("How Trims Work", "minecraft:coast_armor_trim_smithing_template", List.of(
            new Page("Trim Combat Effects",
                "Armor trims grant COMBAT BONUSES in Craftics!\n\n" +
                "Each trimmed armor piece gives a stackable per-piece bonus.\n" +
                "Wearing 4 pieces with the SAME trim activates a powerful Full Set Bonus.\n\n" +
                "Trim templates drop from biome bosses (~35% chance).\n" +
                "Apply trims at a Smithing Table in the hub."),
            new Page("Overworld Trims",
                "Sentry: +1 Ranged Power /piece\n  Full Set: Overwatch (counter-attack ranged)\n\n" +
                "Dune: +1 Blunt Power /piece\n  Full Set: Sandstorm (-1 Speed aura)\n\n" +
                "Coast: +1 Water Power /piece\n  Full Set: Tidal (water heals)\n\n" +
                "Wild: +1 AP /piece\n  Full Set: Feral (1.3x kill streak dmg)"),
            new Page("Overworld Trims (cont.)",
                "Wayfinder: +1 Speed /piece\n  Full Set: Pathfinder (ignore obstacles)\n\n" +
                "Shaper: +1 Defense /piece\n  Full Set: Terraformer (free barrier/turn)\n\n" +
                "Raiser: +1 ally damage /piece\n  Full Set: Rally (allies +2 Spd, +1 Atk)\n\n" +
                "Host: +2 max HP /piece\n  Full Set: Symbiote (heal 1 HP/kill)\n\n" +
                "Tide: +1 HP regen per 2 turns /piece\n  Full Set: Ocean's Blessing (emergency heal)"),
            new Page("Nether Trims",
                "Ward: +1 Defense /piece\n  Full Set: Fortress (50% less dmg when stationary)\n\n" +
                "Snout: +1 Cleaving Power /piece\n  Full Set: Brute Force (splash damage)\n\n" +
                "Rib: +1 Magic Power /piece\n  Full Set: Infernal (fire +3 bonus dmg)\n\n" +
                "Eye: +1 Attack Range /piece\n  Full Set: All-Seeing (see enemy stats)"),
            new Page("End & Trial Trims",
                "Spire: +1 Luck /piece\n  Full Set: Fortune's Peak (double emeralds)\n\n" +
                "Vex: Ignore 1 enemy DEF /piece\n  Full Set: Ethereal (20% dodge)\n\n" +
                "Silence: +1 stealth range /piece\n  Full Set: Phantom (invisible 2 turns)\n\n" +
                "Flow: +1 Speed /piece\n  Full Set: Current (kills refund 1 AP)\n\n" +
                "Bolt: +1 Sword Power /piece\n  Full Set: Thunderstrike (crits stun)"),
            new Page("Trim Materials",
                "The MATERIAL you use to apply a trim also grants a bonus!\n\n" +
                "Iron: +1 Defense /piece\n" +
                "Copper: +1 Speed /piece\n" +
                "Gold: +1 Luck /piece\n" +
                "Lapis: +1 Magic Power /piece\n" +
                "Emerald: +1 AP /piece"),
            new Page("Trim Materials (cont.)",
                "Diamond: +1 Melee Power /piece\n" +
                "Netherite: +1 Armor Pen /piece\n" +
                "Redstone: +1 Ranged Power /piece\n" +
                "Amethyst: +1 HP Regen /piece\n" +
                "Quartz: +2 Max HP /piece\n\n" +
                "Pattern + Material stack! Mix for your build.")
        )));
        CATEGORIES.add(new Category("Armor Trims", "minecraft:coast_armor_trim_smithing_template",
            "Trim combat bonuses and full set effects.", trims));

        // === EVENTS ===
        List<Entry> events = new ArrayList<>();
        events.add(new Entry("Wandering Trader", "minecraft:emerald", List.of(
            new Page("Trading Post",
                "~13% chance between levels.\n\n" +
                "A wandering trader appears in a biome-themed bazaar.\n" +
                "Trader types: Weaponsmith, Armorer, Alchemist, and more.\n\n" +
                "Trades scale with biome tier. Spend emeralds for gear!")
        )));
        events.add(new Entry("Trial Chamber", "minecraft:trial_key", List.of(
            new Page("Optional Challenge",
                "~10% chance between levels.\n\n" +
                "Trial chamber with Breeze, Bogged, Stray, and more " +
                "in a compact tuff brick arena.\n\n" +
                "OPTIONAL: Skip or accept for rare loot.\n" +
                "Rewards: Trial Keys, Mace, Wind Charges, Breeze Rods!")
        )));
        events.add(new Entry("Ambush", "minecraft:iron_sword", List.of(
            new Page("Surprise Attack!",
                "~10% chance between levels.\n\n" +
                "UNAVOIDABLE! 2-3 fast enemies surround you in a " +
                "small dirt arena. No escape!\n\n" +
                "Quick fight with small emerald reward on victory.\n" +
                "Stay alert — always keep healing items ready!")
        )));
        events.add(new Entry("Shrine of Fortune", "minecraft:gold_block", List.of(
            new Page("Emerald Gamble",
                "~7% chance between levels.\n\n" +
                "A mysterious shrine offers to trade 3-5 emeralds for " +
                "a random reward.\n\n" +
                "Possible outcomes:\n" +
                "- Consumables (common)\n" +
                "- Gear like shields/swords (uncommon)\n" +
                "- Diamond gear or totems (rare)\n" +
                "- JACKPOT: Triple emeralds back! (10%)")
        )));
        events.add(new Entry("Wounded Traveler", "minecraft:bread", List.of(
            new Page("Act of Kindness",
                "~6% chance between levels.\n\n" +
                "A wounded traveler begs for food. Give any food item " +
                "to receive a random reward.\n\n" +
                "Rewards range from emeralds and arrows to diamonds, " +
                "saddles, and even a Totem of Undying!")
        )));
        events.add(new Entry("Treasure Vault", "minecraft:gold_block", List.of(
            new Page("Hidden Riches",
                "~4% chance between levels.\n\n" +
                "A vault with NO enemies — just free loot!\n" +
                "OPTIONAL: Skip or enter to claim 3-5 random items.\n\n" +
                "The vault floor is pure gold. " +
                "Items include trial chamber loot pool rewards.")
        )));
        events.add(new Entry("Ominous Trial", "minecraft:ominous_trial_key", List.of(
            new Page("Ultimate Challenge",
                "~5% chance — LATE GAME ONLY (biome 10+).\n\n" +
                "An ominous trial with a WARDEN boss, Breeze, and " +
                "elite trial mobs on deepslate.\n\n" +
                "OPTIONAL but EXTREMELY dangerous.\n" +
                "Rewards: 2-3 legendary items — netherite gear, " +
                "ominous trial keys, heavy cores!")
        )));
        CATEGORIES.add(new Category("Random Events", "minecraft:trial_key",
            "Events that appear between levels.", events));
    }
}
