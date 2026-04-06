package com.crackedgames.craftics.client.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All guide book content. Categories contain entries, entries contain pages.
 * Tracks which entries are unlocked (for bestiary discovery).
 * Unlock state is server-authoritative: synced via GuideBookSyncPayload on join
 * and whenever the server unlocks a new entry.
 */
public class GuideBookData {

    public record Page(String title, String text) {}
    public record Entry(String name, String iconItem, List<Page> pages) {}
    public record Category(String name, String iconItem, String description, List<Entry> entries) {}

    private static final List<Category> CATEGORIES = new ArrayList<>();
    private static final java.util.Set<String> unlockedEntries = new java.util.HashSet<>();
    /** Default entries that are always unlocked (everything except Bestiary and Armor Trims). */
    private static final java.util.Set<String> defaultUnlocks = new java.util.HashSet<>();

    static {
        buildContent();
        // All categories unlocked except Bestiary and Armor Trims
        for (int i = 0; i < CATEGORIES.size(); i++) {
            String catName = CATEGORIES.get(i).name();
            if (catName.equals("Enemy Bestiary") || catName.equals("Armor Trims")) continue;
            for (Entry e : CATEGORIES.get(i).entries()) {
                defaultUnlocks.add(e.name());
                unlockedEntries.add(e.name());
            }
        }
    }

    public static List<Category> getCategories() { return CATEGORIES; }

    public static boolean isUnlocked(String entryName) {
        return unlockedEntries.contains(entryName);
    }

    /**
     * Apply a full sync from the server. Replaces all non-default unlock state
     * with the server-provided set. Called when GuideBookSyncPayload is received.
     * @param pipeDelimited pipe-separated entry names from server (bestiary + trims only)
     */
    public static void applySyncFromServer(String pipeDelimited) {
        // Reset to defaults only
        unlockedEntries.clear();
        unlockedEntries.addAll(defaultUnlocks);
        // Add server-provided entries
        if (pipeDelimited != null && !pipeDelimited.isEmpty()) {
            for (String entry : pipeDelimited.split("\\|")) {
                if (!entry.isEmpty()) unlockedEntries.add(entry);
            }
        }
    }

    /**
     * Local-only unlock for immediate UI feedback during combat.
     * The server is authoritative — this just prevents a visual delay.
     */
    public static void unlockLocal(String entryName) {
        unlockedEntries.add(entryName);
    }

    /** Unlock a bestiary entry locally by mob type ID (for immediate UI feedback during combat). */
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
        unlockLocal(sb.toString());
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
                "Tridents - Melee stab (1 AP) when adjacent.\n" +
                "Throw (2 AP) in straight/diagonal lines.\n" +
                "Lodges in ground — walk to retrieve.\n" +
                "Loyalty: auto-returns. Riptide: dash.\n" +
                "Channeling: lightning strike on throw.\n\n" +
                "Eggs & Snowballs - Low damage utility items."),
            new Page("Special & Pet Weapons",
                "Hoes - Special damage type. Low base damage but\n" +
                "boosted by Special affinity. The main melee\n" +
                "for effect-based builds.\n\n" +
                "Shovels - Pet damage type. Moderate damage\n" +
                "boosted by Pet affinity. The main melee\n" +
                "for pet/companion builds.")
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
        enemies.add(mob("Wolf", "Speed: 3 | Passive until hit\n\nTame with bone to gain an ally! Weak to Special."));
        enemies.add(mob("Cat", "Speed: 2 | Passive\n\nTame with fish. Creepers avoid cats!"));
        enemies.add(mob("Fox", "Speed: 2 | Passive\n\nShy and quick. Will flee from you."));
        enemies.add(mob("Rabbit", "Speed: 2 | Passive\n\nTiny and fast. Harmless."));
        enemies.add(mob("Goat", "Speed: 2 | Passive\n\nCharges and rams! Weak to Slashing."));
        enemies.add(mob("Parrot", "Speed: 2 | Passive\n\nColorful companion. Tame with seeds."));
        enemies.add(mob("Bee", "Speed: 2 | Passive until hit\n\nStings once then dies. Weak but annoying in groups."));
        enemies.add(mob("Cod", "Speed: 2 | Passive\n\nAquatic. Only found in water biomes."));
        // Hostile - Overworld
        enemies.add(mob("Zombie", "Speed: 2 | Range: 1\n\nBasic melee grunt. Baby zombies are faster. Armed variants hit harder.\n\nWeak to: Blunt, Cleaving"));
        enemies.add(mob("Husk", "Speed: 2 | Range: 1\n\nDesert zombie variant. Tougher in dry heat.\n\nWeak to: Blunt, Water"));
        enemies.add(mob("Drowned", "Speed: 2 | Range: 1-3\n\nAquatic zombie. 50% spawn with tridents; they throw diagonally at range ≤3. Non-trident drowns are pure melee.\n\nWeak to: Blunt, Cleaving\nResist: Water"));
        enemies.add(mob("Skeleton", "Speed: 2 | Range: 3\n\nRanged archer. Keeps distance and retreats if you close in.\n\nWeak to: Blunt\nResist: Ranged"));
        enemies.add(mob("Stray", "Speed: 2 | Range: 3\n\nFrozen skeleton variant. Slowness arrows!\n\nWeak to: Blunt\nResist: Ranged, Water"));
        enemies.add(mob("Creeper", "Speed: 2 | Special: Explode\n\nSneaks close then detonates! Massive AoE damage to everything nearby.\n\nWeak to: Ranged, Cleaving\nResist: Slashing"));
        enemies.add(mob("Spider", "Speed: 3 | Size: 2x2\n\nFast 2x2 predator that pounces! Takes up 4 tiles.\n\nWeak to: Cleaving, Special\nResist: Blunt"));
        enemies.add(mob("Pillager", "Speed: 2 | Range: 3\n\nCrossbow-wielding raider. Fires from distance.\n\nWeak to: Slashing, Cleaving\nResist: Ranged"));
        enemies.add(mob("Vindicator", "Speed: 3 | Range: 1\n\nAxe-wielding berserker. Charges in aggressively!\n\nWeak to: Ranged\nResist: Cleaving"));
        enemies.add(mob("Evoker", "Speed: 2 | Range: 4\n\nSummons vex fangs from distance. High priority target!"));
        enemies.add(mob("Phantom", "Speed: 4 | Special: Swoop\n\nFlying enemy that swoops from distance, hits, and retreats.\n\nWeak to: Ranged\nResist: Slashing"));
        // Hostile - Nether
        enemies.add(mob("Blaze", "Speed: 2 | Range: 3\n\nHovering ranged attacker. Fireball deals area damage.\n\nWeak to: Water\nResist: Special, Ranged"));
        enemies.add(mob("Ghast", "Speed: 1 | Range: 5\n\nSlow but extreme range. Fireball explosions!\n\nWeak to: Ranged, Slashing\nResist: Special"));
        enemies.add(mob("Magma Cube", "Speed: 3 | Range: 1\n\nBouncy slime. Splits on death!\n\nWeak to: Water\nResist: Blunt"));
        enemies.add(mob("Wither Skeleton", "Speed: 3 | Range: 1\n\nRelentless pursuer with stone sword.\n\nWeak to: Blunt, Water\nResist: Special"));
        enemies.add(mob("Hoglin", "Speed: 3 | Range: 1\n\nCharging beast. Hits hard!\n\nWeak to: Special\nResist: Blunt"));
        enemies.add(mob("Piglin", "Speed: 2 | Range: 3\n\nCrossbow or gold sword. Trades if you have gold!\n\nWeak to: Special, Water\nResist: Ranged"));
        enemies.add(mob("Zombified Piglin", "Speed: 2 | Range: 1\n\nNeutral until hit, then swarms!"));
        // Hostile - End
        enemies.add(mob("Enderman", "Speed: 5 | Special: Teleport\n\nTeleports anywhere! Very hard to run from.\n\nWeak to: Water, Special\nResist: Ranged"));
        enemies.add(mob("Shulker", "Speed: 1 | Range: 4\n\nStationary turret. Levitation projectiles!\n\nWeak to: Blunt\nResist: Ranged, Slashing"));
        enemies.add(mob("Witch", "Speed: 2 | Range: 4\n\nThrows harmful potions from long range.\n\nWeak to: Slashing, Cleaving\nResist: Special\nImmune: Water"));
        // Bosses
        enemies.add(mob("The Revenant", "BOSS | Zombie | 20HP / 4ATK / 2DEF / Speed 2\nPlains biome boss.\n\n" +
            "Abilities:\n- Raise the Dead: Summons 1-2 Zombies every 3 turns\n- Death Charge: 3-tile line from center, ATK+2\n- Gravefire Grid: Telegraphs a magma checker-grid for 1 turn\n- Shield Bash: Knockback 2 tiles\n\n" +
            "Phase 2 — Undying Rage: Regeneration, faster summons, fire trail on charge."));
        enemies.add(mob("Sandstorm Pharaoh", "BOSS | Husk | 25HP / 6ATK / 1DEF / Speed 2\nDesert biome boss.\n\n" +
            "Abilities:\n- Plant Mine: Invisible mine, 6 dmg on contact\n- Sand Burial: 2x2 quicksand stun\n- Sandstorm: 3x3 AoE + accuracy debuff\n- Curse of the Sands: Tiles you leave become quicksand\n\n" +
            "Phase 2 — Tomb Wrath: 2 mines/turn, 3x3 burial, summons 2 Husks."));
        enemies.add(mob("Frostbound Huntsman", "BOSS | Stray | 25HP / 5ATK / 2DEF / Range 4 / Speed 2\nSnowy Tundra boss.\n\n" +
            "Abilities:\n- Blizzard: 3x3 AoE + frozen tiles (1-turn stun)\n- Frost Arrow: Range 4, ATK + Slowness\n- Ice Wall: Creates obstacle line\n\n" +
            "- Harpoon Pull: telegraphed pull 2 tiles toward the boss\n- Whiteout Ring: ring burst with one safe gap\n\n" +
            "Phase 2 — Permafrost: Speed 3, random frozen tiles every 2 turns."));
        enemies.add(mob("The Rockbreaker", "BOSS | Vindicator | 30HP / 6ATK / 3DEF / Speed 2\nStony Peaks boss.\n\n" +
            "Abilities:\n- Seismic Slam: Cross pattern, 5 dmg\n- Boulder Toss: Range 4, creates obstacle\n- Fortify: +5 DEF for 2 turns\n- Avalanche: Full-row attack\n\n" +
            "Phase 2: Permanent +3 DEF, 2 boulders, 2 rows avalanche."));
        enemies.add(mob("The Hexweaver", "BOSS | Evoker | 28HP / 5ATK / 2DEF / Range 4 / Speed 2\nDark Forest boss.\n\n" +
            "Abilities:\n- Hex Snare: telegraphed curse + 2-tile pull\n- Runic Prison: cardinal runes erupt + brief cage\n- Vex Swarm: Summons 2 Vexes every 3 turns\n- Cursed Fog: 3x3 debuff zone\n- Hex Bolt: Ranged ATK + Slowness\n\n" +
            "Phase 2 — Arcane Fury: Teleports away, full cross fangs, 3 Vexes."));
        enemies.add(mob("The Hollow King", "BOSS | Zombie | 40HP / 7ATK / 3DEF / Speed 2\nCaverns boss.\n\n" +
            "Abilities:\n- Demolition Cache: telegraphed TNT that detonates next round\n- Rubble Toss: mines obstacle, throws it at marked tile\n- Cave-In: Boulders fall on tiles\n- Miner's Fury: Line charge destroys obstacles\n- Summon Silverfish from rubble\n- Lights Out: casts darkness (place Torches/Lanterns to negate)\n\n" +
            "Phase 2 — Total Collapse: permanent darkness, auto cave-ins, 3 TNT charges. COUNTERPLAY: Bring Torches, Lanterns, or Campfires to maintain light zones."));
        enemies.add(mob("The Broodmother", "BOSS | Spider | 35HP / 6ATK / 2DEF / Speed 3 | Size 3x3\nJungle boss.\n\n" +
            "Abilities:\n- Spawn Brood: 2-3 Cave Spiders from egg sacs\n- Web Spray: 3x3 stun + slow\n- Venomous Bite: ATK + Poison\n- Pounce: Leap 3 tiles, 2x2 AoE\n\n" +
            "Phase 2 — Nest Awakening: +2 Speed, respawning egg sacs."));
        enemies.add(mob("The Tidecaller", "BOSS | Drowned | 30HP / 5ATK / 2DEF / Range 3 / Speed 2\nRiver Delta boss.\n\n" +
            "Abilities:\n- Tidal Wave: 2-tile-wide flood column\n- Trident Storm: 3 tridents in spread\n- Riptide Charge: Water charge, knockback 2\n- Call of the Deep: Summon Drowned on water\n\n" +
            "Phase 2 — Deluge: Half arena floods permanently, +2 ATK on water."));
        enemies.add(mob("The Molten King", "BOSS | Magma Cube | 35HP / 7ATK / 2DEF / Speed 2\nNether Wastes boss.\n\n" +
            "Abilities:\n- Eruption: Ring of fire AoE around self\n- Lava Trail: Leaves fire on tiles moved\n- Absorb: Merges with nearby cube to heal\n\n" +
            "Phase 2 — Meltdown: Permanent fire tiles, constant eruptions."));
        enemies.add(mob("The Bastion Brute", "BOSS | Skeleton (Piglin wargear) | 45HP / 8ATK / 3DEF / Speed 3\nCrimson Forest boss.\n\n" +
            "Abilities:\n- Gore Charge: 4-tile charge, ATK+3, knockback 3\n- Fungal Growth: 3x3 heal zone\n- Rampage: AoE all adjacent tiles\n- Summon Pack: 2 Piglins (once)\n\n" +
            "Phase 2 — Blood Frenzy: +4 ATK, fire trail, 2-tile rampage, speed 4."));
        enemies.add(mob("Wailing Revenant", "BOSS | Ghast | 60HP / 8ATK / 2DEF / Stationary\nSoul Sand Valley boss.\n\n" +
            "Hovers outside the arena edge — attack the front row to hit it.\nNo regular ghasts spawn; only Wither Skeletons.\n\n" +
            "Abilities:\n- Fireball Barrage: 3 fireballs fly across the arena\n- Raining Fireballs: Half the arena warned, 5 dmg each\n- Magma Rows: Random rows turn to magma for 2 turns\n- Summon Wither Skeletons: 2 skeletons (max 4)\n\n" +
            "Phase 2 — Requiem: 5 fireballs, 2 magma rows, 3 skeletons."));
        enemies.add(mob("Ashen Warlord", "BOSS | Wither Skeleton | 55HP / 10ATK / 4DEF / Speed 3\nBasalt Deltas boss.\n\n" +
            "Abilities:\n- Wither Slash: ATK + permanent max HP reduction\n- Summon Blaze Guard: 2 Blazes every 4 turns\n\n" +
            "Phase 2 — Warlord's Command: Arc wither slash, summons Wither Skeletons instead, speed 4."));
        enemies.add(mob("The Void Walker", "BOSS | Enderman | 50HP / 9ATK / 2DEF / Speed 3\nWarped Forest boss.\n\n" +
            "Abilities:\n- Void Rift: Portal pair (step on one, teleport to other)\n- Mirror Image: 2 decoy clones\n- Phase Strike: Teleport behind player + attack\n- Void Pull: Pulls player 2 tiles toward boss\n\n" +
            "Phase 2 — Reality Shatter: Permanent rifts, 3 clones, pull range 3."));
        enemies.add(mob("Shulker Architect", "BOSS | Shulker | 50HP / 9ATK / 4DEF / Range 5 / Speed 1\nEnd City boss.\n\n" +
            "Abilities:\n- Bullet Storm: 4 homing bullets + Levitation\n- Deploy Turret: Stationary shulker turret (max 3)\n- Fortify Shell: 80% damage reduction 1 turn\n- Teleport Link: Teleport to turret position\n\n" +
            "Phase 2 — Defense Protocol: 6 bullets, reflect shell, turret limit 5."));
        enemies.add(mob("The Chorus Mind", "BOSS | Enderman | 60HP / 12ATK / 3DEF / Speed 2\nChorus Grove boss.\n\n" +
            "Abilities:\n- Chorus Bloom: Grow obstacle plants, teleport to any\n- Entangle: Root area, immobilize + damage\n- Chorus Bomb: AoE + random teleport on hit\n- Resonance Cascade: All plants pulse AoE damage\n\n" +
            "Phase 2 — Overgrowth: Auto-spread plants, auto cascade, boss teleports each turn."));
        enemies.add(mob("The Void Herald", "BOSS | Enderman | 55HP / 10ATK / 3DEF / Speed 3\nOuter End Islands boss.\n\n" +
            "Abilities:\n- Void Gale: Push all entities toward void edge\n- Lightning Strike: Mark tile, + pattern 6 dmg next turn\n- Platform Collapse: Permanently remove 2x2 floor\n- Blink Assault: Teleport + hit 3 tiles\n\n" +
            "Phase 2 — Oblivion: Auto collapse, gale 3 tiles, 2 lightning marks, speed 4."));
        enemies.add(mob("The Wither", "BOSS | Wither | 65HP / 8ATK / 5DEF / Range 5 / Speed 2\nBasalt Deltas final boss.\n\n" +
            "Abilities:\n- Wither Skull Barrage: 3 destroyable skull projectiles\n- Decay Aura: Tiles near Wither become fire\n- Summon Wither Skeletons (max 4)\n- Charge: 4-tile dash, ATK+3\n\n" +
            "Phase 2 — Wither Armor: Immune to ranged, transition explosion, 5 skulls, larger decay."));
        enemies.add(mob("Warden", "BOSS | Warden | Speed 3\nDeep Dark boss. Sonic boom ignores defense. Massive HP.\n\nWeak to: Ranged\nResist: Blunt, Slashing"));
        enemies.add(mob("Ender Dragon", "BOSS | Ender Dragon | Speed 4\nFinal boss. Massive, fast, devastating. Triggers NG+!\n\nWeak to: Ranged, Special\nResist: Water"));
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
                "Luck - +2%/pt to ALL combat procs, crits & loot.\n\n" +
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
                "Chainmail (Rogue): +1 Speed, -1 AP cost, +2 Slashing dmg\n" +
                "Iron (Guard): +2 DEF, KB immune, +2 Cleaving dmg\n" +
                "Gold (Gambler): +15% crit, +1 emerald/kill, +2 Special dmg"),
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
            new Page("Sword Enchantments",
                "Sharpness: +1 ATK/lvl + applies Bleed stacks. Each stack = +1 bonus damage when enemy is attacked.\n\n" +
                "Smite: AoE radiant burst vs undead. All undead in radius take bonus damage.\n\n" +
                "Bane of Arthropods: Injects venom into arthropods — Poison + Slowness. Spreads on death.\n\n" +
                "Fire Aspect: Cone of fire in swing direction. Burns all enemies in the cone."),
            new Page("Sword Enchantments (2)",
                "Knockback: Directional shockwave. Pushes target + enemies behind them. Wall collision = bonus damage.\n\n" +
                "Sweeping Edge: 360° spin hitting ALL adjacent enemies. Lv1: 60%, Lv2: 75%, Lv3: 90% damage + knockback.\n\n" +
                "Looting: Extra loot drops on kill (+50%/+100%/+150%)."),
            new Page("Bow & Crossbow Enchantments",
                "Power: +damage and +range per level.\n\n" +
                "Flame: Burns target and all adjacent enemies.\n\n" +
                "Infinity: Never consume arrows (only need 1).\n\n" +
                "Punch: Radial knockback burst on impact. Collision damage.\n\n" +
                "Quick Charge: Reduces crossbow AP cost.\n" +
                "Multishot: 3 bolts in a fan (45° angles).\n" +
                "Piercing: Bolts pierce through enemies + inflict Bleed."),
            new Page("Mace Enchantments",
                "Density: Gravity well — pulls nearby enemies to impact point. Crushing bonus damage.\n\n" +
                "Breach: Permanently reduces target defense per hit. Stacks all combat.\n\n" +
                "Wind Burst: Shockwave knockback on all adjacent + buffs next Mace hit damage."),
            new Page("Trident Enchantments",
                "Impaling: +damage per level + inflicts Bleed stacks.\n\n" +
                "Channeling: Chain lightning on throw hit. Prioritizes Soaked enemies (2x damage). Lv1=1, Lv2=3, Lv3=5 chains.\n\n" +
                "Loyalty: Trident ricochets to nearby enemies before returning. +1 ricochet per level (50% damage).\n\n" +
                "Riptide: Dash through enemies instead of throwing."),
            new Page("Armor Enchantments",
                "Protection: +1 DEF per 2 levels.\n\n" +
                "Fire Protection: Reduces fire damage (25%/50%/75%/100%).\n\n" +
                "Thorns: Guaranteed damage reflection (15%/25%/35% of damage taken).\n\n" +
                "Feather Falling: Knockback immunity. Attackers recoil off you.")
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
                "Trident: Melee (1 AP) or Throw (2 AP)\n" +
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
                "Campfire: +1 HP/turn when adjacent + light zone (radius 3)\n" +
                "Honey Block: Enemies lose all movement\n" +
                "Lightning Rod: 4 AoE DMG next turn\n" +
                "Cactus: 1 DMG/turn to adjacent enemies\n" +
                "Banner: +2 DEF to allies within 2 tiles\n" +
                "Cake: Heals 2 HP, 3 uses"),
            new Page("Light Sources (vs Darkness)",
                "Place to negate darkness effects:\n\n" +
                "Torch: Light radius 2 (small, fast)\n" +
                "Lantern: Light radius 3 + enemy detection\n" +
                "Campfire: Light radius 3 + healing\n\n" +
                "Useful against bosses like The Hollow King in Phase 2 when darkness is permanent.")
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
                "Rib: +1 Special Power /piece\n  Full Set: Infernal (fire +3 bonus dmg)\n\n" +
                "Eye: +1 Attack Range /piece\n  Full Set: All-Seeing (see enemy stats)"),
            new Page("End & Trial Trims",
                "Spire: +1 Luck /piece\n  Full Set: Fortune's Peak (double emeralds)\n\n" +
                "Vex: Ignore 1 enemy DEF /piece\n  Full Set: Ethereal (20% dodge)\n\n" +
                "Silence: +1 stealth range /piece\n  Full Set: Phantom (invisible 2 turns)\n\n" +
                "Flow: +1 Speed /piece\n  Full Set: Current (kills refund 1 AP)\n\n" +
                "Bolt: +1 Slashing Power /piece\n  Full Set: Thunderstrike (crits stun)"),
            new Page("Trim Materials",
                "The MATERIAL you use to apply a trim also grants a bonus!\n\n" +
                "Iron: +1 Defense /piece\n" +
                "Copper: +1 Speed /piece\n" +
                "Gold: +1 Luck /piece\n" +
                "Lapis: +1 Special Power /piece\n" +
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
