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

    /**
     * Reset unlock state to defaults. Called on client disconnect so bestiary
     * / armor-trim unlocks from the previous world don't bleed into the next.
     * The server resyncs via GuideBookSyncPayload on Craftics-world JOIN.
     */
    public static void resetToDefaults() {
        unlockedEntries.clear();
        unlockedEntries.addAll(defaultUnlocks);
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
                "Lodges in ground, walk to retrieve.\n" +
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
        enemies.add(mob("Llama", "Speed: 2 | Range: 2 | Passive until hit\n\nAttacking a llama makes it permanently aggressive. It backs up to keep distance 2 and spits at you. Found in Mountain biomes.\n\nWeak to: Slashing"));
        // Hostile - Overworld
        enemies.add(mob("Zombie", "Speed: 2 | Range: 1\n\nBasic melee grunt. Baby zombies are faster. Armed variants hit harder.\n\nWeak to: Blunt, Cleaving"));
        enemies.add(mob("Zombie Villager", "Speed: 2 | Range: 1\n\nZombified villager. Fights identically to a regular Zombie and shares its horde bonus (+1 ATK per adjacent zombie/husk/drowned). Spawns in Plains.\n\nWeak to: Blunt, Cleaving"));
        enemies.add(mob("Husk", "Speed: 2 | Range: 1\n\nDesert zombie variant. Tougher in dry heat.\n\nWeak to: Blunt, Water"));
        enemies.add(mob("Drowned", "Speed: 2 | Range: 1-3\n\nAquatic zombie. 50% spawn with tridents; they throw diagonally at range ≤3. Non-trident drowns are pure melee.\n\nWeak to: Blunt, Cleaving\nResist: Water"));
        enemies.add(mob("Skeleton", "Speed: 2 | Range: 3\n\nRanged archer. Keeps distance and retreats if you close in.\n\nWeak to: Blunt\nResist: Ranged, Physical"));
        enemies.add(mob("Stray", "Speed: 2 | Range: 3\n\nFrozen skeleton variant. Slowness arrows!\n\nWeak to: Blunt\nResist: Ranged, Water, Physical"));
        enemies.add(mob("Creeper", "Speed: 2 | Special: Explode\n\nSneaks close then detonates! Massive AoE damage to everything nearby.\n\nWeak to: Ranged, Cleaving\nResist: Slashing, Physical"));
        enemies.add(mob("Spider", "Speed: 3 | Size: 2x2\n\nAmbush predator that pounces from distance or drops from the ceiling. Chooses between attacking and shooting cobwebs to slow you or block escape routes.\n\nWeak to: Cleaving, Special\nResist: Blunt, Physical"));
        enemies.add(mob("Cave Spider", "Speed: 3 | Range: 1\n\nSmaller, faster spider variant that poisons on hit. Pounces 2 tiles (one shorter than regular Spider) and can drop on you from the ceiling within 2 tiles. More aggressive in Jungle and Deep Dark biomes.\n\nWeak to: Cleaving, Special\nResist: Blunt, Physical"));
        enemies.add(mob("Silverfish", "Speed: 3 | Range: 1\n\nFlanking swarmer. Always tries to approach from the opposite side of other enemies. Low damage but attacks the instant it reaches you. Don't let a pack surround you. Spawns in Deep Dark.\n\nWeak to: Slashing\nResist: Ranged"));
        enemies.add(mob("Pillager", "Speed: 2 | Range: 3\n\nCrossbow-wielding raider. Fires from distance.\n\nWeak to: Slashing, Cleaving\nResist: Ranged, Physical"));
        enemies.add(mob("Vindicator", "Speed: 3 | Range: 1\n\nRook-movement axe berserker. Dashes in straight lines like a chess rook! Charge damage scales with distance traveled. When enraged (+50% ATK), attacks also knock you back.\n\nWeak to: Ranged\nResist: Cleaving, Physical"));
        enemies.add(mob("Ravager", "Speed: 2 | Range: 1 | Size 2x2 | 30HP / 6ATK / 2DEF\n\nBull Rush: charges up to 3 tiles in a straight line for heavy damage + knockback 2. If it can't charge, stomps every adjacent tile (AoE around itself). Always aggressive, very tanky. Bring ranged. Spawns in Forest and Mountain.\n\nWeak to: Ranged, Water\nResist: Blunt, Physical"));
        enemies.add(mob("Evoker", "Speed: 2 | Range: 4 | 7HP / 3ATK\n\nFragile illager caster. Summons vex fangs in a line from distance. Low HP but devastating if ignored. Prioritize over front-line raiders whenever possible.\n\nWeak to: Slashing, Cleaving\nResist: Special, Physical"));
        enemies.add(mob("Phantom", "Speed: 4 | Range: 1-3 | 14HP / 7ATK / 1DEF\n\nFlying swooper. Dives from distance, hits, then retreats out of melee reach. Hard to pin down; ranged weapons preferred.\n\nWeak to: Ranged\nResist: Slashing"));
        // Hostile - Nether
        enemies.add(mob("Blaze", "Speed: 2 | Range: 3\n\nHovering ranged attacker. Fireball deals area damage.\n\nWeak to: Water\nResist: Special, Ranged, Physical"));
        enemies.add(mob("Ghast", "Speed: 1 | Range: 5\n\nSlow but extreme range. Fireball explosions!\n\nWeak to: Ranged, Slashing\nResist: Special, Physical"));
        enemies.add(mob("Magma Cube", "Speed: 3 | Range: 1\n\nBouncy slime. Splits on death!\n\nWeak to: Water\nResist: Blunt"));
        enemies.add(mob("Wither Skeleton", "Speed: 3 | Range: 1\n\nRelentless pursuer with stone sword.\n\nWeak to: Blunt, Water\nResist: Special, Physical"));
        enemies.add(mob("Hoglin", "Speed: 3 | Range: 1\n\nCharging beast. Hits hard!\n\nWeak to: Special\nResist: Blunt, Physical"));
        enemies.add(mob("Piglin", "Speed: 2 | Range: 3\n\nCrossbow or gold sword. Trades if you have gold!\n\nWeak to: Special, Water\nResist: Ranged, Physical"));
        enemies.add(mob("Piglin Brute", "Speed: 2 | Range: 1 | 20HP / 10ATK / 3DEF\n\nElite piglin with a golden axe. Ignores gold bribes and hits like a truck. Dramatically tougher than a regular Piglin. Spawns in Crimson Forest.\n\nWeak to: Special, Water\nResist: Blunt, Physical"));
        enemies.add(mob("Zombified Piglin", "Speed: 2 | Range: 1 | 10HP / 4ATK / 1DEF\n\nNeutral until hit. Then every zombified piglin nearby aggros on you too. Safe to pass through; suicidal to swing at first.\n\nWeak to: Blunt, Water\nResist: Special, Physical"));
        // Hostile - End
        enemies.add(mob("Enderman", "Speed: 5 | Special: Teleport\n\nAggressive phase-shifting teleporter. Hunts in assault cycles of 2-3 rapid teleport-strikes before blinking away. Dodges sideways when hit instead of fleeing. Below 50% HP enters Frenzy: never retreats, +50% damage, relentless teleport-strikes every turn.\n\nWeak to: Water, Special\nResist: Ranged, Physical"));
        enemies.add(mob("Endermite", "Speed: 3 | Range: 1 | 8HP / 3ATK | Special: Blink\n\nTiny void pest with short-range teleports. Blinks 2-3 tiles to reach you and attacks the instant it's adjacent. Never idles. If blocked, blinks to a random nearby tile and rushes in next turn. Spawns in Warped Forest.\n\nWeak to: Water, Special\nResist: Physical"));
        enemies.add(mob("Shulker", "Speed: 1 | Range: 4\n\nStationary turret. Levitation projectiles!\n\nWeak to: Blunt\nResist: Ranged, Slashing, Physical"));
        enemies.add(mob("Witch", "Speed: 2 | Range: 4\n\nThrows harmful potions from long range.\n\nWeak to: Slashing, Cleaving\nResist: Special, Physical\nImmune: Water"));
        // Bosses
        enemies.add(mob("The Revenant", "BOSS | Zombie | 20HP / 4ATK / 2DEF / Speed 2\nPlains biome boss.\n\n" +
            "Abilities:\n- Raise the Dead: Summons 1-2 Zombies every 3 turns\n- Death Charge: 3-tile line from center, ATK+2\n- Gravefire Grid: Telegraphs a magma checker-grid for 1 turn\n- Shield Bash: Knockback 2 tiles\n\n" +
            "Phase 2 -Undying Rage: Regeneration, faster summons, fire trail on charge."));
        enemies.add(mob("Sandstorm Pharaoh", "BOSS | Husk | 25HP / 6ATK / 1DEF / Speed 2\nDesert biome boss.\n\n" +
            "Abilities:\n- Plant Mine: Invisible mine, 6 dmg on contact\n- Sand Burial: 2x2 quicksand stun\n- Sandstorm: 3x3 AoE + accuracy debuff\n- Curse of the Sands: Tiles you leave become quicksand\n\n" +
            "Phase 2 -Tomb Wrath: 2 mines/turn, 3x3 burial, summons 2 Husks."));
        enemies.add(mob("Frostbound Huntsman", "BOSS | Stray | 25HP / 5ATK / 2DEF / Range 4 / Speed 2\nSnowy Tundra boss.\n\n" +
            "Abilities:\n- Harpoon Pull: Telegraphed pull, 4 dmg, drags you 2 tiles toward the boss\n- Whiteout Ring: Ring burst around you with one safe gap, 4 dmg per tile\n- Blizzard: 3x3 AoE, 5 dmg + Slowness. P2: center stuns!\n- Ice Wall: 3 obstacle tiles blocking movement\n- Glacial Trap: 2x2 freeze zone, 2 dmg + stun\n- Frost Arrow: Range 4, ATK + Slowness\n\n" +
            "Phase 2 -Permafrost: Speed 3, frozen tiles spawn every 2 turns, all cooldowns reduced."));
        enemies.add(mob("The Rockbreaker", "BOSS | Vindicator | 30HP / 6ATK / 3DEF / Speed 2\nStony Peaks boss. Aggressive melee brawler. Every attack knocks you around the arena.\n\n" +
            "Abilities:\n- Ground Pound: Instant melee AoE + knockback 2 (no telegraph!)\n- Charge: Dashes toward you, 7 dmg + knockback 3\n- Seismic Slam: Cross pattern, 6 dmg + knockback 2\n- Boulder Toss: Range 4, 5 dmg + knockback + creates obstacle\n- Avalanche: Full-row attack, 4 dmg + pushes you downward\n\n" +
            "Phase 2 -Unstoppable: +1 Speed, all knockback distances increased, shorter cooldowns."));
        enemies.add(mob("The Hexweaver", "BOSS | Evoker | 28HP / 5ATK / 2DEF / Range 4 / Speed 2\nDark Forest boss.\n\n" +
            "Abilities:\n- Hex Snare: telegraphed curse + 2-tile pull\n- Runic Prison: cardinal runes erupt + brief cage\n- Vex Swarm: Summons 2 Vexes every 3 turns\n- Cursed Fog: 3x3 debuff zone\n- Hex Bolt: Ranged ATK + Slowness\n\n" +
            "Phase 2 -Arcane Fury: Teleports away, full cross fangs, 3 Vexes."));
        enemies.add(mob("The Hollow King", "BOSS | Zombie | 40HP / 7ATK / 3DEF / Speed 2\nCaverns boss.\n\n" +
            "Abilities:\n- Demolition Cache: telegraphed TNT that detonates next round\n- Rubble Toss: mines obstacle, throws it at marked tile\n- Cave-In: Boulders fall on tiles\n- Miner's Fury: Line charge destroys obstacles\n- Summon Silverfish from rubble\n- Lights Out: casts darkness (place Torches/Lanterns to negate)\n\n" +
            "Phase 2 -Total Collapse: permanent darkness, auto cave-ins, 3 TNT charges. COUNTERPLAY: Bring Torches, Lanterns, or Campfires to maintain light zones."));
        enemies.add(mob("The Broodmother", "BOSS | Spider | 35HP / 6ATK / 2DEF / Speed 3 | Size 3x3\nJungle boss.\n\n" +
            "Abilities:\n- Spawn Brood: 2-3 Cave Spiders from egg sacs\n- Web Spray: 3x3 stun + slow\n- Venomous Bite: ATK + Poison\n- Pounce: Leap 3 tiles, 2x2 AoE\n\n" +
            "Phase 2 -Nest Awakening: +2 Speed, respawning egg sacs."));
        enemies.add(mob("The Tidecaller", "BOSS | Drowned | 30HP / 5ATK / 2DEF / Range 3 / Speed 2\nRiver Delta boss.\n\n" +
            "Abilities:\n- Tidal Wave: 2-tile-wide flood column\n- Trident Storm: 3 tridents in spread\n- Riptide Charge: Water charge, knockback 2\n- Call of the Deep: Summon Drowned on water\n\n" +
            "Phase 2 -Deluge: Half arena floods permanently, +2 ATK on water."));
        enemies.add(mob("The Molten King", "BOSS | Magma Cube | 35HP / 8ATK / 2DEF / Speed 2 | Size 3x3\nNether Wastes boss.\n\n" +
            "SPLIT MECHANIC: At 50% HP, the boss splits into 2 smaller copies that keep ALL boss abilities. Those copies split again at 50% HP into a total of 4 tiny bosses.\n\n" +
            "Abilities:\n- Magma Eruption: Teleport-leap + 3x3 blast (8 dmg) + fire ring\n- Lava Cage: Ring player with fire, blocking escape\n- Absorb: Merges with nearby cube to heal\n- Melee: Knockback 1 tile on hit\n\n" +
            "Phase 2 -Meltdown: Arena shrinks via lava rings, faster cooldowns."));
        enemies.add(mob("The Bastion Brute", "BOSS | Skeleton (Piglin wargear) | 45HP / 8ATK / 3DEF / Speed 3\nCrimson Forest boss.\n\n" +
            "Abilities:\n- Gore Charge: 4-tile charge, ATK+3, knockback 3\n- Fungal Growth: 3x3 heal zone\n- Rampage: AoE all adjacent tiles\n- Summon Pack: 2 Piglins (once)\n\n" +
            "Phase 2 -Blood Frenzy: +4 ATK, fire trail, 2-tile rampage, speed 4."));
        enemies.add(mob("Wailing Revenant", "BOSS | Ghast | 60HP / 8ATK / 2DEF / Stationary\nSoul Sand Valley boss.\n\n" +
            "Hovers outside the arena edge. Attack the front row to hit it.\nNo regular ghasts spawn; only Wither Skeletons.\n\n" +
            "Abilities:\n- Fireball Barrage: 3 fireballs fly across the arena\n- Raining Fireballs: Half the arena warned, 5 dmg each\n- Magma Rows: Random rows turn to magma for 2 turns\n- Summon Wither Skeletons: 2 skeletons (max 4)\n\n" +
            "Phase 2 -Requiem: 5 fireballs, 2 magma rows, 3 skeletons."));
        enemies.add(mob("Ashen Warlord", "BOSS | Wither Skeleton | 55HP / 10ATK / 4DEF / Speed 3\nBasalt Deltas boss.\n\n" +
            "Abilities:\n- Wither Slash: ATK + permanent max HP reduction\n- Summon Blaze Guard: 2 Blazes every 4 turns\n\n" +
            "Phase 2 -Warlord's Command: Arc wither slash, summons Wither Skeletons instead, speed 4."));
        enemies.add(mob("The Void Walker", "BOSS | Enderman | 50HP / 9ATK / 2DEF / Speed 3\nWarped Forest boss.\n\n" +
            "Abilities:\n- Void Rift: Portal pair (step on one, teleport to other)\n- Mirror Image: 2 decoy clones\n- Phase Strike: Teleport behind player + attack\n- Void Pull: Pulls player 2 tiles toward boss\n\n" +
            "Phase 2 -Reality Shatter: Permanent rifts, 3 clones, pull range 3."));
        enemies.add(mob("Shulker Architect", "BOSS | Shulker | 50HP / 9ATK / 4DEF / Range 5 / Speed 1\nEnd City boss.\n\n" +
            "Abilities:\n- Bullet Storm: 4 homing bullets + Levitation\n- Deploy Turret: Stationary shulker turret (max 3)\n- Fortify Shell: 80% damage reduction 1 turn\n- Teleport Link: Teleport to turret position\n\n" +
            "Phase 2 -Defense Protocol: 6 bullets, reflect shell, turret limit 5."));
        enemies.add(mob("The Chorus Mind", "BOSS | Enderman | 60HP / 12ATK / 3DEF / Speed 2\nChorus Grove boss.\n\n" +
            "Abilities:\n- Chorus Bloom: Grow obstacle plants, teleport to any\n- Entangle: Root area, immobilize + damage\n- Chorus Bomb: AoE + random teleport on hit\n- Resonance Cascade: All plants pulse AoE damage\n\n" +
            "Phase 2 -Overgrowth: Auto-spread plants, auto cascade, boss teleports each turn."));
        enemies.add(mob("The Void Herald", "BOSS | Enderman | 55HP / 10ATK / 3DEF / Speed 3\nOuter End Islands boss.\n\n" +
            "Abilities:\n- Void Gale: Push all entities toward void edge\n- Lightning Strike: Mark tile, + pattern 6 dmg next turn\n- Platform Collapse: Permanently remove 2x2 floor\n- Blink Assault: Teleport + hit 3 tiles\n\n" +
            "Phase 2 -Oblivion: Auto collapse, gale 3 tiles, 2 lightning marks, speed 4."));
        enemies.add(mob("The Wither", "BOSS | Wither | 110HP / 10ATK / 5DEF / Range 5 / Speed 2 | Size 2x2\nBasalt Deltas final boss.\n\n" +
            "Abilities:\n- Wither Skull Barrage: 3 (P2: 5) 6HP, 7-dmg skull projectiles, killable\n- Decay Aura: Radius 3 (P2: 4) damage + applies Wither, ramps each turn\n- Melee strikes apply Wither II for 4 turns\n- Summon Wither Skeletons: 2 every 4 turns, max 4\n- Charge: 4-tile dash, ATK+3 (P2 leaves fire trail)\n\n" +
            "Phase 2 (under 50% HP) - Wither Armor: Immune to ranged, 12-dmg transition blast, 5 skulls, larger decay aura.\n\n" +
            "Wither effect ramps with duration. Cleanse fast or fight in short bursts.\n\nWeak to: Water\nResist: Slashing, Blunt, Ranged, Physical"));
        enemies.add(mob("Warden", "BOSS | Warden | 60HP / 8ATK / 4DEF / Speed 3\nDeep Dark boss.\n\nAbilities:\n- Sonic Boom: straight-line ranged attack that ignores defense\n- Tremor Sense: always knows your position. Stealth and distance don't help\n\nWeak to: Ranged\nResist: Blunt, Slashing, Physical"));
        enemies.add(mob("Ender Dragon", "BOSS | Ender Dragon | 100HP / 15ATK / 5DEF / Speed 4\nDragon's Nest final boss. Massive, fast, devastating. Clearing it triggers New Game Plus.\n\nWeak to: Ranged, Special\nResist: Water, Physical"));
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
                "Defense - +1 Armor Class/point (raises dodge chance vs enemies).\n\n" +
                "Luck - +2%/pt to ALL combat procs, crits & loot.\n\n" +
                "Resourceful - +1 emerald/level & trader discounts."),
            new Page("Stat Builds",
                "Glass Cannon: Max Power + AP for burst.\n\n" +
                "Tank: Vitality + Defense to survive.\n\n" +
                "Speedster: Speed + AP to outmaneuver.\n\n" +
                "Merchant: Resourceful + Luck for loot.")
        )));
        progression.add(new Entry("§cSlashing Loadouts", "minecraft:diamond_sword", List.of(
            new Page("Bleed Striker",
                "§cBleed Striker. Stack bleed, finish quick.\n\n" +
                "Weapon: Diamond or Netherite Sword + Sharpness.\n" +
                "Each Sharpness hit stacks Bleed (1, 3, 6, 10 dmg/turn).\n\n" +
                "Armor: Chainmail set (Rogue). +2 Slashing, -1 AP cost.\n" +
                "Trim: Bolt = +1 Slashing per piece.\n\n" +
                "Tip: Sweeping Edge spreads hits to adjacent foes. Every\n" +
                "extra hit is another bleed stack."),
            new Page("First-Strike Assassin",
                "§cFirst-Strike Assassin. Open with a guaranteed crit.\n\n" +
                "Weapon: Diamond Sword (30% native crit) or Netherite\n" +
                "Sword (execute under 30% HP).\n\n" +
                "Hybrid: Chain+Netherite (Ambush): first attack always crits.\n" +
                "Hybrid: Leather+Chain (Skirmisher): +3 dmg if you moved.\n\n" +
                "Mob head: Piglin Head = +1 Slashing.\n" +
                "Open from max move range, eat the free crit, then\n" +
                "Skirmisher keeps stacking as long as you reposition."),
            new Page("Sweeper",
                "§cSweeper. Clear adds with every swing.\n\n" +
                "Weapon: any Sword + Sweeping Edge III (90% AoE).\n\n" +
                "Armor: Iron set (Guard) for survivability while\n" +
                "standing in the middle of a pile of enemies, or\n" +
                "Hybrid Chain+Iron (Sentinel) for ripostes on dodged hits.\n\n" +
                "Great vs swarms like Slimes, bees, and Wither Skeletons.")
        )));
        progression.add(new Entry("§6Cleaving Loadouts", "minecraft:diamond_axe", List.of(
            new Page("Armor Crusher",
                "§6Armor Crusher. Punish heavy mobs.\n\n" +
                "Weapon: Iron, Diamond, or Netherite Axe.\n" +
                "5% chance per hit to ignore enemy armor entirely.\n\n" +
                "Armor: Iron set (Guard). +2 Cleaving, +2 AC, KB immune.\n" +
                "Trim: Snout = +1 Cleaving per piece.\n\n" +
                "Best targets: bosses, golems, anything DEF 3 or higher."),
            new Page("Wall Slammer",
                "§6Wall Slammer. Push enemies into walls for bonus dmg.\n\n" +
                "Weapon: Axe + Knockback II.\n\n" +
                "Hybrid: Iron+Diamond (Warlord): every melee hit KB 1.\n" +
                "Hybrid: Iron+Netherite (Immovable): reflect 2 dmg.\n\n" +
                "Pair with cactus or wall placements to slam enemies\n" +
                "into terrain hazards.")
        )));
        progression.add(new Entry("§8Blunt Loadouts", "minecraft:mace", List.of(
            new Page("Mace AoE King",
                "§8Mace AoE King. Half-damage to a whole 3x3.\n\n" +
                "Weapon: Mace + Density (more AoE damage) + Wind Burst\n" +
                "(shockwave knockback) + Breach (armor pen).\n\n" +
                "Armor: Diamond set (Knight). +2 Blunt, +1 ATK.\n" +
                "Trim: Dune = +1 Blunt per piece.\n\n" +
                "Mob head: Creeper Head = +1 Blunt."),
            new Page("Stun Lock",
                "§8Stun Lock. Keep targets from acting.\n\n" +
                "Weapon: Stick or Bamboo (5% stun per hit), upgrade to\n" +
                "Blaze Rod (fire + stun) or Breeze Rod (wind burst).\n\n" +
                "Armor: any survival set. You want to outlast their stuns.\n\n" +
                "Items: Bell (2-tile AoE stun), Cobweb (single-target stun),\n" +
                "Powder Snow Bucket (freeze)."),
            new Page("Stonewall Brawler",
                "§8Stonewall Brawler. Cap incoming dmg, hit big.\n\n" +
                "Hybrid: Diamond+Netherite (Stonewall): cap incoming at 6.\n" +
                "Weapon: Mace for AoE, or Blaze Rod for fire ticks.\n\n" +
                "Vitality + Defense stats. Pair with Resistance potions.\n" +
                "Walk into a swarm, eat the damage, mace them all.")
        )));
        progression.add(new Entry("§3Water Loadouts", "minecraft:trident", List.of(
            new Page("Riptide Bruiser",
                "§3Riptide Bruiser. Dash through enemies in a line.\n\n" +
                "Weapon: Trident + Riptide II/III. Dash hits + KB everything\n" +
                "in the line (1 + level tiles of KB).\n\n" +
                "Armor: Turtle Helmet (Aquatic) + any survival set.\n" +
                "+3 Water, walk on water, +1 HP regen.\n" +
                "Trim: Coast = +1 Water per piece.\n\n" +
                "Best on tight arenas. Turn a row of mobs into pinballs."),
            new Page("Lightning Caller",
                "§3Lightning Caller. Channeling + Soaked combo.\n\n" +
                "Weapon: Trident + Channeling III + Loyalty.\n" +
                "Channeling does 4+2/lvl on throw, DOUBLES vs Soaked,\n" +
                "chains to (level-1) extra targets.\n\n" +
                "Setup: Soak first with corals, pufferfish, nautilus shell,\n" +
                "or just throw the trident into a water tile.\n\n" +
                "Armor: Turtle Helmet + Coast-trimmed survival set.\n" +
                "Tip: Soaked also gives -1 enemy Speed, so they can't kite."),
            new Page("Coral Statuses",
                "§3Coral Statuses. One debuff per coral, swap to taste.\n\n" +
                "Each live coral applies a different effect on hit:\n" +
                "Tube = Soaked, Brain = Confuse (40%), Bubble = KB,\n" +
                "Fire = Searing (+3 dmg to burning), Horn = Pierce 3 armor.\n\n" +
                "Keep 2-3 corals on the hotbar and swap to match the fight.\n" +
                "Armor: Turtle Helmet + Coast trim, or full Coast for max Water.\n\n" +
                "Best vs single tough enemies. Stack debuffs, then finish."),
            new Page("Water-Burst Throwables",
                "§3Water-Burst Throwables. AoE crowd control.\n\n" +
                "All four AoE throwables count as Water:\n" +
                "Turtle Egg (T1, 2 dmg, Soak I), Pufferfish (T2, 3 + Soak II\n" +
                "+ Poison I), Nautilus Shell (T3, 4 + Soak III + Confuse I),\n" +
                "Heart of the Sea (T4, 5 + Soak IV + Confuse II).\n\n" +
                "Armor: Turtle Helmet + Coast trim.\n" +
                "Combo: open with a Heart of the Sea throw, then\n" +
                "Channeling Trident for 2x lightning across the soaked mob.")
        )));
        progression.add(new Entry("§dSpecial Loadouts", "minecraft:golden_hoe", List.of(
            new Page("Hoe Specialist",
                "§dHoe Specialist. Low base damage, huge with affinity.\n\n" +
                "Weapon: Diamond or Netherite Hoe.\n" +
                "Hoes have 1-3 base damage but every Special point adds\n" +
                "+3 damage. Stacks with affinity perks.\n\n" +
                "Armor: Gold set (Gambler). +2 Special, +3 Luck crit chance.\n" +
                "Trim: Rib = +1 Special per piece.\n" +
                "Mob head: Wither Skeleton Skull = +1 Special.\n\n" +
                "Affinity perk: 3% chance per point to make ANY attack 0 AP."),
            new Page("Sherd Caster",
                "§dSherd Caster. Every sherd is a spell.\n\n" +
                "Stash 4-6 pottery sherds with complementary effects:\n" +
                "Heart (heal+regen), Skull (execute), Prize (3x next hit),\n" +
                "Arms Up (war cry), Blade (phantom slash).\n\n" +
                "Armor: Gold (Gambler) for crit synergy + emerald gain,\n" +
                "or Hybrid Gold+Diamond (Gladiator) for +50% crit damage.\n\n" +
                "Resourceful stat keeps your sherd budget topped up."),
            new Page("Wind Mage",
                "§dWind Mage. Knock everything around.\n\n" +
                "Weapon: Wind Charges (knock enemies back OR self-launch\n" +
                "off an adjacent tile; momentum bonus = +50% on next hit).\n\n" +
                "Combine with: any Special weapon for follow-up burst,\n" +
                "wall placements to trap targets after KB, or Pottery Sherd\n" +
                "Snort (Tectonic Charge) for chained shoves.\n\n" +
                "Armor: Gold + Special trim for damage scaling."),
            new Page("Crit Gambler",
                "§dCrit Gambler. Stack crit chance, swing big.\n\n" +
                "Hybrid: Gold+Diamond (Gladiator): crits +50% extra dmg.\n" +
                "Hybrid: Gold+Netherite (Berserker): crit rises as HP drops.\n\n" +
                "Weapon: Diamond Sword (30% native crit) or Diamond Hoe\n" +
                "(for the Special-affinity ramp).\n\n" +
                "Luck stat + Spire trim = +1 Luck per piece, boosting\n" +
                "every Craftics proc including crits.")
        )));
        progression.add(new Entry("§aPet Loadouts", "minecraft:bone", List.of(
            new Page("Beastmaster",
                "§aBeastmaster. Let allies do the killing.\n\n" +
                "Weapon: Diamond or Netherite Shovel. Pet-type, scales with\n" +
                "your Pet affinity.\n\n" +
                "Trim: Raiser = +1 ally damage per piece.\n" +
                "Affinity perk: +3 ally HP per point.\n\n" +
                "Add mobs to your battle party at the hub (Shift+RClick to\n" +
                "toggle), or use Spawn Eggs for one-battle summons. A Wolf,\n" +
                "Iron Golem, and Llama covers melee, tank, and ranged."),
            new Page("Lead Commander",
                "§aLead Commander. Micromanage ally turns.\n\n" +
                "Item: a Lead in main hand. Click an ally to select,\n" +
                "then click an adjacent enemy (attack) or any tile (move).\n" +
                "Costs 2 AP. Does NOT consume the ally's own turn.\n\n" +
                "Armor: any survival set. You stay alive while pets fight.\n" +
                "Raiser trim still buffs every ally hit, including the\n" +
                "free lead-commanded ones."),
            new Page("Mount Build",
                "§aMount Build. Ride a party mob for bonus Speed.\n\n" +
                "Setup: equip a Saddle on a rideable mob (Horse, Donkey,\n" +
                "Mule, Camel, Skeleton/Zombie Horse) in the hub, then add\n" +
                "it to your battle party. You auto-mount at combat start.\n\n" +
                "Mounting grants +3 Speed and the mount's HP pool.\n\n" +
                "Pair with: any weapon. Mounted speed makes Riptide,\n" +
                "Skirmisher (+3 if you moved), and hit-and-run Mace plays\n" +
                "all even better. Horse Armor (equipped pre-tame) adds DEF.")
        )));
        progression.add(new Entry("§bRanged Loadouts", "minecraft:bow", List.of(
            new Page("Bow Sniper",
                "§bBow Sniper. Long-range single-target.\n\n" +
                "Weapon: Bow + Power IV/V (+1 dmg/lvl + range).\n" +
                "Add Flame for burn on hit, or Infinity to skip arrow drain.\n" +
                "Tipped arrows still consume even with Infinity.\n\n" +
                "Armor: any survival set, ideally with Sentry trim\n" +
                "(+1 Ranged per piece).\n" +
                "Mob head: Skeleton Skull = +1 Ranged."),
            new Page("Crossbow Pierce",
                "§bCrossbow Pierce. Line shots through groups.\n\n" +
                "Weapon: Crossbow + Piercing III (hits 4 targets in a line)\n" +
                "+ Quick Charge for AP reduction.\n\n" +
                "Bolts deal 50% to targets behind the first and inflict Bleed.\n" +
                "Best vs single-file enemy lines or stacked mobs.\n\n" +
                "Trim: Sentry per piece. Mob head: Skeleton Skull."),
            new Page("Multishot Spread",
                "§bMultishot Spread. Diagonal fan attack.\n\n" +
                "Weapon: Crossbow + Multishot (fires 2 extra bolts at\n" +
                "45 degree diagonals).\n\n" +
                "Best vs clusters where 3 spread lines all land.\n" +
                "Tipped arrows apply on every bolt. Load Slowness or\n" +
                "Weakness arrows for crowd lockdown.\n\n" +
                "Armor: Hybrid Chain+Netherite (Ambush) for guaranteed\n" +
                "crit on the opening volley."),
            new Page("Rocket Crossbow",
                "§bRocket Crossbow. AoE long-range burst.\n\n" +
                "Weapon: Crossbow with a Firework Rocket in OFF-HAND.\n" +
                "Rockets bypass the usual arrow check, so you don't need\n" +
                "an arrow stack, just the rocket. Massive damage AoE\n" +
                "at full crossbow range.\n\n" +
                "Pair with Quick Charge so each rocket fires fast.\n" +
                "Resourceful + Curiosity Dealer trades keep rockets stocked."),
            new Page("Ricochet Chain",
                "§bRicochet Chain. Bounce shots between mobs.\n\n" +
                "Affinity perk: 5% chain-ricochet chance per Ranged point.\n" +
                "Stack Ranged affinity to 10+ and you'll see chains every\n" +
                "shot.\n\n" +
                "Weapon: any Bow or Crossbow. Punch I/II also pushes\n" +
                "targets behind the impact and adds collision damage.\n\n" +
                "Best on packed arenas where ricochets re-target."),
            new Page("Copper Marksman §7(addon)",
                "§bCopper Marksman §7(needs Copper Age Backport mod).\n\n" +
                "Full Copper armor set (Marksman): +4 Ranged Power and a\n" +
                "ricochet that stacks on top of the affinity perk.\n\n" +
                "Six Copper hybrids:\n" +
                "Copper+Leather (Run and Gun): moved this turn = +1 range.\n" +
                "Copper+Chain (Deadeye): +3 vs enemies that haven't acted.\n" +
                "Copper+Iron (Aegis): 30% dodge vs incoming ranged.\n" +
                "Copper+Gold (Contagion): spread debuffs to adjacent.\n" +
                "Copper+Diamond (Siege): splash half dmg to adjacent.\n" +
                "Copper+Netherite (Stormbringer): arc to extra enemy.")
        )));
        progression.add(new Entry("§7Physical Loadouts", "minecraft:leather_chestplate", List.of(
            new Page("Brawler Streaker",
                "§7Brawler Streaker. Keep killing, keep buffing.\n\n" +
                "Weapon: empty main hand (fists are Physical).\n\n" +
                "Armor: Leather set (Brawler). +2 Physical AND a kill streak\n" +
                "(+30% dmg per kill, max 3 stacks).\n\n" +
                "Mob head: Zombie Head = +1 Physical.\n" +
                "Affinity perk: 3% counter chance per point.\n" +
                "Best vs swarms. Chained kills keep your buff alive."),
            new Page("Counterpuncher",
                "§7Counterpuncher. Let them hit you, hit harder back.\n\n" +
                "Hybrid: Leather+Iron (Counterpuncher): 50% chance to\n" +
                "instantly attack back when hit.\n\n" +
                "Vitality + Defense stats. The more attacks you eat the\n" +
                "more free retaliations you get.\n\n" +
                "Pair with: Shield in offhand (+1 DEF, 25% block) and\n" +
                "Thorns armor for reflection on top."),
            new Page("Lucky Streak",
                "§7Lucky Streak. Crit ramp until you die.\n\n" +
                "Hybrid: Leather+Gold (Lucky Streak): +10% crit per kill,\n" +
                "resets when hit.\n\n" +
                "Weapon: Diamond Sword (native 30% crit) or any sword\n" +
                "with Sharpness for bleed chains between kills.\n\n" +
                "Stay mobile (Speed stat) so you never take a hit and\n" +
                "the streak never resets."),
            new Page("Breaker / Rampage",
                "§7Breaker or Rampage. Ignore resistance OR refund AP.\n\n" +
                "Hybrid: Leather+Diamond (Breaker): your attacks ignore\n" +
                "all damage-type resistances. Great vs resist-heavy bosses.\n\n" +
                "Hybrid: Leather+Netherite (Rampage): every kill refunds\n" +
                "1 AP. Multi-kill turns become possible.\n\n" +
                "Both pair well with Strength or Glass Cannon stats. You\n" +
                "want to be the one swinging.")
        )));
        progression.add(new Entry("Addon Mod Loadouts §7(if installed)", "minecraft:copper_ingot", List.of(
            new Page("Copper Age Backport",
                "§6Copper Age Backport §7(addon). Adds copper weapons +\n" +
                "armor on versions before vanilla added them.\n\n" +
                "Copper Sword, Axe, Pickaxe, Hoe, and Shovel slot in\n" +
                "between stone and iron. Damage type matches the\n" +
                "equivalent tool.\n\n" +
                "Full Copper armor set: §6Marksman§7. +4 Ranged Power and\n" +
                "ranged ricochet. Six Copper hybrid sets stack on top of\n" +
                "the standard 15. See Ranged Loadouts for the full table."),
            new Page("Artifacts: Head & Necklace",
                "§5Artifacts §7(addon). Wearable trinkets via the\n" +
                "Accessories slot system.\n\n" +
                "Head: Night Vision Goggles +1 Range, Snorkel +1 Water +\n" +
                "Soaked immune, Cowboy Hat pulls hits 1 tile closer,\n" +
                "Villager Hat +50% emeralds.\n\n" +
                "Necklace: Flame Pendant burns adjacent 2 dmg/turn, Thorn\n" +
                "Pendant reflects 25%, Cross Necklace halves the next hit,\n" +
                "Shock Pendant 30% chain 3 dmg on hit."),
            new Page("Artifacts: Hands & Belt",
                "§5Artifacts §7(addon).\n\n" +
                "Hands: Power Glove +1 Melee, Golden Hook pulls hits closer,\n" +
                "Lucky Scarf +1 Luck.\n\n" +
                "Belt: Antidote Vessel cleanses poison, Cloud in a Bottle\n" +
                "+1 Speed + jump, Obsidian Skull immune to fire damage,\n" +
                "Pickaxe Heater pickaxes ignore obstacle armor."),
            new Page("Artifacts: Feet & Curio",
                "§5Artifacts §7(addon).\n\n" +
                "Feet: Running Shoes +1 Speed, Bunny Hoppers double KB\n" +
                "resist, Steadfast Spikes pierce armor on melee, Kitty\n" +
                "Slippers cancel first fall.\n\n" +
                "Curio: Lucky Star double crit dmg, Eternal Steak\n" +
                "non-consuming food, Pocket Piston pushes 2 tiles,\n" +
                "Mimic kicks in custom mimic boss encounters."),
            new Page("Creeper Overhaul",
                "§2Creeper Overhaul §7(addon). Adds biome-themed creepers.\n\n" +
                "Each variant inherits combat behavior from base Creeper\n" +
                "AI but with biome-flavored explosions (Cave Creeper\n" +
                "blinds, Snowy Creeper slows, etc.). Treat them like\n" +
                "regular creepers in your loadout choices but plan for\n" +
                "the post-blast status effect."),
            new Page("Variants & Ventures + Spring to Life",
                "§3Variants & Ventures §7(addon). Adds zombie, skeleton,\n" +
                "and spider sub-variants with stat tweaks. Stats roll\n" +
                "within the species' Craftics range, so existing loadouts\n" +
                "handle them. Expect a bit more spread.\n\n" +
                "§aSpring to Life §7(addon). Variant cows, pigs, chickens.\n" +
                "All taming + ally rules apply unchanged.")
        )));
        CATEGORIES.add(new Category("Leveling & Stats", "minecraft:experience_bottle",
            "Grow stronger through combat.", progression));

        // === EQUIPMENT & ENCHANTMENTS ===
        List<Entry> equipment = new ArrayList<>();
        equipment.add(new Entry("Armor Sets", "minecraft:diamond_chestplate", List.of(
            new Page("Set Bonuses",
                "Wear a full matching armor set for combat bonuses.\n\n" +
                "Leather (Brawler): +2 Physical, kill-streak damage ramp\n" +
                "Chainmail (Rogue): +1 Speed, attacks cost 1 less AP (min 1), +2 Slashing\n" +
                "Iron (Guard): +2 AC, immune to knockback, +2 Cleaving\n" +
                "Gold (Gambler): +15% crit chance, +1 emerald per kill, +2 Special"),
            new Page("Advanced Sets",
                "Diamond (Knight): +3 AC, +1 ATK, +2 Blunt\n" +
                "Netherite (Juggernaut): +4 AC, +2 ATK, fire immune, +2 to ALL damage types\n" +
                "Turtle (Aquatic): Water tiles walkable, +1 HP regen per turn, +1 Range on water, +2 Water\n\n" +
                "Mixed armor gets no set bonus. Hybrid two-material combos unlock their own set bonuses, see the Hybrid Armors category.")
        )));
        equipment.add(new Entry("Shield", "minecraft:shield", List.of(
            new Page("Shield Mechanics",
                "Equip a shield in your OFFHAND slot.\n\n" +
                "Passive: +1 Armor Class at all times.\n\n" +
                "Block: 25% chance to completely block an incoming attack, negating all damage.\n\n" +
                "No AP cost - purely passive! Equip and fight normally.")
        )));
        equipment.add(new Entry("Enchantments", "minecraft:enchanted_book", List.of(
            new Page("Sword Enchantments",
                "Sharpness: +1 ATK/lvl + applies Bleed stacks. Each stack = +1 bonus damage when enemy is attacked.\n\n" +
                "Smite: AoE radiant burst vs undead. All undead in radius take bonus damage.\n\n" +
                "Bane of Arthropods: Injects venom into arthropods. Poison + Slowness. Spreads on death.\n\n" +
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
                "Density: Gravity well. Pulls nearby enemies to impact point. Crushing bonus damage.\n\n" +
                "Breach: Permanently reduces target defense per hit. Stacks all combat.\n\n" +
                "Wind Burst: Shockwave knockback on all adjacent + buffs next Mace hit damage."),
            new Page("Trident Enchantments",
                "Impaling: +damage per level + inflicts Bleed stacks.\n\n" +
                "Channeling: Chain lightning on throw hit. Prioritizes Soaked enemies (2x damage). Lv1=1, Lv2=3, Lv3=5 chains.\n\n" +
                "Loyalty: Trident ricochets to nearby enemies before returning. +1 ricochet per level (50% damage).\n\n" +
                "Riptide: Dash through enemies instead of throwing."),
            new Page("Armor Enchantments",
                "Protection: +1 Armor Class per 2 levels worn.\n\n" +
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
        pets.add(new Entry("Battle Party", "minecraft:bone", List.of(
            new Page("Adding Mobs",
                "Your battle party is built at the hub.\n\n" +
                "Shift+Right-Click any rideable or tameable mob in the\n" +
                "hub to toggle it in your party. Up to Pet Affinity + 1\n" +
                "mobs at a time (more Pet points = bigger party).\n\n" +
                "At combat start, every party mob spawns into the arena\n" +
                "as your ally. After the fight, survivors return to the\n" +
                "hub automatically."),
            new Page("Mid-Combat Taming",
                "You can also tame a wild mob during a fight.\n\n" +
                "Use the mob's breeding item on it (Bone for wolves,\n" +
                "Fish for cats, Wheat for cows/sheep/goats, Carrot/Apple\n" +
                "for horses, etc.).\n\n" +
                "Combat-capable mobs become an ally for the rest of\n" +
                "that battle. Passive mobs (cow, sheep, pig, chicken) are\n" +
                "sent home to your hub instead of fighting."),
            new Page("Ally AI",
                "Tamed allies act on the enemy phase:\n" +
                "- Pick a target based on archetype (melee charges nearest,\n" +
                "  ranged kites, support stays near you, flyers chase weak).\n" +
                "- Retreat below 25% HP unless their archetype says fight.\n" +
                "- Path around walls and tile hazards.\n\n" +
                "Heal allies with Hay Block (+4 HP) or Pet affinity (+3 HP\n" +
                "per point at spawn). Lead command lets you spend 2 AP to\n" +
                "give an ally a bonus attack/move without using their turn.")
        )));
        pets.add(new Entry("Mounts", "minecraft:saddle", List.of(
            new Page("How Mounting Works",
                "Mount setup happens at the hub, not in combat.\n\n" +
                "Step 1: tame a rideable mob (Horse, Donkey, Mule, Camel,\n" +
                "Skeleton/Zombie Horse) using its breeding item.\n" +
                "Step 2: equip a Saddle on it (right-click while holding\n" +
                "the saddle).\n" +
                "Step 3: add the saddled mob to your battle party with\n" +
                "Shift+Right-Click.\n\n" +
                "When combat starts, you auto-mount that party mob.\n" +
                "Mounting grants +3 Speed and you ride along with the\n" +
                "mount's HP pool as a bonus shield."),
            new Page("Riding in Combat",
                "While mounted:\n" +
                "- Your move uses the mount's tile path (same speed bonus).\n" +
                "- Weapon attacks fire normally from the saddle.\n" +
                "- The mount can't take its own ally turn while you ride\n" +
                "  it (you're the driver).\n" +
                "- If the mount dies, you dismount and lose the bonus.\n\n" +
                "Pair with Riptide Trident or Skirmisher hybrid for free\n" +
                "+3 dmg from the constant movement.")
        )));
        pets.add(new Entry("Pet Armor", "minecraft:diamond_horse_armor", List.of(
            new Page("Pet Equipment",
                "Equip armor on a pet BEFORE taming it (the bonus is\n" +
                "snapshotted at the moment it joins your party).\n\n" +
                "Horse Armor bonuses:\n" +
                "Leather: +1 DEF\n" +
                "Iron: +2 DEF\n" +
                "Gold: +1 DEF, +1 ATK\n" +
                "Diamond: +3 DEF, +1 ATK\n\n" +
                "Wolf Armor: +2 DEF, +1 ATK\n\n" +
                "Swapping armor after taming does not change the bonus,\n" +
                "so plan the loadout before recruiting.")
        )));
        CATEGORIES.add(new Category("Pets & Allies", "minecraft:bone",
            "Build a battle party of tamed mobs.", pets));

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
                "Shaper: +1 Armor Class /piece\n  Full Set: Terraformer (free barrier/turn)\n\n" +
                "Raiser: +1 ally damage /piece\n  Full Set: Rally (allies +2 Spd, +1 Atk)\n\n" +
                "Host: +2 max HP /piece\n  Full Set: Symbiote (heal 1 HP/kill)\n\n" +
                "Tide: +1 HP regen per 2 turns /piece\n  Full Set: Ocean's Blessing (emergency heal)"),
            new Page("Nether Trims",
                "Ward: +1 Armor Class /piece\n  Full Set: Fortress (50% less dmg when stationary)\n\n" +
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
                "Iron: +1 Armor Class /piece\n" +
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
                "Stay alert. Always keep healing items ready!")
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
                "A vault with NO enemies. Just free loot!\n" +
                "OPTIONAL: Skip or enter to claim 3-5 random items.\n\n" +
                "The vault floor is pure gold. " +
                "Items include trial chamber loot pool rewards.")
        )));
        events.add(new Entry("Ominous Trial", "minecraft:ominous_trial_key", List.of(
            new Page("Ultimate Challenge",
                "~5% chance, LATE GAME ONLY (biome 10+).\n\n" +
                "An ominous trial with a WARDEN boss, Breeze, and " +
                "elite trial mobs on deepslate.\n\n" +
                "OPTIONAL but EXTREMELY dangerous.\n" +
                "Rewards: 2-3 legendary items. Netherite gear, " +
                "ominous trial keys, heavy cores!")
        )));
        CATEGORIES.add(new Category("Random Events", "minecraft:trial_key",
            "Events that appear between levels.", events));
    }
}
