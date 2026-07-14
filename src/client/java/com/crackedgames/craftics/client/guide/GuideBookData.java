package com.crackedgames.craftics.client.guide;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;

/**
 * All guide book content. Categories contain entries, entries contain pages.
 * Bestiary entries carry structured MobStats so the screen can render stat
 * badges instead of plain text.
 *
 * Unlock state is server-authoritative: synced via GuideBookSyncPayload on join
 * and whenever the server unlocks a new entry.
 *
 * IMPORTANT naming contract:
 * - Regular mob entries must be named exactly as CombatManager.entityTypeIdToMobName
 *   produces them ("minecraft:zombified_piglin" -> "Zombified Piglin").
 * - Boss entries must be named exactly as CombatManager.getBossName returns them
 *   ("The Revenant", "The Wither", ...). Both are used as unlock keys.
 * - "How Trims Work" is referenced by name from CombatManager trim-drop unlocks.
 */
public class GuideBookData {
    // Content version: 2026-06-12 mass overhaul

    public record Page(String title, String text) {}
    /** Structured bestiary stats. Any field may be null/empty -> badge hidden. */
    public record MobStats(String role, String hp, String atk, String def, String spd,
                           String rng, String size, String weak, String resist, String immune) {}
    public record Entry(String name, String iconItem, List<Page> pages, MobStats stats) {
        public Entry(String name, String iconItem, List<Page> pages) { this(name, iconItem, pages, null); }
    }
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
     * The server is authoritative - this just prevents a visual delay.
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

    /** Shorthand for creating a bestiary mob entry with structured stats. */
    private static Entry mob(String name, String icon, MobStats stats, String description) {
        return new Entry(name, icon, List.of(new Page(name, description)), stats);
    }

    /** Bestiary mob entry with multiple pages (bosses with long ability lists). */
    private static Entry mob(String name, String icon, MobStats stats, Page... pages) {
        return new Entry(name, icon, List.of(pages), stats);
    }

    /** Stat helper. Pass null for fields that don't apply. */
    private static MobStats st(String role, String hp, String atk, String def, String spd,
                               String rng, String size, String weak, String resist, String immune) {
        return new MobStats(role, hp, atk, def, spd, rng, size, weak, resist, immune);
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
                "Speed determines how many tiles you can move.\n\n" +
                "You can move AND attack in the same turn, in any order, until both pools are spent."),
            new Page("Your Turn",
                "On your turn:\n" +
                "- Left-click a tile to move there\n" +
                "- Left-click an enemy to attack\n" +
                "- Right-click to use items\n" +
                "- Press R to end your turn\n\n" +
                "Moving costs Speed points. Attack AP cost depends on the weapon (see Weapons & AP).\n\n" +
                "The act-order strip at the top shows who acts next. A gold highlight follows whoever is acting."),
            new Page("Enemy Turns",
                "After you end your turn, each enemy acts. They may:\n" +
                "- Move toward you\n" +
                "- Attack if in range\n" +
                "- Use special abilities\n" +
                "- Flee if wounded\n\n" +
                "Every mob type has its own attack animation style: spiders pounce, golems slam, wolves dash, endermen blink, witches channel. Learn the tells - telegraphed abilities show warning tiles one turn ahead.\n\n" +
                "Watch enemy behavior patterns to plan your strategy!"),
            new Page("Useful Keys",
                "R - End turn\n" +
                "G - Open this guide book\n" +
                "Y - Toggle enemy threat overlay (shows reach)\n" +
                "U - Toggle combat UI\n" +
                "H - Respec stat points\n" +
                "J - Respec affinity points\n" +
                "M - Mount ability (e.g. Netherite Golem)\n" +
                "Left/Right arrows - Rotate move slots\n\n" +
                "Craftics combat keys win over conflicting mod binds; rebind in vanilla Controls if needed.")
        )));
        basics.add(new Entry("Enemy Scaling", "minecraft:redstone", List.of(
            new Page("Stats Grow With You",
                "Bestiary pages show BASE stats. Real enemies scale up:\n\n" +
                "- Biome difficulty adds HP, ATK and DEF the deeper you go\n" +
                "- Later levels within a biome add more\n" +
                "- Co-op: enemies gain +25% HP per extra player\n" +
                "- New Game Plus multiplies everything again\n\n" +
                "Bosses get a large HP multiplier on top of their base.\n\n" +
                "So a Plains zombie and a Cave zombie are very different fights. Use base stats to compare mobs, not to predict exact numbers."),
            new Page("Enemy Defense & Your Deflect",
                "Enemy DEF reduces the damage they take per hit (capped - high DEF never makes them immune).\n\n" +
                "Your Armor Class (AC) instead gives a chance for enemies to MISS you entirely. The higher your AC compared to the attacker's strength, the more often they whiff.\n\n" +
                "Hover any enemy to inspect its live stats in the panel.")
        )));
        basics.add(new Entry("Tile Types", "minecraft:grass_block", List.of(
            new Page("Arena Tiles",
                "Normal Tiles - Walk freely on grass, stone, sand, etc.\n\n" +
                "Obstacles - Trees, rocks, and walls block movement. Some can be mined with a pickaxe.\n\n" +
                "Water Tiles - Require a boat in your inventory to cross (consumed on entry), or a Turtle Helmet to walk on. Enemies cannot cross water unless aquatic (like Drowned - who move double speed on it)."),
            new Page("Bushes & Stealth",
                "Tall grass and ferns are BUSH tiles: step in to become hidden.\n\n" +
                "Hidden players can't be targeted by enemies unless the enemy is adjacent. Great for breaking line of sight, escaping ranged mobs, or setting up ambushes.\n\n" +
                "Bushes can be broken for 1 AP. The Silence trim extends your stealth range. In co-op, every party member who stays in a bush stays hidden."),
            new Page("Building & Fire",
                "Any plain full-cube block in your inventory can be PLACED as a temporary wall (lasts 4 turns). Funnel enemies, block charges, cover retreats.\n\n" +
                "Fire spreads! Burning tiles ignite adjacent flammable obstacles each turn. Lava, fire cones and fire trails can turn a forest arena into a hazard zone - for both sides.")
        )));
        basics.add(new Entry("Weapons & AP", "minecraft:diamond_sword", List.of(
            new Page("Attack Costs (AP)",
                "Each weapon has its own AP cost per attack:\n\n" +
                "1 AP - Swords, Hoes, Shovels, Bows, sticks/rods, corals, Trident melee\n" +
                "2 AP - Axes, Mace, Trident throw\n" +
                "4 AP - Crossbow (Quick Charge lowers it)\n\n" +
                "Chainmail (Rogue) set reduces melee attack cost by 1 (min 1).\n\n" +
                "Weapons lose durability on every attack; flimsy improvised weapons can break mid-fight."),
            new Page("Melee Weapons",
                "Swords (1 AP, Slashing) - solid damage, range 1.\n" +
                "Wood 5 / Stone 6 / Iron 9 / Gold 14 / Diamond 12 / Netherite 15.\n" +
                "Diamond Sword: 30% native crit. Netherite Sword: executes weakened foes.\n\n" +
                "Axes (2 AP, Cleaving) - heavy hits.\n" +
                "Wood 8 / Stone 11 / Iron 15 / Gold 24 / Diamond 21 / Netherite 27.\n" +
                "Chance per hit to ignore enemy armor.\n\n" +
                "Yes - GOLD weapons are top-tier in Craftics. Fragile, but ferocious."),
            new Page("Ranged Weapons",
                "Bows (1 AP) - range 3, need arrows. Power adds damage AND range.\n\n" +
                "Crossbows (4 AP) - fire in full straight lines across the whole arena (rook lines). Piercing, Multishot, Quick Charge all supported.\n\n" +
                "Tridents - Melee stab (1 AP) when adjacent. Throw (2 AP) up to 5 tiles in straight/diagonal lines; lodges in the ground, walk over to retrieve. Loyalty: ricochets then auto-returns. Riptide: dash attack. Channeling: lightning strike."),
            new Page("Special & Pet Weapons",
                "Hoes (1 AP) - Special damage type. Low base damage but +3 per Special affinity point. The main melee for effect-based builds.\n\n" +
                "Shovels (1 AP) - Pet damage type. Moderate damage boosted by Pet affinity. The main melee for pet/companion builds.\n\n" +
                "Blunt oddballs - Stick & Bamboo (stun chance), Blaze Rod (fire), Breeze Rod (wind burst). Cheap, fun, surprisingly viable early.")
        )));
        basics.add(new Entry("Damage Types", "minecraft:amethyst_shard", List.of(
            new Page("The Eight Types",
                "Every attack has a damage type. Every mob has weaknesses and resistances.\n\n" +
                "Slashing - swords\n" +
                "Cleaving - axes\n" +
                "Blunt - maces, sticks, rods\n" +
                "Ranged - bows, crossbows\n" +
                "Water - tridents, corals, water throwables\n" +
                "Special - hoes, sherds, horns, potions\n" +
                "Pet - shovels and your allies\n" +
                "Physical - bare fists\n\n" +
                "Hitting a weakness deals bonus damage; hitting a resistance is reduced. Check the Bestiary badges!"),
            new Page("Affinity",
                "Each damage type has an AFFINITY track. Points come from odd-numbered level-ups and gear.\n\n" +
                "Every affinity point adds +3 damage of that type, plus a per-type perk (see Affinities in Leveling & Stats).\n\n" +
                "Armor contributes too: every armor piece grants half an affinity point of its material's type - even in mixed sets. Mob-head helmets grant a full point.")
        )));
        basics.add(new Entry("Stacked Enemies", "minecraft:slime_ball", List.of(
            new Page("Mobs Riding Mobs",
                "Some enemies arrive stacked:\n\n" +
                "Zombie Stack - zombies on shoulders\n" +
                "Skeleton/Zombie Horseman - rider + mount\n" +
                "Piglin Cavalry - piglin riding a hoglin\n" +
                "Slime Tower - three slimes high\n" +
                "Blaze Tower - spinning fire column\n\n" +
                "Kill the bottom layer and the top keeps fighting from the ground. Towers attack harder while taller - topple them fast, or burst the rider first.")
        )));
        basics.add(new Entry("Food & Eating", "minecraft:golden_carrot", List.of(
            new Page("Eating in Combat",
                "Eating food costs 1 AP and heals based on the food's nourishment - from 2 HP for a berry up to a full heal from rich golden foods.\n\n" +
                "Golden Carrot is special: it's FREE to eat (0 AP) and grants +1 AP this turn. Emergency fuel!\n\n" +
                "Totem of Undying can be eaten for a full heal - or kept in inventory to auto-revive you at half HP when you'd die.\n\n" +
                "In co-op you can feed an adjacent teammate. Cake placed on a tile gives 3 shared bites of 2 HP each.")
        )));
        CATEGORIES.add(new Category("Combat Basics", "minecraft:iron_sword",
            "Learn the fundamentals of grid combat.", basics));

        // === ENEMY BESTIARY ===
        // Names must match entityTypeIdToMobName / getBossName (see class javadoc).
        // Stats shown are BASE values from biome data - they scale with biome, level and party size.
        List<Entry> enemies = new ArrayList<>();
        // --- Passive & neutral mobs ---
        enemies.add(mob("Cow", "minecraft:cow_spawn_egg",
            st("Passive", "4", "0", "0", "2", null, null, null, null, null),
            "Harmless farm animal. Tame with Wheat to recruit it. Drops beef on defeat."));
        enemies.add(mob("Pig", "minecraft:pig_spawn_egg",
            st("Passive", "4", "0", "0", "2", null, null, null, null, null),
            "Harmless farm animal. Tame with Carrot, Potato or Beetroot. Drops porkchop on defeat."));
        enemies.add(mob("Sheep", "minecraft:sheep_spawn_egg",
            st("Passive", "4", "0", "0", "2", null, null, null, null, null),
            "Harmless farm animal. Tame with Wheat. Drops mutton on defeat."));
        enemies.add(mob("Chicken", "minecraft:chicken_spawn_egg",
            st("Passive", "3", "0", "0", "2", null, null, null, null, null),
            "Harmless bird. Tame with any seeds (wheat, melon, pumpkin, beetroot). Drops chicken on defeat."));
        enemies.add(mob("Horse", "minecraft:horse_spawn_egg",
            st("Passive", "6", "0", "0", "2", null, null, null, null, null),
            "Tame with golden apples or golden carrots. Saddle it in the hub and add it to your party for a +3 Speed mount!"));
        enemies.add(mob("Wolf", "minecraft:wolf_spawn_egg",
            st("Neutral", "4", "0", "0", "3", null, null, "Special", null, null),
            "Passive until hit. Tame with a bone or beef to gain a loyal melee ally that dashes at enemies."));
        enemies.add(mob("Cat", "minecraft:cat_spawn_egg",
            st("Passive", "3", "1", "0", "2", null, null, null, null, null),
            "Skittish - it flees from you unless you're holding a fish. Tame with cod or salmon for a quick flanking ally."));
        enemies.add(mob("Fox", "minecraft:fox_spawn_egg",
            st("Passive", "3", "0", "0", "2", null, null, null, null, null),
            "A predator, not a coward: foxes hunt sheep and chickens in the arena with hit-and-run pounces, and they bite back if you attack them. Tame with sweet or glow berries."));
        enemies.add(mob("Rabbit", "minecraft:rabbit_spawn_egg",
            st("Passive", "2", "0", "0", "2", null, null, null, null, null),
            "Tiny and fast. Harmless. Tame with a carrot, golden carrot or dandelion."));
        enemies.add(mob("Goat", "minecraft:goat_spawn_egg",
            st("Neutral", "8", "1", "0", "2", null, null, null, "Blunt", null),
            "Charges and rams! Its thick skull shrugs off blunt hits. Tame with wheat."));
        enemies.add(mob("Parrot", "minecraft:parrot_spawn_egg",
            st("Passive", "2", "0", "0", "3", null, null, null, null, null),
            "Colorful jungle companion. Tame with wheat or melon seeds."));
        enemies.add(mob("Bee", "minecraft:bee_spawn_egg",
            st("Neutral", "3", "1", "0", "4", null, null, null, null, null),
            "Hurt one bee and EVERY bee in the level turns on you, poisoning on hit. Fast and annoying in groups. Tame with dandelion, poppy or blue orchid."));
        enemies.add(mob("Cod", "minecraft:cod_spawn_egg",
            st("Passive", "2", "0", "0", "2", null, null, null, null, null),
            "Aquatic. Only found in water biomes."));
        enemies.add(mob("Llama", "minecraft:llama_spawn_egg",
            st("Neutral", "8", "2", "1", "2", "1-2", null, null, null, null),
            "Passive until hit - then permanently aggressive. It backs off to keep its distance and spits at you. Tame with a hay block for a solid ranged ally. Found in Mountain biomes."));
        // --- Hostile: Overworld ---
        enemies.add(mob("Zombie", "minecraft:zombie_spawn_egg",
            st("Hostile", "6-12", "2-3", "0-1", "1", "1", null, "Blunt, Cleaving", null, null),
            "Basic melee grunt - slow but relentless. Gains +1 ATK per adjacent zombie, husk or drowned (horde bonus). Baby zombies are faster. Armed variants hit harder."));
        enemies.add(mob("Zombie Villager", "minecraft:zombie_villager_spawn_egg",
            st("Hostile", "6", "2", "0", "1", "1", null, "Blunt, Cleaving", null, null),
            "Zombified villager. Fights identically to a regular Zombie and shares its horde bonus. Spawns in Plains."));
        enemies.add(mob("Husk", "minecraft:husk_spawn_egg",
            st("Hostile", "10-12", "3-4", "1", "1", "1", null, "Blunt, Water", null, null),
            "Desert zombie variant. Tougher in dry heat, and it joins zombie hordes for the same +1 ATK stacking bonus."));
        enemies.add(mob("Drowned", "minecraft:drowned_spawn_egg",
            st("Hostile", "9", "3", "1", "1", "1-3", null, "Blunt, Cleaving", "Water", null),
            "Aquatic zombie that moves DOUBLE speed through water. Half of them carry tridents and throw diagonally at range 3; the rest are pure melee."));
        enemies.add(mob("Skeleton", "minecraft:skeleton_spawn_egg",
            st("Hostile", "6-12", "2-5", "0-2", "2", "3", null, "Blunt", "Ranged, Physical", null),
            "Ranged archer. Keeps distance and retreats if you close in. Cut it off with walls or rush it down."));
        enemies.add(mob("Stray", "minecraft:stray_spawn_egg",
            st("Hostile", "6", "3", "0", "2", "3", null, "Blunt", "Ranged, Water, Physical", null),
            "Frozen skeleton variant. Slowness arrows!"));
        enemies.add(mob("Bogged", "minecraft:bogged_spawn_egg",
            st("Hostile", "10", "3", "0", "2", "3", null, "Blunt", "Special, Physical", null),
            "Swamp skeleton found in Trial Chambers. Fires poison-tipped arrows - cleanse fast or outlast the ticks."));
        enemies.add(mob("Creeper", "minecraft:creeper_spawn_egg",
            st("Hostile", "10-14", "4-6", "0-1", "2", null, null, "Ranged, Cleaving", "Slashing, Physical", null),
            "Sneaks close then detonates! Massive AoE damage to everything nearby - including other enemies. Kill it at range or bait the blast into their lines."));
        enemies.add(mob("Spider", "minecraft:spider_spawn_egg",
            st("Hostile", "10-12", "3-4", "1-2", "3", "1", null, "Cleaving, Special", "Blunt, Physical", null),
            "Ambush predator that pounces from distance or drops from the ceiling. Chooses between attacking and shooting cobwebs to slow you or block escape routes."));
        enemies.add(mob("Cave Spider", "minecraft:cave_spider_spawn_egg",
            st("Hostile", "8", "3", "0", "2", "1", null, "Cleaving, Special", "Blunt, Physical", null),
            "Smaller spider that poisons on hit. Pounces 2 tiles (one shorter than a regular Spider) and can drop on you from the ceiling. Lurks in Jungle arenas and Trial Chambers."));
        enemies.add(mob("Silverfish", "minecraft:silverfish_spawn_egg",
            st("Hostile", "4-15", "2-4", "0-2", "2", "1", null, "Cleaving, Blunt", "Ranged", null),
            "Flanking swarmer that approaches from the opposite side of other enemies. When one is hurt, the whole swarm speeds up. Don't let a pack surround you. Spawns in the Deep Dark and from The Hollow King's rubble."));
        enemies.add(mob("Pillager", "minecraft:pillager_spawn_egg",
            st("Hostile", "8", "3", "0-1", "2", "3", null, "Slashing, Cleaving", "Ranged, Physical", null),
            "Crossbow-wielding raider. Fires from distance. Found in Forest and Mountain biomes."));
        enemies.add(mob("Vindicator", "minecraft:vindicator_spawn_egg",
            st("Hostile", "9-10", "4", "1", "3", "1", null, "Ranged", "Cleaving, Physical", null),
            "Rook-movement axe berserker - it dashes in straight lines like a chess rook, and charge damage scales with distance traveled. When enraged (+50% ATK) its hits also knock you back."));
        enemies.add(mob("Ravager", "minecraft:ravager_spawn_egg",
            st("Hostile", "30", "6", "2", "3", "1", null, "Special", "Blunt, Ranged, Physical", null),
            "Bull Rush: charges up to 3 tiles in a straight line for heavy damage + knockback 2. If it can't charge, it stomps every adjacent tile. Always aggressive, very tanky, and it RESISTS arrows - bring Special damage or heavy melee. Spawns in Forest."));
        enemies.add(mob("Evoker", "minecraft:evoker_spawn_egg",
            st("Hostile", "7", "3", "0", "2", "4", null, "Slashing, Cleaving", "Special, Physical", null),
            "Fragile illager caster. Summons vex fangs in a line from distance. Low HP but devastating if ignored - prioritize it over front-line raiders."));
        enemies.add(mob("Vex", "minecraft:vex_spawn_egg",
            st("Summon", "3", "2", "0", "3", "1", null, "Slashing", "Ranged, Physical", null),
            "Phasing nuisance summoned by The Hexweaver. Dies to a stiff breeze, but stings every turn it's alive. Swat them with sweeping melee."));
        enemies.add(mob("Witch", "minecraft:witch_spawn_egg",
            st("Hostile", "12", "5", "2", "2", "3", null, "Slashing, Cleaving", "Special, Physical", "Water"),
            "Throws harmful potions from range and channels with raised arms before big throws. Her poison flask deals no impact damage - it's pure poison - while her harming flask hits for full. Immune to Water damage, so leave the trident at home."));
        enemies.add(mob("Phantom", "minecraft:phantom_spawn_egg",
            st("Hostile", "10-14", "5-7", "0-1", "4", "1-3", null, "Ranged", "Slashing", null),
            "Flying swooper. Dives from distance, hits, then retreats out of melee reach - each phantom builds its own dive-speed streak. Hard to pin down; ranged weapons preferred."));
        enemies.add(mob("Slime", "minecraft:slime_spawn_egg",
            st("Hostile", "8", "3", "0", "2", "1", null, "Water", "Blunt", null),
            "Bouncy blob that hops and crashes. Arrives stacked as a Slime Tower - the tower hits harder while tall, and layers keep fighting when toppled."));
        enemies.add(mob("Creaking", "minecraft:creaking_spawn_egg",
            st("Hostile", "4+", null, null, "2", "1", null, null, null, null),
            "Pale Garden horror. INVULNERABLE while its Creaking Heart stands - find and destroy the heart block, then the creaking crumbles. Until then, run."));
        // --- Hostile: Nether ---
        enemies.add(mob("Blaze", "minecraft:blaze_spawn_egg",
            st("Hostile", "14", "6", "0", "2", "4", null, "Water", "Special, Ranged, Physical", null),
            "Hovering ranged attacker. Fireball deals area damage. Soak it or rush it - arrows bounce off."));
        enemies.add(mob("Breeze", "minecraft:breeze_spawn_egg",
            st("Mini-Boss", "20", "5", "2", "3", "3", null, "Blunt", "Ranged", null),
            "Trial Chamber signature mob. Leaps around the arena firing wind bursts that shove you out of position. Pin it in a corner and crush it."));
        enemies.add(mob("Ghast", "minecraft:ghast_spawn_egg",
            st("Hostile", "12", "6", "0", "1", "5", "2x2", "Ranged, Slashing", "Special, Physical", null),
            "Slow but extreme range. Fireball explosions! Its huge body can't dodge - punish it from afar."));
        enemies.add(mob("Magma Cube", "minecraft:magma_cube_spawn_egg",
            st("Hostile", "8", "3", "1", "3", "1", "2x2", "Water", "Blunt", null),
            "Bouncy lava slime. Splits on death!"));
        enemies.add(mob("Wither Skeleton", "minecraft:wither_skeleton_spawn_egg",
            st("Hostile", "16", "7", "0-1", "3", "1-2", null, "Blunt, Water", "Special, Physical", null),
            "Relentless pursuer with a stone sword. Fast for its size - don't assume you can kite it."));
        enemies.add(mob("Hoglin", "minecraft:hoglin_spawn_egg",
            st("Hostile", "14", "5", "2", "3", "1", "2x2", "Special", "Blunt, Physical", null),
            "Charging beast that rams like a goat twice its size. Hits hard!"));
        enemies.add(mob("Piglin", "minecraft:piglin_spawn_egg",
            st("Hostile", "10-12", "4-5", "1", "2", "4", null, "Special, Water", "Ranged, Physical", null),
            "Fights with crossbow or golden sword depending on what it spawned holding. Outside combat, piglins still love gold - see the Piglin Barter event."));
        enemies.add(mob("Piglin Brute", "minecraft:piglin_brute_spawn_egg",
            st("Hostile", "20", "7", "3", "2", "1", null, "Special, Water", "Blunt, Slashing, Physical", null),
            "Elite piglin with a golden axe. Ignores gold, ignores fear, hits like a truck. Dramatically tougher than a regular Piglin. Spawns in Crimson Forest."));
        enemies.add(mob("Zombified Piglin", "minecraft:zombified_piglin_spawn_egg",
            st("Neutral", "8-10", "3-4", "1", "1", "1", null, "Blunt, Cleaving", "Special, Physical", null),
            "Neutral until hit - then every zombified piglin nearby aggros on you too. Safe to pass through; suicidal to swing at first."));
        // --- Hostile: End ---
        enemies.add(mob("Enderman", "minecraft:enderman_spawn_egg",
            st("Hostile", "16-22", "6-9", "1-3", "5", "1", null, "Water, Special", "Ranged, Physical", null),
            "Aggressive phase-shifting teleporter. Hunts in assault cycles of 2-3 rapid teleport-strikes before blinking away, and dodges sideways when hit. Below 50% HP it enters Frenzy: never retreats, +50% damage, relentless strikes every turn."));
        enemies.add(mob("Endermite", "minecraft:endermite_spawn_egg",
            st("Hostile", "8", "3", "0", "2", "1", null, "Cleaving, Water", "Ranged, Physical", null),
            "Tiny void pest that blinks 2-3 tiles to reach you and attacks the instant it's adjacent. Never idles - if blocked, it blinks somewhere nearby and rushes in next turn. Spawns in the Warped Forest and with The Void Herald."));
        enemies.add(mob("Shulker", "minecraft:shulker_spawn_egg",
            st("Hostile", "14-18", "6-8", "2", "1", "4-5", null, "Blunt", "Ranged, Slashing, Physical", null),
            "Stationary turret. Levitation projectiles! Close the gap and crack the shell with blunt force."));
        enemies.add(mob("End Crystal", "minecraft:end_crystal",
            st("Hazard", null, null, null, null, null, null, null, null, null),
            "Dragon's Nest hazard. Destructible - but it detonates when killed, damaging everything within 2 tiles. Pop it from range, or lure enemies next to it first."));
        // --- Compatibility mobs (DYNAMIC: each block only adds entries when the
        //     source mod is installed). Names must match entityTypeIdToMobName so
        //     they auto-unlock on encounter via unlockBestiaryForCombat. Icons use
        //     "moddedSpawnEgg|vanillaFallback" so they render with or without the
        //     mod's items present. Base stats are taken from each mod's compat class. ---
        FabricLoader loader = FabricLoader.getInstance();

        // Creeper Overhaul - 12 biome-matched variants. They share the vanilla
        // creeper's weakness/resistance profile; the variant identity is the blast.
        if (loader.isModLoaded("creeperoverhaul")) {
            String cWeak = "Ranged, Cleaving";
            String cResist = "Slashing, Physical";
            enemies.add(mob("Desert Creeper", "creeperoverhaul:desert_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "8", "3", "0", "2", null, null, cWeak, cResist, null),
                "Desert creeper variant. Short fuse, detonates a turn sooner than normal. Blast inflicts Blindness. Replaces the creeper in Desert."));
            enemies.add(mob("Jungle Creeper", "creeperoverhaul:jungle_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "8", "3", "0", "2", null, null, cWeak, cResist, null),
                "Jungle creeper variant. Camouflages until within 3 tiles. Blast inflicts Poison. Replaces the creeper in Jungle."));
            enemies.add(mob("Cave Creeper", "creeperoverhaul:cave_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "8", "3", "0", "2", null, null, cWeak, cResist, null),
                "Underground creeper variant. Blast inflicts Blindness and Mining Fatigue. Replaces the creeper in Caverns and Deep Dark."));
            enemies.add(mob("Bamboo Creeper", "creeperoverhaul:bamboo_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "8", "3", "0", "2", null, null, cWeak, cResist, null),
                "Jungle creeper variant, arrives stacked on another mob. Camouflages until within 4 tiles. Blast inflicts Poison. Found in Jungle."));
            enemies.add(mob("Dripstone Creeper", "creeperoverhaul:dripstone_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "9", "3", "0", "2", null, null, cWeak, cResist, null),
                "Caverns creeper variant. Piercing blast hits harder than a normal creeper. 9 HP. Found in Caverns."));
            enemies.add(mob("Snowy Creeper", "creeperoverhaul:snowy_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "8", "3", "0", "2", null, null, cWeak, cResist, null),
                "Snowy creeper variant. Blast inflicts Slowness, applies Weakness on hit. Found in Snowy Tundra."));
            enemies.add(mob("Hills Creeper", "creeperoverhaul:hills_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "8", "3", "0", "3", null, null, cWeak, cResist, null),
                "Mountain creeper variant. +1 Speed and a larger blast radius. Applies Weakness on hit. Found in Mountains."));
            enemies.add(mob("Dark Oak Creeper", "creeperoverhaul:dark_oak_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "10", "3", "1", "2", null, null, cWeak, cResist, null),
                "Forest creeper variant. Tankiest creeper at 10 HP and 1 DEF. Otherwise behaves like a normal creeper. Found in Forest."));
            enemies.add(mob("Plains Creeper", "creeperoverhaul:plains_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "7", "2", "0", "2", null, null, cWeak, cResist, null),
                "Plains creeper variant. Weaker than a normal creeper with a smaller blast. Found in Plains."));
            enemies.add(mob("Beach Creeper", "creeperoverhaul:beach_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "8", "3", "0", "2", null, null, cWeak, cResist, null),
                "River creeper variant. Blast knocks you back 3 tiles and applies Soaked. Found in River."));
            enemies.add(mob("Ocean Creeper", "creeperoverhaul:ocean_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "8", "3", "0", "2", null, null, cWeak, cResist, null),
                "River creeper variant. Larger blast radius with heavy Soaked. Found in River."));
            enemies.add(mob("Mushroom Creeper", "creeperoverhaul:mushroom_creeper_spawn_egg|minecraft:creeper_spawn_egg",
                st("Hostile", "8", "3", "0", "2", null, null, cWeak, cResist, null),
                "Chorus Grove creeper variant. Wide blast inflicts Poison. Found in Chorus Grove."));
        }

        // Variants & Ventures - 4 themed zombie/skeleton variants.
        if (loader.isModLoaded("variantsandventures")) {
            enemies.add(mob("Gelid", "variantsandventures:gelid_spawn_egg|minecraft:zombie_spawn_egg",
                st("Hostile", "8", "2", "0", "1", "1", null, "Blunt, Cleaving", null, null),
                "Frozen zombie variant. Slow melee walker like a zombie. Applies Weakness on hit. Found in Snowy Tundra and Mountains."));
            enemies.add(mob("Thicket", "variantsandventures:thicket_spawn_egg|minecraft:zombie_spawn_egg",
                st("Hostile", "8", "3", "0", "1", "1", null, "Blunt, Cleaving", null, null),
                "Jungle zombie variant. Hits harder than a normal zombie and applies Poison on hit. Found in Jungle."));
            enemies.add(mob("Verdant", "variantsandventures:verdant_spawn_egg|minecraft:skeleton_spawn_egg",
                st("Hostile", "6", "2", "0", "2", "3", null, "Blunt", "Ranged, Physical", null),
                "Jungle skeleton variant. Ranged kiter at range 3 with Poison arrows. Found in Jungle and Forest."));
            enemies.add(mob("Murk", "variantsandventures:murk_spawn_egg|minecraft:skeleton_spawn_egg",
                st("Hostile", "7", "3", "0", "2", "2", null, "Blunt", "Ranged, Physical", null),
                "River skeleton variant armed with shears. Range 2, but hits harder up close and applies Soaked on hit. Found in River."));
        }

        // Artifacts - the Mimic ambush from the Abandoned Campsite event.
        if (loader.isModLoaded("artifacts")) {
            enemies.add(mob("Mimic", "artifacts:mimic_spawn_egg|minecraft:chest",
                st("Ambush", "15+", "10", "2", null, null, null, null, null, null),
                "Ambusher disguised as loot. Only appears in the Abandoned Campsite event between levels. HP scales to 75% of the biome boss, minimum 15. Each turn it alternates two attacks: Tantrum hops 4-6 times through nearby tiles and deals 10 damage if it lands on you; Dash charges in a straight line until it hits a wall for 10 damage, shoving anything in its path. Drops a random artifact on defeat."));
        }

        // --- Bosses (named exactly as CombatManager.getBossName) ---
        // Boss HP gets a large multiplier on top of base + biome scaling.
        enemies.add(mob("The Revenant", "minecraft:zombie_head",
            st("Boss", "30", "1", "1", "1", "1", null, "Blunt, Cleaving", null, null),
            new Page("The Revenant",
                "Plains boss - an undead knight. Slow, but the arena fills up fast.\n\n" +
                "Abilities:\n" +
                "- Raise the Dead: plants grave markers that hatch Zombies - destroy the markers first!\n" +
                "- Death Charge: 3-tile line charge, bonus ATK\n" +
                "- Gravefire Grid: telegraphs a magma checker-grid for 1 turn\n" +
                "- Shield Bash: half-damage hit, knockback 2 (on cooldown)\n\n" +
                "Phase 2 - Undying Rage: regeneration, faster summons, fire trail on charge.")));
        enemies.add(mob("The Hexweaver", "minecraft:totem_of_undying",
            st("Boss", "35", "5", "1", "2", "4", null, "Slashing, Cleaving", "Special, Physical", null),
            new Page("The Hexweaver",
                "Dark Forest boss - an evoker archmage.\n\n" +
                "Abilities:\n" +
                "- Hex Snare: telegraphed curse + 2-tile pull\n" +
                "- Runic Prison: cardinal runes erupt + brief cage\n" +
                "- Vex Swarm: summons 2 Vexes every 3 turns\n" +
                "- Fang Line: 5-tile vex fang line, 4 dmg\n" +
                "- Cursed Fog: 3x3 debuff zone\n" +
                "- Hex Bolt: ranged ATK + Slowness\n\n" +
                "Phase 2 - Arcane Fury: teleports away, full cross fangs, 3 Vexes per swarm (up to 6).")));
        enemies.add(mob("The Frostbound Huntsman", "minecraft:blue_ice",
            st("Boss", "40", "4", "2", "2", "3", null, "Blunt", "Ranged, Water, Physical", null),
            new Page("The Frostbound Huntsman (Phase 1)",
                "Snowy Tundra boss - a stray sharpshooter that controls lanes and slows your team.\n\n" +
                "Moveset:\n" +
                "- Harpoon Pull: telegraphed forced-move. The warning paints a FULL row/column lane, then drags you 2 tiles toward the boss.\n" +
                "- Whiteout Ring: burst around your tile with ONE safe gap.\n" +
                "- Blizzard: 3x3 blast + Slowness.\n" +
                "- Ice Wall: drops a 3-tile obstacle line to block paths.\n" +
                "- Glacial Trap: 2x2 freeze zone, damage + stun pressure.\n" +
                "- Frost Arrow: long-range shot that applies Slowness.\n\n" +
                "Pattern notes: at long range it favors Frost Arrow pressure and uses walls/harpoon to deny easy kiting."),
            new Page("The Frostbound Huntsman (Phase 2)",
                "Permafrost phase turns the arena into a control puzzle.\n\n" +
                "Phase 2 changes:\n" +
                "- Speed increases (harder to pin down).\n" +
                "- Passive Ice Wall every turn for constant lane denial.\n" +
                "- Passive freeze tiles every 2 turns.\n" +
                "- Blizzard center now stuns.\n" +
                "- Cooldowns are reduced across the kit.\n" +
                "- Expanding Frost Wave pressure can pulse while other attacks continue.\n\n" +
                "Counterplay: break sightlines, keep escape lanes open, and respect full-lane harpoon warnings before ending your turn.")));
        enemies.add(mob("The Rockbreaker", "minecraft:iron_axe",
            st("Boss", "40", "5", "2", "2", "1", null, "Ranged", "Cleaving, Physical", null),
            new Page("The Rockbreaker",
                "Stony Peaks boss - an aggressive melee brawler. Every attack knocks you around the arena.\n\n" +
                "Abilities:\n" +
                "- Ground Pound: instant 2x2 melee AoE + knockback 2 (no telegraph)\n" +
                "- Charge: dashes toward you, heavy damage + knockback 3\n" +
                "- Seismic Slam: cross pattern + knockback 2\n" +
                "- Boulder Toss: range 4, damage + knockback + creates an obstacle\n" +
                "- Avalanche: full-row attack that pushes you downward\n\n" +
                "Phase 2 - Unstoppable: +1 Speed, bigger knockbacks, shorter cooldowns, charges smash through obstacles.")));
        enemies.add(mob("The Tidecaller", "minecraft:trident",
            st("Boss", "45", "5", "2", "2", "1", null, "Blunt, Cleaving", "Water", null),
            new Page("The Tidecaller",
                "River Delta boss - a drowned tide-priest.\n\n" +
                "Abilities:\n" +
                "- Tidal Wave: 2-tile-wide flood column\n" +
                "- Trident Storm: 3 tridents in a spread\n" +
                "- Riptide Charge: water charge, knockback 2\n" +
                "- Call of the Deep: summons Drowned onto water tiles\n\n" +
                "Phase 2 - Deluge: floods half the arena once (your tile is spared), +2 ATK while on water.\n\n" +
                "Bring boats or a Turtle Helmet - control of the water is control of the fight.")));
        enemies.add(mob("The Sandstorm Pharaoh", "minecraft:sandstone",
            st("Boss", "45", "6", "2", "1", "1", null, "Blunt, Water", null, null),
            new Page("The Sandstorm Pharaoh",
                "Desert boss - a husk king of the dunes.\n\n" +
                "Abilities:\n" +
                "- Plant Mine: invisible mine, 6 dmg + 1-turn stun on contact\n" +
                "- Sand Burial: 2x2 quicksand stun\n" +
                "- Sandstorm: 3x3 AoE, 3 dmg + accuracy debuff\n" +
                "- Curse of the Sands: tiles you leave become quicksand\n\n" +
                "Phase 2 - Tomb Wrath: 2 mines per turn, 3x3 burial, summons 2 Husks.")));
        enemies.add(mob("The Broodmother", "minecraft:cobweb",
            st("Boss", "50", "5", "2", "3", "1", "2x2", "Cleaving, Special", "Blunt, Physical", null),
            new Page("The Broodmother",
                "Jungle boss - a monstrous spider matriarch. Alternates between HUNTING you and NESTING to lay egg sacs.\n\n" +
                "Abilities:\n" +
                "- Egg Sacs: 1-HP destructible eggs that hatch Cave Spiders (the brood is capped - smash eggs to starve it)\n" +
                "- Web Spray: 3x3 stun + slow\n" +
                "- Venomous Bite: ATK + Poison\n" +
                "- Pounce: leap 3 tiles, 2x2 AoE\n\n" +
                "Phase 2 - Nest Awakening: faster, pounce range +1, webs poison, egg sacs respawn.")));
        enemies.add(mob("The Hollow King", "minecraft:tnt",
            st("Boss", "55", "6", "3", "1", "1", null, "Blunt, Cleaving", null, null),
            new Page("The Hollow King",
                "Caverns boss - a buried tyrant with a miner's arsenal.\n\n" +
                "Abilities:\n" +
                "- Demolition Cache: telegraphed TNT that detonates next round\n" +
                "- Rubble Toss: mines an obstacle, throws it at a marked tile\n" +
                "- Cave-In: boulders fall on telegraphed tiles\n" +
                "- Miner's Fury: line charge that destroys obstacles\n" +
                "- Swarm Call: 3-4 Silverfish from the rubble\n" +
                "- Lights Out: casts darkness - enemies gain +2 ATK in the dark\n\n" +
                "Phase 2 - Total Collapse: permanent darkness, automatic cave-ins, extra TNT pressure.\n\n" +
                "COUNTERPLAY: bring Torches, Lanterns or Campfires to hold light zones.")));
        enemies.add(mob("The Warden", "minecraft:echo_shard",
            st("Boss", "60", "8", "4", "3", "1", null, "Ranged", "Blunt, Slashing, Physical", null),
            new Page("The Warden",
                "Deep Dark boss. It is BLIND - it hunts by vibration.\n\n" +
                "Move 3+ tiles in a turn and it locks onto you. Creep short distances to stay off its senses, or THROW items at empty tiles to send it chasing echoes.\n\n" +
                "Abilities:\n" +
                "- Tremor Stomp: AoE shockwave around itself\n" +
                "- Sculk Spread: corrupts floor tiles\n" +
                "- Sculk Shriekers: alarm blocks that reveal you - destroy them\n" +
                "- Darkness Pulse: dims the arena\n\n" +
                "Phase 2 - The Ancient Awakens: unlocks Sonic Boom, a straight-line blast that IGNORES defense.")));
        enemies.add(mob("The Molten King", "minecraft:magma_cream",
            st("Boss", "55", "6", "3", "2", "1", "4x4", "Water", "Blunt", null),
            new Page("The Molten King",
                "Nether Wastes boss - a colossal magma cube monarch.\n\n" +
                "SPLIT MECHANIC: at half HP the 4x4 king splits into two 2x2 copies that keep ALL boss abilities. They do not split again.\n\n" +
                "Abilities:\n" +
                "- Magma Eruption: teleport-leap + 3x3 blast + fire ring\n" +
                "- Lava Cage: rings you with fire, blocking escape\n" +
                "- Absorb: merges with a nearby magma cube to heal\n" +
                "- Melee: knockback 1 on hit\n\n" +
                "Phase 2 - Meltdown: arena shrinks via lava rings, faster cooldowns.")));
        enemies.add(mob("The Wailing Revenant", "minecraft:ghast_tear",
            st("Boss", "90", "10", "3", "0", "6", "2x2", "Ranged, Slashing", "Special, Physical", null),
            new Page("The Wailing Revenant",
                "Soul Sand Valley boss - a colossal ghast that hovers OUTSIDE the arena edge. Attack the front row of tiles to hit it.\n\n" +
                "No regular ghasts spawn here; only Wither Skeletons answer its call.\n\n" +
                "Abilities:\n" +
                "- Fireball Barrage: 3 fireballs fly across the arena\n" +
                "- Raining Fireballs: half the arena warned, heavy damage\n" +
                "- Magma Rows: random rows turn to magma for 2 turns\n" +
                "- Summon Wither Skeletons: 2 at a time\n\n" +
                "Phase 2 - Requiem: 5 fireballs, 3 magma rows, 3 skeletons (up to 6).")));
        enemies.add(mob("The Bastion Brute", "minecraft:golden_axe",
            st("Boss", "65", "8", "3", "3", "1", null, "Special, Water", "Blunt, Slashing, Physical", null),
            new Page("The Bastion Brute",
                "Crimson Forest boss - a war-chief in piglin wargear.\n\n" +
                "Abilities:\n" +
                "- Gore Charge: 4-tile charge, bonus ATK, knockback 3 (stops at deep water)\n" +
                "- Ground Slam: cross-pattern slam that sets the floor on fire\n" +
                "- Rampage: AoE on all adjacent tiles\n" +
                "- Summon Pack: calls 2 Piglins (on cooldown)\n\n" +
                "Phase 2 - Blood Frenzy: +4 ATK, Speed 4, fire trail, 2-tile rampage.")));
        enemies.add(mob("The Void Walker", "minecraft:ender_pearl",
            st("Boss", "65", "9", "2", "3", "1", null, "Water, Special", "Ranged, Physical", null),
            new Page("The Void Walker",
                "Warped Forest boss - an enderman that bends space.\n\n" +
                "Abilities:\n" +
                "- Void Rift: portal pairs - step on one, exit the other. First traversal each fight grants YOU Strength + Speed\n" +
                "- Mirror Image: 2 decoy clones (8 HP, take double damage)\n" +
                "- Phase Strike: teleports behind you + attacks\n" +
                "- Void Pull: drags you 2 tiles toward it\n" +
                "- Void Beam / Null Burst / Ender Roar: ranged void artillery\n\n" +
                "Phase 2 - Reality Shatter: permanent rifts, 3 clones, pull range 3.")));
        enemies.add(mob("The Wither", "minecraft:wither_skeleton_skull",
            st("Boss", "110", "10", "5", "2", "5", "2x2", "Water", "Slashing, Blunt, Ranged, Physical", null),
            new Page("The Wither",
                "Basalt Deltas final boss.\n\n" +
                "Abilities:\n" +
                "- Wither Skull Barrage: 3 skull projectiles (P2: 5). Skulls have 6 HP and hit for 7 - shoot them down!\n" +
                "- Decay Aura: passive radius-2 (P2: 3) damage + Wither, ramping each turn\n" +
                "- Melee: applies Wither II for 4 turns\n" +
                "- Summon Wither Skeletons: 2 every 4 turns (max 4)\n" +
                "- Charge: 4-tile dash, bonus ATK (P2 leaves a fire trail)\n\n" +
                "Phase 2 - Wither Armor: heavy ranged resist, an 8-damage transition blast, more skulls, larger aura.\n\n" +
                "The Wither effect ramps with duration - cleanse fast or fight in short bursts. Water damage is its only soft spot.")));
        enemies.add(mob("The Void Herald", "minecraft:end_rod",
            st("Boss", "70", "10", "3", "3", "1", null, "Water, Special", "Ranged, Physical", null),
            new Page("The Void Herald",
                "Outer End Islands boss - fight on floating platforms over the abyss.\n\n" +
                "Abilities:\n" +
                "- Void Gale: pushes everything toward the void edge\n" +
                "- Lightning Strike: marks a tile, +-pattern blast next turn\n" +
                "- Platform Collapse: permanently removes 2x2 of floor\n" +
                "- Blink Assault: teleport + hit 3 tiles\n\n" +
                "Phase 2 - Oblivion: automatic collapses, gale pushes 3 tiles, 2 lightning marks, Speed 4, summons Endermites every 3 turns.\n\n" +
                "Watch your footing - the boss doesn't have to kill you if the void does.")));
        enemies.add(mob("The Shulker Architect", "minecraft:shulker_shell",
            st("Boss", "75", "9", "4", "1", "5", "2x2", "Blunt", "Ranged, Slashing, Physical", null),
            new Page("The Shulker Architect",
                "End City boss - a master builder waging siege warfare.\n\n" +
                "Abilities:\n" +
                "- Bullet Storm: telegraphed volley of 4 bullets (P2: 6) on marked tiles\n" +
                "- Deploy Turret: stationary shulker turret (6 HP, range 4, max 3)\n" +
                "- Fortify Shell: 80% damage reduction for 1 turn\n" +
                "- Teleport Link: swaps to a turret's position\n\n" +
                "Phase 2 - Defense Protocol: reflect shell, auto-deploys a turret every 3 turns, turret limit 5.\n\n" +
                "Kill turrets first or the Architect will always have an escape hatch.")));
        enemies.add(mob("The Chorus Mind", "minecraft:chorus_fruit",
            st("Boss", "80", "12", "3", "2", "1", null, "Water, Special", "Ranged, Physical", null),
            new Page("The Chorus Mind",
                "Chorus Grove boss - the grove itself is the weapon.\n\n" +
                "Abilities:\n" +
                "- Chorus Bloom: grows obstacle plants; the boss can teleport beside any of them\n" +
                "- Entangle: 2x2 root zone (P2: 3x3), immobilize + damage\n" +
                "- Chorus Bomb: range-4 2x2 blast + random teleport on hit\n" +
                "- Resonance Cascade: EVERY plant pulses AoE damage\n\n" +
                "Phase 2 - Overgrowth: plants auto-spread, cascades fire automatically, boss teleports every turn.\n\n" +
                "Cut the garden down - every plant you destroy is one less bomb.")));
        enemies.add(mob("The Ender Dragon", "minecraft:dragon_egg",
            st("Boss", "100", "11", "5", "4", null, null, "Ranged, Special", "Water, Physical", null),
            new Page("The Ender Dragon",
                "Dragon's Nest final boss. Clearing it triggers New Game Plus.\n\n" +
                "The dragon circles OFF-STAGE for most of the fight, strafing the arena:\n" +
                "- Breath Wave / Breath Cross: telegraphed dragon's breath patterns\n" +
                "- Swoop: diving strike along a marked line\n\n" +
                "Every few turns it PERCHES: it lands, becomes targetable, and defends itself with Wing Buffet and Tail Slam. That's your damage window.\n\n" +
                "End Crystals on the field empower it - destroy them (carefully, they explode).\n\n" +
                "Burst it on the perch, survive the strafes, and the End is yours.")));
        CATEGORIES.add(new Category("Enemy Bestiary", "minecraft:zombie_head",
            "Know your foes. Entries unlock as you encounter them.", enemies));

        // === LEVELING & STATS ===
        List<Entry> progression = new ArrayList<>();
        progression.add(new Entry("Leveling Up", "minecraft:experience_bottle", List.of(
            new Page("How to Level Up",
                "Defeat a Biome Boss (final level of each biome) to gain a Level Up.\n\n" +
                "EVEN levels grant 1 Stat Point.\n" +
                "ODD levels grant 1 Affinity Point.\n\n" +
                "Re-killing the same biome's boss has diminishing returns - each biome needs more repeat kills for the next level (1st kill, then 3rd, 7th, 15th...). Push forward for steady growth.\n\n" +
                "Respec anytime: H for stats, J for affinities."),
            new Page("Emeralds",
                "Winning combat earns Emeralds. The reward scales with biome difficulty and enemy count.\n\n" +
                "Boss bonus: +3 emeralds.\n" +
                "Kill streaks pay out too: chain 3+ kills without a miss for bonus emeralds.\n\n" +
                "Spend at the Wandering Trader between levels!")
        )));
        progression.add(new Entry("Stats Guide", "minecraft:nether_star", List.of(
            new Page("Combat Stats",
                "Speed (base 3) - Tiles moved per turn. +1/point.\n\n" +
                "Action Points (base 3) - Actions per turn. +1/point.\n\n" +
                "Melee Power - +1 melee damage/point.\n\n" +
                "Ranged Power - +1 ranged damage/point."),
            new Page("Defensive & Utility",
                "Vitality - +8 max HP/point.\n\n" +
                "Defense - +1 Armor Class/point. AC gives enemies a chance to MISS you - the gap between your AC and their attack decides how often.\n\n" +
                "Luck - +5% crit chance/point, +2%/point to weapon procs and loot rolls.\n\n" +
                "Resourceful - +1 emerald/level & trader discounts."),
            new Page("Stat Builds",
                "Glass Cannon: Max Power + AP for burst.\n\n" +
                "Tank: Vitality + Defense to survive.\n\n" +
                "Speedster: Speed + AP to outmaneuver.\n\n" +
                "Merchant: Resourceful + Luck for loot.")
        )));
        progression.add(new Entry("Affinities", "minecraft:enchanting_table", List.of(
            new Page("Affinity Points",
                "Odd-numbered level-ups grant Affinity Points. Each point in a damage type gives +3 damage of that type AND stacks its perk:\n\n" +
                "Slashing - bleed builds\n" +
                "Cleaving - armor-crush builds\n" +
                "Blunt - AoE/stun builds\n" +
                "Ranged - 5% chain-ricochet chance per point\n" +
                "Water - soak/lightning builds\n" +
                "Special - 3% chance per point for ANY attack to cost 0 AP\n" +
                "Pet - +10 ally HP per point, +1 party size per point\n" +
                "Physical - 3% counter-attack chance per point\n\n" +
                "Press J to respec affinities. Gear adds more: armor pieces give half a point of their material's type, mob heads a full point."),
            new Page("Picking a Lane",
                "Affinity rewards commitment - +3 damage per point snowballs fast on cheap 1-AP weapons.\n\n" +
                "Match your affinity to your loadout (see the Loadout entries below), then pick armor, trims and a mob head that feed the same type.\n\n" +
                "Splashing 1-2 points into Pet for the bigger battle party is always solid.")
        )));
        progression.add(new Entry("§cSlashing Loadouts", "minecraft:diamond_sword", List.of(
            new Page("Bleed Striker",
                "§cBleed Striker. Stack bleed, finish quick.\n\n" +
                "Weapon: Diamond or Netherite Sword + Sharpness.\n" +
                "Each Sharpness hit adds a Bleed stack; the target takes damage equal to its stacks every turn (1, 2, 3...). Classified as Special damage.\n\n" +
                "Armor: Chainmail set (Rogue). +2 Slashing, -1 AP cost.\n" +
                "Trim: Bolt = +1 Slashing per piece.\n\n" +
                "Tip: Sweeping Edge spreads hits to adjacent foes. Every extra hit is another bleed stack."),
            new Page("First-Strike Assassin",
                "§cFirst-Strike Assassin. Open with a guaranteed crit.\n\n" +
                "Weapon: Diamond Sword (30% native crit) or Netherite Sword (execute under 30% HP).\n\n" +
                "Hybrid: Chain+Netherite (Ambush): first attack always crits.\n" +
                "Hybrid: Leather+Chain (Skirmisher): +3 dmg if you moved.\n\n" +
                "Mob head: Piglin Head = +1 Slashing affinity.\n" +
                "Open from max move range, eat the free crit, then Skirmisher keeps stacking as long as you reposition."),
            new Page("Sweeper",
                "§cSweeper. Clear adds with every swing.\n\n" +
                "Weapon: any Sword + Sweeping Edge. Lv1 hits a 3-wide chop, Lv2 a 5-tile arc, Lv3 a full 360° spin (90% damage + knockback).\n\n" +
                "Armor: Iron set (Guard) for survivability while standing in a pile of enemies, or Hybrid Chain+Iron (Sentinel) for ripostes on deflected hits.\n\n" +
                "Great vs swarms like Slimes, Silverfish, and Vexes.")
        )));
        progression.add(new Entry("§6Cleaving Loadouts", "minecraft:diamond_axe", List.of(
            new Page("Armor Crusher",
                "§6Armor Crusher. Punish heavy mobs.\n\n" +
                "Weapon: Iron, Diamond, Netherite - or GOLDEN - Axe (2 AP).\n" +
                "Chance per hit to ignore enemy armor entirely.\n\n" +
                "Armor: Iron set (Guard). +2 Cleaving, +2 AC.\n" +
                "Trim: Snout = +1 Cleaving per piece.\n\n" +
                "Best targets: bosses, brutes, anything with high DEF."),
            new Page("Wall Slammer",
                "§6Wall Slammer. Push enemies into walls for bonus dmg.\n\n" +
                "Weapon: Axe + Knockback (directional shockwave, collision damage).\n\n" +
                "Hybrid: Iron+Diamond (Warlord): every melee hit KB 1.\n" +
                "Hybrid: Iron+Netherite (Immovable): reflect damage + knockback immunity.\n\n" +
                "Pair with cactus or placed-block walls to slam enemies into terrain hazards.")
        )));
        progression.add(new Entry("§8Blunt Loadouts", "minecraft:mace", List.of(
            new Page("Mace AoE King",
                "§8Mace AoE King. Crush a whole crowd at once.\n\n" +
                "Weapon: Mace (2 AP) + Density (gravity well pulls enemies into the impact) + Wind Burst (shockwave knockback) + Breach (permanently shreds DEF per hit).\n\n" +
                "Armor: Diamond set (Knight). +2 Blunt, +1 ATK.\n" +
                "Trim: Dune = +1 Blunt per piece.\n" +
                "Mob head: Creeper Head = +1 Blunt affinity."),
            new Page("Stun Lock",
                "§8Stun Lock. Keep targets from acting.\n\n" +
                "Weapon: Stick or Bamboo (cheap stun chance), upgrade to Blaze Rod (fire + stun) or Breeze Rod (wind burst). All 1 AP.\n\n" +
                "Armor: any survival set. You want to outlast them.\n\n" +
                "Items: Bell (2-tile AoE stun), Cobweb (single-target stun), Powder Snow Bucket (freeze zone)."),
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
                "Weapon: Trident + Riptide II/III. Dash hits + KB everything in the line (1 + level tiles of KB).\n\n" +
                "Armor: Turtle Helmet (walk on water) + any survival set.\n" +
                "Trim: Coast = +1 Water per piece.\n\n" +
                "Best on tight arenas. Turn a row of mobs into pinballs."),
            new Page("Lightning Caller",
                "§3Lightning Caller. Channeling + Soaked combo.\n\n" +
                "Weapon: Trident + Channeling + Loyalty.\n" +
                "Channeling deals 3/6/10 by level on throw, DOUBLES vs Soaked targets, and chains (Lv1: 1, Lv2: 3, Lv3: 5 extra targets at half damage - full vs Soaked).\n\n" +
                "Setup: Soak first with corals, pufferfish, nautilus shell, or just throw the trident into a water tile.\n\n" +
                "Tip: Soaked also gives -1 enemy Speed, so they can't kite."),
            new Page("Coral Statuses",
                "§3Coral Statuses. One debuff per coral, swap to taste.\n\n" +
                "Each live coral applies a different effect on hit:\n" +
                "Tube = Soaked, Brain = Confuse (40%), Bubble = KB, Fire = Searing (+3 dmg to burning), Horn = Pierce 3 armor.\n\n" +
                "Keep 2-3 corals on the hotbar and swap to match the fight. All 1 AP.\n\n" +
                "Best vs single tough enemies. Stack debuffs, then finish."),
            new Page("Water-Burst Throwables",
                "§3Water-Burst Throwables. AoE crowd control.\n\n" +
                "All four AoE throwables count as Water:\n" +
                "Turtle Egg (T1, 2 dmg, Soak I), Pufferfish (T2, 3 + Soak II + Poison I), Nautilus Shell (T3, 4 + Soak III + Confuse I), Heart of the Sea (T4, 5 + Soak IV + Confuse II).\n\n" +
                "Combo: open with a Heart of the Sea throw, then Channeling Trident for 2x lightning across the soaked mob.")
        )));
        progression.add(new Entry("§dSpecial Loadouts", "minecraft:golden_hoe", List.of(
            new Page("Hoe Specialist",
                "§dHoe Specialist. Low base damage, huge with affinity.\n\n" +
                "Weapon: Diamond or Netherite Hoe (1 AP).\n" +
                "Hoes have tiny base damage but every Special affinity point adds +3.\n\n" +
                "Armor: Gold set (Gambler). +2 Special, crit + emerald perks.\n" +
                "Trim: Rib = +1 Special per piece.\n" +
                "Mob head: Wither Skeleton Skull = +1 Special affinity.\n\n" +
                "Affinity perk: 3% chance per point to make ANY attack cost 0 AP."),
            new Page("Sherd Caster",
                "§dSherd Caster. Every sherd is a spell.\n\n" +
                "Stash 4-6 pottery sherds with complementary effects:\n" +
                "Heart (heal+regen), Skull (execute), Prize (3x next hit), Arms Up (war cry), Blade (phantom slash).\n\n" +
                "Armor: Gold (Gambler) for crit synergy + emerald gain, or Hybrid Gold+Diamond (Gladiator) for +50% crit damage.\n\n" +
                "Resourceful stat keeps your sherd budget topped up."),
            new Page("Wind Mage",
                "§dWind Mage. Knock everything around.\n\n" +
                "Weapon: Wind Charges (knock enemies back up to 3 tiles OR self-launch 2 tiles; momentum bonus = +50% on your next hit).\n\n" +
                "Combine with: any Special weapon for follow-up burst, wall placements to trap targets after KB, or Pottery Sherd Snort (Tectonic Charge) for chained shoves.\n\n" +
                "Armor: Gold + Rib trim for damage scaling."),
            new Page("Crit Gambler",
                "§dCrit Gambler. Stack crit chance, swing big.\n\n" +
                "Hybrid: Gold+Diamond (Gladiator): crits +50% extra dmg.\n" +
                "Hybrid: Gold+Netherite (Berserker): crit rises as HP drops.\n" +
                "Hybrid: Chain+Gold (Cutpurse): crits restore 2 HP.\n\n" +
                "Weapon: Diamond Sword (30% native crit) or Diamond Hoe (for the Special-affinity ramp).\n\n" +
                "Luck stat (+5% crit/point) + Spire trim, boosting every Craftics proc including crits.")
        )));
        progression.add(new Entry("§aPet Loadouts", "minecraft:bone", List.of(
            new Page("Beastmaster",
                "§aBeastmaster. Let allies do the killing.\n\n" +
                "Weapon: Diamond or Netherite Shovel. Pet-type, scales with your Pet affinity.\n\n" +
                "Trim: Raiser = +1 ally damage per piece.\n" +
                "Affinity perk: +3 ally HP and +1 party size per point.\n\n" +
                "Add mobs to your battle party at the hub (Shift+RClick to toggle), or use Spawn Eggs (2 AP) for one-battle summons. A Wolf, Iron Golem, and Llama covers melee, tank, and ranged."),
            new Page("Lead Commander",
                "§aLead Commander. Micromanage ally turns.\n\n" +
                "Item: a Lead in main hand. Click an ally to select, then click an adjacent enemy (attack) or any tile (move). Costs 2 AP. Does NOT consume the ally's own turn.\n\n" +
                "Armor: any survival set. You stay alive while pets fight.\n" +
                "Raiser trim still buffs every ally hit, including the free lead-commanded ones."),
            new Page("Mount Build",
                "§aMount Build. Ride a party mob for bonus Speed.\n\n" +
                "Setup: equip a Saddle on a rideable party mob in the hub. You auto-mount at combat start (one rideable mob per party).\n\n" +
                "Mounting grants +3 Speed and the mount's HP pool as a shield.\n\n" +
                "Pair with: Riptide, Skirmisher (+3 if you moved), or hit-and-run Mace plays. Horse Armor (equipped before taming) adds DEF.")
        )));
        progression.add(new Entry("§bRanged Loadouts", "minecraft:bow", List.of(
            new Page("Bow Sniper",
                "§bBow Sniper. Long-range single-target.\n\n" +
                "Weapon: Bow (1 AP) + Power. Power scales hard: 1/3/5/8/11 bonus damage AND up to +3 range at high levels.\n" +
                "Add Flame for burn on hit, or Infinity to skip arrow drain. Tipped arrows still consume even with Infinity.\n\n" +
                "Armor: any survival set with Sentry trim (+1 Ranged per piece).\n" +
                "Mob head: Skeleton Skull = +1 Ranged affinity."),
            new Page("Crossbow Pierce",
                "§bCrossbow Pierce. Snipe down whole ranks.\n\n" +
                "Weapon: Crossbow (4 AP) - fires full rook lines across the entire arena. Piercing punches through multiple targets (50% behind the first) and inflicts Bleed. Quick Charge cuts the AP cost.\n\n" +
                "Best vs single-file enemy lines or stacked mobs.\n\n" +
                "Trim: Sentry per piece. Mob head: Skeleton Skull."),
            new Page("Multishot Spread",
                "§bMultishot Spread. Diagonal fan attack.\n\n" +
                "Weapon: Crossbow + Multishot (fires 2 extra bolts at 45° diagonals) + Quick Charge.\n\n" +
                "Best vs clusters where 3 spread lines all land.\n" +
                "Tipped arrows apply on every bolt. Load Slowness or Weakness arrows for crowd lockdown.\n\n" +
                "Armor: Hybrid Chain+Netherite (Ambush) for a guaranteed crit on the opening volley."),
            new Page("Rocket Crossbow",
                "§bRocket Crossbow. AoE long-range burst.\n\n" +
                "Weapon: Crossbow with a Firework Rocket in OFF-HAND.\n" +
                "Rockets bypass the usual arrow check, so you don't need an arrow stack, just the rocket. Massive AoE damage at full crossbow range.\n\n" +
                "Pair with Quick Charge so each rocket fires fast.\n" +
                "Resourceful + trader trades keep rockets stocked."),
            new Page("Ricochet Chain",
                "§bRicochet Chain. Bounce shots between mobs.\n\n" +
                "Affinity perk: 5% chain-ricochet chance per Ranged point. Stack Ranged affinity to 10+ and you'll see chains every shot.\n\n" +
                "Weapon: any Bow or Crossbow. Punch also pushes targets behind the impact and adds collision damage.\n\n" +
                "Best on packed arenas where ricochets re-target."),
            new Page("Copper Marksman §7(addon)",
                "§bCopper Marksman §7(needs Copper Age Backport mod).\n\n" +
                "Full Copper armor set (Marksman): +4 Ranged Power and a ricochet that stacks on top of the affinity perk.\n\n" +
                "Six Copper hybrids:\n" +
                "Copper+Leather (Run and Gun): moved this turn = +1 range.\n" +
                "Copper+Chain (Deadeye): +3 vs enemies that haven't acted.\n" +
                "Copper+Iron (Aegis): 30% deflect vs incoming ranged.\n" +
                "Copper+Gold (Contagion): spread debuffs to adjacent.\n" +
                "Copper+Diamond (Siege): splash half dmg to adjacent.\n" +
                "Copper+Netherite (Stormbringer): arc to extra enemy.")
        )));
        progression.add(new Entry("§7Physical Loadouts", "minecraft:leather_chestplate", List.of(
            new Page("Brawler Streaker",
                "§7Brawler Streaker. Keep killing, keep buffing.\n\n" +
                "Weapon: empty main hand (fists are Physical).\n\n" +
                "Armor: Leather set (Brawler). +2 Physical AND a kill streak (+30% dmg per kill, max 3 stacks - resets on a turn without a kill).\n\n" +
                "Mob head: Zombie Head = +1 Physical affinity.\n" +
                "Affinity perk: 3% counter chance per point.\n" +
                "Best vs swarms. Chained kills keep your buff alive."),
            new Page("Counterpuncher",
                "§7Counterpuncher. Let them hit you, hit harder back.\n\n" +
                "Hybrid: Leather+Iron (Counterpuncher): 50% chance to instantly attack back when hit.\n\n" +
                "Vitality + Defense stats. The more attacks you eat the more free retaliations you get.\n\n" +
                "Pair with: Shield in offhand (+1 AC, 25% block) and Thorns armor for reflection on top."),
            new Page("Lucky Streak",
                "§7Lucky Streak. Crit ramp until you die.\n\n" +
                "Hybrid: Leather+Gold (Lucky Streak): +10% crit per kill, resets when hit.\n\n" +
                "Weapon: Diamond Sword (native 30% crit) or any sword with Sharpness for bleed chains between kills.\n\n" +
                "Stay mobile (Speed stat) so you never take a hit and the streak never resets."),
            new Page("Breaker / Rampage",
                "§7Breaker or Rampage. Ignore resistance OR refund AP.\n\n" +
                "Hybrid: Leather+Diamond (Breaker): your attacks ignore all damage-type resistances. Great vs resist-heavy bosses.\n\n" +
                "Hybrid: Leather+Netherite (Rampage): every kill refunds 1 AP. Multi-kill turns become possible.\n\n" +
                "Both pair well with Power or Glass Cannon stats. You want to be the one swinging.")
        )));
        CATEGORIES.add(new Category("Leveling & Stats", "minecraft:experience_bottle",
            "Grow stronger through combat.", progression));

        // === EQUIPMENT & ENCHANTMENTS ===
        List<Entry> equipment = new ArrayList<>();
        equipment.add(new Entry("Armor Sets", "minecraft:diamond_chestplate", List.of(
            new Page("Set Bonuses",
                "Wear a full matching armor set for combat bonuses:\n\n" +
                "Leather (Brawler): +2 Physical, kill-streak damage ramp\n" +
                "Chainmail (Rogue): +1 Speed, attacks cost 1 less AP (min 1), +2 Slashing\n" +
                "Iron (Guard): +2 AC, +2 Cleaving\n" +
                "Gold (Gambler): +15% crit chance, +1 emerald per kill, +2 Special"),
            new Page("Advanced Sets",
                "Diamond (Knight): +3 AC, +1 ATK, +2 Blunt\n" +
                "Netherite (Juggernaut): +4 AC, +2 ATK, +2 to ALL damage types\n" +
                "Turtle Helmet: walk on water tiles, +3 Water\n\n" +
                "Even MIXED armor pulls its weight: every piece grants half an affinity point of its material's damage type. Full sets and exact 2/2 hybrid splits add the perks above."),
            new Page("Hybrid Sets",
                "Wear exactly 2+2 pieces of two materials to unlock a hybrid bonus:\n\n" +
                "Leather+Chain (Skirmisher): +3 dmg if you moved this turn\n" +
                "Leather+Iron (Counterpuncher): 50% counter when hit\n" +
                "Leather+Gold (Lucky Streak): +10% crit per kill, resets when hit\n" +
                "Leather+Diamond (Breaker): ignore damage-type resistances\n" +
                "Leather+Netherite (Rampage): kills refund 1 AP\n" +
                "Chain+Iron (Sentinel): riposte when an enemy misses you\n" +
                "Chain+Gold (Cutpurse): crits restore 2 HP\n" +
                "Chain+Diamond (Duelist): +4 dmg vs isolated enemies"),
            new Page("Hybrid Sets (2)",
                "Chain+Netherite (Ambush): your first attack each fight always crits\n" +
                "Iron+Gold (Gilded Guard): 15% chance to fully negate a hit\n" +
                "Iron+Diamond (Warlord): every melee hit knocks back 1\n" +
                "Iron+Netherite (Immovable): reflect 2 dmg, knockback immunity\n" +
                "Gold+Diamond (Gladiator): crits deal +50% damage\n" +
                "Gold+Netherite (Berserker): crit chance rises as HP drops\n" +
                "Diamond+Netherite (Stonewall): incoming damage capped at 6\n\n" +
                "Copper Age Backport adds six more - see Ranged Loadouts.")
        )));
        // Immersive Armors - only worth a page when the mod is actually installed.
        if (com.crackedgames.craftics.compat.immersivearmors.ImmersiveArmorsCompat.isLoaded()) {
            equipment.add(new Entry("Immersive Armors", "immersive_armors:warrior_chestplate", List.of(
                new Page("Light Sets",
                    "Ten more full sets, each with its own trick. Every piece still grants half an affinity point; the trick needs all four.\n\n" +
                    "Wooden (Slashing, 2 AC): 30% less Ranged and Blunt damage, but each piece can shatter when you're hit\n\n" +
                    "Bone (Blunt, 2 AC): 25% chance to fire without spending an arrow; pieces can shatter\n\n" +
                    "Robe (Special, 2 AC): pottery sherds never break and cost 1 less AP"),
                new Page("Middle Sets",
                    "Wither (Special, 4 AC): 50% chance to fire without spending an arrow; melee attackers wither\n\n" +
                    "Slime (Special, 4 AC): attackers bounce a tile away and so do you; your hits knock back too\n\n" +
                    "Steampunk (Physical, 4 AC): +1 Speed, and its radar paints each enemy's next route in yellow, the tiles they'll strike in red\n\n" +
                    "Warrior (Cleaving, 5 AC): +1 Cleaving damage for every heart you are missing"),
                new Page("Heavy Sets",
                    "Divine (Special, 6 AC): the first hit you take each battle is deflected entirely\n\n" +
                    "Prismarine (Water, 6 AC): at the start of your turn, every enemy within 2 tiles takes 3 Water damage and is Soaked\n\n" +
                    "Heavy (Blunt, 7 AC): netherite-grade plate. Immune to knockback, but -1 Speed.")
            )));
        }

        // Simply Bows - only worth a page when the mod is actually installed.
        if (com.crackedgames.craftics.compat.simplybows.SimplyBowsCompat.isLoaded()) {
            equipment.add(new Entry("Simply Bows", "simplybows:echo_bow/echo_bow", List.of(
                new Page("Legendary Bows",
                    "Seven bows drop from bosses, each with one trick. They eat arrows and take bow enchants like any bow.\n\n" +
                    "Winterfang (Water, 1 AP): every hit fans out 3 homing frost bolts that chill what they find\n\n" +
                    "Buzzkill (Ranged + Pet, 1 AP): poisons on hit, and 35% of the time bursts into 1-2 bee allies\n\n" +
                    "Tremorstrike (Blunt, 1 AP): stuns the target and everything within 2 tiles"),
                new Page("Ground Bows",
                    "Three bows leave something behind. Shoot an enemy and the effect centers on IT; aim at open ground and it lands there instead. Either way it costs 2 AP and an arrow.\n\n" +
                    "Everbloom (Ranged): grows a 3x3 flower field for 4 turns. Enemies in it are sapped and bewildered; you and your allies are mended.\n\n" +
                    "Bubbleveil (Water + Ranged): Soaks what it hits and leaves a column under it. A column pops on the first enemy to WALK into it, so knock a soaked target off its tile and back again to set it off.\n\n" +
                    "Petalwind (Ranged): rains arrows on a quarter of the arena's tiles. A struck enemy is always under one. Bigger arena, bigger storm."),
                new Page("Echo & Hybrids",
                    "Echo (Special, 1 AP): two phantom bows loose at the nearest other enemies for half your damage. It never shoots alone.\n\n" +
                    "Bubbleveil and Buzzkill are HYBRID weapons: they scale off two affinities at once. The second affinity counts at half weight, so a hybrid is never strictly better than a bow that committed to one type - it just fits two builds.\n\n" +
                    "Traps you lay are marked by their own particles, and fade on their own.")
            )));
        }

        equipment.add(new Entry("Shield", "minecraft:shield", List.of(
            new Page("Shield Mechanics",
                "Equip a shield in your OFFHAND slot.\n\n" +
                "Passive: +1 Armor Class at all times.\n\n" +
                "Block: 25% chance to completely block an incoming attack, negating all damage.\n\n" +
                "No AP cost - purely passive! Equip and fight normally.")
        )));
        equipment.add(new Entry("Mob Heads", "minecraft:creeper_head", List.of(
            new Page("Head Slot Power",
                "Wear a mob head instead of a helmet for +1 affinity point (= +3 damage) of its type:\n\n" +
                "Zombie Head: +1 Physical\n" +
                "Skeleton Skull: +1 Ranged\n" +
                "Creeper Head: +1 Blunt\n" +
                "Piglin Head: +1 Slashing\n" +
                "Wither Skeleton Skull: +1 Special\n\n" +
                "You give up the helmet's AC and set slot - pure offense, no defense.")
        )));
        equipment.add(new Entry("Enchantments", "minecraft:enchanted_book", List.of(
            new Page("Sword Enchantments",
                "Sharpness: +1 dmg/lvl + a Bleed stack per hit. Bleeding enemies take damage equal to their stacks every turn.\n\n" +
                "Smite: radiant AoE burst vs undead - 2x level bonus damage in a radius that grows with level.\n\n" +
                "Bane of Arthropods: injects venom into arthropods. Poison + Slowness.\n\n" +
                "Fire Aspect: cone of fire in your swing direction. Burns all enemies in the cone (wider with Sweeping Edge)."),
            new Page("Sword Enchantments (2)",
                "Knockback: directional shockwave. Pushes the target + enemies behind them (level+1 tiles). Wall collision = bonus damage.\n\n" +
                "Sweeping Edge: Lv1 hits a 3-wide chop, Lv2 a 5-tile arc, Lv3 a full 360° spin - at 60/75/90% damage. Lv3 adds knockback."),
            new Page("Bow & Crossbow Enchantments",
                "Power: big scaling - 1/3/5/8/11 bonus damage and up to +3 range.\n\n" +
                "Flame: burns target and all adjacent enemies.\n\n" +
                "Infinity: never consume arrows (only need 1). Tipped arrows are still consumed.\n\n" +
                "Punch: radial knockback burst on impact + collision damage.\n\n" +
                "Quick Charge: -1 crossbow AP per level (min 1).\n" +
                "Multishot: 3 bolts in a 45° fan.\n" +
                "Piercing: bolts pierce 1+level targets (50% behind the first) + inflict Bleed."),
            new Page("Mace Enchantments",
                "Density: gravity well. Pulls nearby enemies to the impact point + crushing bonus damage.\n\n" +
                "Breach: permanently reduces target defense per hit. Stacks all combat.\n\n" +
                "Wind Burst: shockwave knockback on all adjacent + buffs your next Mace hit (+2/+3/+4)."),
            new Page("Trident Enchantments",
                "Impaling: 1/2/5/8/10 bonus damage + Bleed stacks (1/1/2/2/3).\n\n" +
                "Channeling: lightning on throw hit - 3/6/10 damage, DOUBLED vs Soaked, chains to 1/3/5 extra targets at half damage (full vs Soaked).\n\n" +
                "Loyalty: trident ricochets to 1 nearby enemy per level (50% damage) before returning.\n\n" +
                "Riptide: dash through enemies instead of throwing - damage + knockback scale with level."),
            new Page("Armor Enchantments",
                "Protection (incl. Blast/Projectile Prot.): +1 Armor Class per 2 total levels worn.\n\n" +
                "Thorns: 15% chance per level (boosted by Luck) to reflect damage back at attackers.\n\n" +
                "Other vanilla armor enchants have no special combat effect - pick AC and set bonuses first."),
            new Page("Focus Tools",
                "Shovels and hoes are focuses, not weapons. They swing badly on purpose. Their value is the enchantment they carry.\n\n" +
                "A focus works from ANYWHERE in your inventory. You never have to hold it.\n\n" +
                "Carrying two of the same enchant does nothing - only the highest level counts. A second focus is only worth it for a DIFFERENT enchant.\n\n" +
                "Find them in enchanting tables, enchanted books, the Wandering Enchanter, trial loot and traders."),
            new Page("Shovel Enchantments (Pet)",
                "A shovel arms your pets. It does nothing for you directly.\n\n" +
                "Honed (max V): your pets deal +1 damage per level.\n\n" +
                "Fire Fang (max III): pets set what they hit alight for 2/3/4 turns.\n\n" +
                "Water Fang (max III): pets apply Soaked for 2/3/4 turns.\n\n" +
                "Thunder Fang (max III): pets shock every OTHER enemy within 1/2/3 tiles of the target for 3 lightning damage."),
            new Page("Shovel Enchantments (2)",
                "The three Fangs are exclusive - one shovel takes one element.\n\n" +
                "But you can carry more than one shovel. Water Fang on one and Thunder Fang on another is the combo: the Soak lands first, and lightning does DOUBLE damage to a Soaked target.\n\n" +
                "Seeker vexes are a spell's payload, not pets. They carry no Fangs and gain no Honed."),
            new Page("Hoe Enchantments (Special)",
                "A hoe rides on your Special-item casts: potions, banners, horns, charges, pearls and pottery sherds.\n\n" +
                "Reserving (max III): +5% per level that a Special item costs no AP. Stacks with Special affinity's own chance.\n\n" +
                "Performative (max III): 5% per level to cast the item TWICE. The encore is free - no extra item, no extra AP.\n\n" +
                "Radiant (max V): +2 damage per level when a Special item hits an undead.\n\n" +
                "Medic (max III): +2 HP per level to any healing a Special item does - to you, a teammate you feed, or a pet.")
        )));
        equipment.add(new Entry("Tipped Arrows", "minecraft:tipped_arrow", List.of(
            new Page("Arrow Effects",
                "Tipped arrows are consumed before regular arrows.\n\n" +
                "Poison: applies Poison (damage over time)\n" +
                "Slowness: -1 enemy speed for 2 turns\n" +
                "Weakness: stuns the enemy\n" +
                "Harming: +4 bonus damage\n" +
                "Healing: restores 4 HP to you\n" +
                "Wither: applies ramping Wither decay\n" +
                "Fire Res: 3 turns of protection\n\n" +
                "Infinity does NOT save tipped arrows!")
        )));
        CATEGORIES.add(new Category("Equipment", "minecraft:diamond_chestplate",
            "Armor sets, enchantments, and gear.", equipment));

        // === ARMOR TRIMS ===
        List<Entry> trims = new ArrayList<>();
        trims.add(new Entry("How Trims Work", "minecraft:coast_armor_trim_smithing_template", List.of(
            new Page("Trim Combat Effects",
                "Armor trims grant COMBAT BONUSES in Craftics!\n\n" +
                "Each trimmed armor piece gives a stackable per-piece bonus. Wearing 4 pieces with the SAME trim activates a powerful Full Set Bonus.\n\n" +
                "Trim templates drop from biome bosses (~35% chance, improved by Luck).\n" +
                "Apply trims at a Smithing Table in the hub."),
            new Page("Overworld Trims",
                "Sentry: +1 Ranged Power /piece\n  Full Set: Overwatch (counter-attack ranged)\n\n" +
                "Dune: +1 Blunt Power /piece\n  Full Set: Sandstorm (-1 Speed aura)\n\n" +
                "Coast: +1 Water Power /piece\n  Full Set: Tidal (water heals)\n\n" +
                "Wild: +1 AP /piece\n  Full Set: Feral (1.3x kill streak dmg)"),
            new Page("Overworld Trims (cont.)",
                "Wayfinder: +1 Speed /piece\n  Full Set: Pathfinder (ignore obstacles)\n\n" +
                "Shaper: +1 Armor Class /piece\n  Full Set: Earthshatter (move 3+ tiles to deal 2 dmg around your destination)\n\n" +
                "Raiser: +1 ally damage /piece\n  Full Set: Rally (allies +2 Spd, +1 Atk)\n\n" +
                "Host: +4 max HP /piece\n  Full Set: Symbiote (heal 1 HP/kill)\n\n" +
                "Tide: +1 HP regen per 2 turns /piece\n  Full Set: Ocean's Blessing (emergency heal)"),
            new Page("Nether Trims",
                "Ward: +1 Armor Class /piece\n  Full Set: Fortress (50% less dmg when stationary)\n\n" +
                "Snout: +1 Cleaving Power /piece\n  Full Set: Brute Force (splash damage)\n\n" +
                "Rib: +1 Special Power /piece\n  Full Set: Infernal (fire +3 bonus dmg)\n\n" +
                "Eye: +1 Attack Range /piece\n  Full Set: Eagle Eye (ranged attacks +30% crit)"),
            new Page("End & Trial Trims",
                "Spire: +1 Luck /piece (+3% crit each)\n  Full Set: Fortune's Peak (double emeralds)\n\n" +
                "Vex: Ignore 1 enemy DEF /piece\n  Full Set: Ethereal (20% deflect)\n\n" +
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
                "Quartz: +4 Max HP /piece\n" +
                "Resin: +1 Ally Damage /piece\n\n" +
                "Pattern + Material stack! Mix for your build.")
        )));
        CATEGORIES.add(new Category("Armor Trims", "minecraft:coast_armor_trim_smithing_template",
            "Trim combat bonuses and full set effects.", trims));

        // === PETS & ALLIES ===
        List<Entry> pets = new ArrayList<>();
        pets.add(new Entry("Battle Party", "minecraft:bone", List.of(
            new Page("Adding Mobs",
                "Your battle party is built at the hub.\n\n" +
                "Shift+Right-Click any rideable or tameable mob in the hub to toggle it in your party. Party size = Pet Affinity + 1 (more Pet points = bigger party). Only ONE rideable mob per party.\n\n" +
                "At combat start, every party mob spawns into the arena as your ally. After the fight, survivors return to the hub automatically."),
            new Page("Mid-Combat Taming",
                "You can also tame a wild mob during a fight: stand ADJACENT with line of sight and use its taming food (see Taming Foods).\n\n" +
                "The mob joins your side for the rest of that battle - yes, even cows, sheep, pigs and chickens will fight for you.\n\n" +
                "Win the fight and tamed survivors can join your hub roster."),
            new Page("Taming Foods",
                "Wolf: Bone or Beef (raw/cooked)\n" +
                "Cat / Ocelot: Cod or Salmon (raw/cooked)\n" +
                "Chicken: any seeds (Wheat, Melon, Pumpkin, Beetroot)\n" +
                "Parrot: Wheat or Melon Seeds\n" +
                "Cow / Sheep / Goat / Mooshroom: Wheat\n" +
                "Pig: Carrot, Potato or Beetroot\n" +
                "Rabbit: Carrot, Golden Carrot or Dandelion\n" +
                "Horse / Donkey: Golden Apple/Carrot\n" +
                "Llama: Hay Block\n" +
                "Fox: Sweet or Glow Berries\n" +
                "Bee: Dandelion, Poppy, Blue Orchid\n" +
                "Turtle: Seagrass\n" +
                "Axolotl: Tropical Fish Bucket\n" +
                "Frog: Slime Ball\n" +
                "Camel: Cactus\n" +
                "Sniffer: Torchflower Seeds"),
            new Page("Ally AI",
                "Tamed allies act on the enemy phase:\n" +
                "- Pick a target based on archetype (melee charges nearest, ranged kites, support stays near you, flyers chase weak).\n" +
                "- Retreat below 25% HP unless their archetype says fight.\n" +
                "- Path around walls and tile hazards.\n\n" +
                "Heal allies with a Hay Block (4 HP or half their max, whichever is more) or Pet affinity (+3 HP per point at spawn). Lead command (2 AP) gives an ally a bonus attack/move without using their turn.")
        )));
        pets.add(new Entry("Mounts", "minecraft:saddle", List.of(
            new Page("How Mounting Works",
                "Mount setup happens at the hub, not in combat.\n\n" +
                "Step 1: get a rideable mob - tame a Horse/Donkey/Camel with its food, or recruit Mules, Skeleton Horses and Zombie Horses directly with Shift+Right-Click (they have no taming food).\n" +
                "Step 2: equip a Saddle on it (right-click while holding the saddle).\n" +
                "Step 3: add it to your battle party.\n\n" +
                "When combat starts, you auto-mount that party mob. Mounting grants +3 Speed and the mount's HP pool as a bonus shield."),
            new Page("Riding in Combat",
                "While mounted:\n" +
                "- Your move uses the mount's tile path (same speed bonus).\n" +
                "- Weapon attacks fire normally from the saddle.\n" +
                "- The mount can't take its own ally turn while you ride it (you're the driver).\n" +
                "- If the mount dies, you dismount and lose the bonus.\n\n" +
                "Pair with Riptide Trident or Skirmisher hybrid for free bonus damage from the constant movement.")
        )));
        pets.add(new Entry("Pet Armor", "minecraft:diamond_horse_armor", List.of(
            new Page("Pet Equipment",
                "Equip armor on a pet BEFORE taming it (the bonus is snapshotted at the moment it joins your party).\n\n" +
                "Horse Armor bonuses:\n" +
                "Leather: +1 DEF\n" +
                "Iron: +2 DEF\n" +
                "Gold: +1 DEF, +1 ATK\n" +
                "Diamond: +3 DEF, +1 ATK\n\n" +
                "Wolf Armor: +2 DEF, +1 ATK\n\n" +
                "Swapping armor after taming does not change the bonus, so plan the loadout before recruiting.")
        )));
        CATEGORIES.add(new Category("Pets & Allies", "minecraft:bone",
            "Build a battle party of tamed mobs.", pets));

        // === ITEMS & ABILITIES ===
        List<Entry> items = new ArrayList<>();
        items.add(new Entry("Combat Items", "minecraft:anvil", List.of(
            new Page("Offensive Items",
                "Anvil (1 AP): drops on the enemy - 1/2 its max HP (pristine), 1/3 (chipped), 1/4 (damaged), then breaks. Wears one stage per use unless Special affinity saves it (10% per point)\n" +
                "TNT (1 AP): detonates next round - 8/5/3 dmg by distance, radius 2. Hurts everyone, including you\n" +
                "Bell (2 AP): stun all enemies within 2 tiles\n" +
                "Crossbow item-throw (2 AP): 3 DMG, 4-tile range\n" +
                "Trident: melee (1 AP) or throw (2 AP)\n" +
                "Fire Charge (1 AP): 4 fire DMG\n" +
                "Spore Blossom (1 AP): radius-3 AoE, -1 Speed to enemies"),
            new Page("Defensive & Utility",
                "Totem (passive): auto-revive at half HP - or EAT it for a full heal\n" +
                "Spyglass (2 AP): mark an enemy - it takes 2x damage (1.5x bosses) this turn and next, glows, and reveals its stats\n" +
                "Compass (1 AP): reveal all positions\n" +
                "Brush (1 AP): dig random loot from an adjacent tile\n" +
                "Ender Pearl (1 AP): teleport - costs 2 HP on landing\n" +
                "Wind Charge (1 AP): shove an enemy up to 3 tiles, or self-launch 2 tiles (+50% momentum bonus on your next hit)"),
            new Page("Goat Horns",
                "Each horn variant is a reusable battle-cry (Special-affinity scaled):\n\n" +
                "Ponder (1 AP): +2 DEF\n" +
                "Sing (1 AP): +2 regen\n" +
                "Feel (1 AP): +2 Speed\n" +
                "Seek (2 AP): +3 ATK\n" +
                "Admire (2 AP): -2 ATK on ALL enemies\n" +
                "Call (2 AP): -1 Speed on ALL enemies\n" +
                "Dream (2 AP): fire resistance\n" +
                "Yearn (3 AP): poison ALL enemies\n\n" +
                "Horns don't break - one horn, every fight."),
            new Page("Potions & Sherds",
                "Drinkable potions work in combat - durations convert to turns, and Special affinity strengthens them.\n\n" +
                "Splash & Lingering potions can be thrown for AoE effects; lingering leaves a cloud on the tiles.\n\n" +
                "Pottery Sherds cast one-shot spells: Heart (heal+regen), Skull (execute), Prize (3x next hit), Arms Up (war cry), Blade (phantom slash), Snort (tectonic shove) and more.")
        )));
        items.add(new Entry("Tile Effects", "minecraft:campfire", List.of(
            new Page("Placed Items",
                "Some items create lasting tile effects:\n\n" +
                "Lava Bucket: 3 fire DMG/turn to enemies (keeps the empty bucket)\n" +
                "Campfire: 2 HP/turn to allies in the surrounding 5x5 + light radius 3\n" +
                "Honey Block: enemies lose all movement on it\n" +
                "Slime Block: bouncy wall - knocks attackers back\n" +
                "Powder Snow Bucket: freeze zone\n" +
                "Lightning Rod: 4 AoE DMG next turn (2x vs Soaked)\n" +
                "Cactus: 1 DMG/turn to adjacent enemies + blocks movement"),
            new Page("Placed Items (2)",
                "Banner: +2 DEF (Special-scaled) to allies within 2 tiles. Multiple banners don't stack - strongest wins\n" +
                "Cake: heals 2 HP per bite, 3 bites, shareable\n" +
                "Jukebox (2 AP): +3 Speed to all allies\n" +
                "Hay Bale: heal an adjacent ally\n" +
                "Any full block: temporary wall for 4 turns"),
            new Page("Light Sources (vs Darkness)",
                "Place to negate darkness effects:\n\n" +
                "Torch: light radius 2 (small, fast)\n" +
                "Lantern: light radius 3 + enemy detection\n" +
                "Campfire: light radius 3 + healing\n\n" +
                "Essential against The Hollow King - in darkness his minions hit harder, and his Phase 2 darkness is permanent.")
        )));
        items.add(new Entry("Terrain Tools", "minecraft:iron_pickaxe", List.of(
            new Page("Modify the Arena",
                "Water Bucket: place a water tile on clean floor (fishable, keeps the bucket)\n" +
                "Sponge: absorb adjacent water - but the sponge block itself blocks the tile\n" +
                "Pickaxe: break an ADJACENT obstacle, make it walkable (costs durability)\n" +
                "Scaffolding: +1 attack range while standing on it\n" +
                "Fishing Rod (3 AP): random loot - stand adjacent to water\n\n" +
                "Boats: cross water tiles (consumed on entry). Each co-op player needs their own.")
        )));
        CATEGORIES.add(new Category("Items & Abilities", "minecraft:anvil",
            "Every usable item explained.", items));

        // === RANDOM EVENTS ===
        List<Entry> events = new ArrayList<>();
        events.add(new Entry("How Events Work", "minecraft:filled_map", List.of(
            new Page("Between Levels",
                "After clearing a level, something may be waiting on the path to the next one.\n\n" +
                "- No events on a biome's first level or right before its boss\n" +
                "- Early biomes see fewer events\n" +
                "- A pity timer builds while nothing happens - droughts make events likelier\n\n" +
                "In co-op, many events are decided by PARTY VOTE. Disconnected players count as a pass.")
        )));
        events.add(new Entry("Wandering Trader", "minecraft:emerald", List.of(
            new Page("Trading Post",
                "The most common event (~25% chance).\n\n" +
                "A wandering trader appears in a biome-themed bazaar. Trader types: Weaponsmith, Armorer, Alchemist, and more.\n\n" +
                "Trades scale with biome tier. Spend emeralds for gear! Resourceful gives discounts.\n\n" +
                "In Nether regions the trader is replaced by Piglin Barter."),
            new Page("Meeting the Trader",
                "Your party walks up to the trader together, then they greet you with a few lines of dialogue (each trader type has its own greeting). Click to skip ahead.\n\n" +
                "Pick \"Let's trade\" to open the shop. When you close it, the trader asks if you're done - choose \"No\" to keep shopping. The run continues once everyone is finished.")
        )));
        events.add(new Entry("Piglin Barter", "minecraft:gold_ingot", List.of(
            new Page("Gold for Goods",
                "The Nether's answer to the trader.\n\n" +
                "Offer gold to a piglin merchant across 5 trade categories. Bigger gold offers improve your odds of the good stuff, and overpaying generously is remembered.\n\n" +
                "Bring gold ingots into the Nether - they're worthless in your pocket and priceless in a snout's hands.")
        )));
        events.add(new Entry("Trial Chamber", "minecraft:trial_key", List.of(
            new Page("Optional Challenge",
                "~10% chance between levels.\n\n" +
                "A compact tuff-brick arena stocked with trial mobs - Bogged, Stray, Husk, Cave Spiders, Silverfish - guarded by a BREEZE mini-boss.\n\n" +
                "OPTIONAL: the party votes to enter or pass.\n" +
                "Rewards: Trial Keys, Mace, Wind Charges, Breeze Rods!")
        )));
        events.add(new Entry("Something Shiny", "minecraft:raw_gold", List.of(
            new Page("Bait or Treasure?",
                "~10% chance: something glints on the ground ahead.\n\n" +
                "The party votes - Take it or Leave it.\n\n" +
                "Take: 50/50 between a rare reward... or an AMBUSH in a cramped arena.\n" +
                "Leave: you walk past. Nothing gained, nothing risked.\n\n" +
                "Tied votes spring the ambush. Keep healing items ready before grabbing."),
            new Page("If It's an Ambush",
                "2-3 enemies pulled from the CURRENT biome's pool - zombies in Plains, magma cubes in the Nether, and so on - scaled to the biome like a normal fight. They aren't faster or special, just sudden and close in a small arena.\n\n" +
                "Win for a small emerald bonus. AoE weapons and a stun bell clear the crowd fast.")
        )));
        events.add(new Entry("Shrine of Fortune", "minecraft:gold_block", List.of(
            new Page("Emerald Gamble",
                "~7% chance between levels.\n\n" +
                "A mysterious shrine accepts offerings of 2, 5, or 10 emeralds for a random reward. Bigger offerings roll better reward bands - but every tier has a bust chance (about 25% / 15% / 8%): pay and receive NOTHING.\n\n" +
                "Rewards range from consumables up to diamond gear and totems, scaling with biome tier.\n\n" +
                "Feeling lucky?")
        )));
        events.add(new Entry("Wounded Traveler", "minecraft:bread", List.of(
            new Page("Act of Kindness",
                "~6% chance between levels.\n\n" +
                "A wounded traveler begs for food. Give any food item to receive a random reward.\n\n" +
                "Rewards range from emeralds and arrows to diamonds, saddles, and even a Totem of Undying!")
        )));
        events.add(new Entry("Dig Site", "minecraft:brush", List.of(
            new Page("Push Your Luck",
                "~6% chance between levels. Each player digs their own site.\n\n" +
                "You start at a 5% pull chance. \"Keep brushing\" adds 15% to it, but every brush has a 10% chance to break the relic and end your dig empty-handed.\n\n" +
                "\"Attempt\" at any time rolls your current pull chance: success digs out a random pottery sherd, failure gets nothing. The brush option disappears once the pull hits 100%.\n\n" +
                "Stop early to play it safe, or keep brushing for better odds and risk the break. The sherd you pull feeds the Special \"Sherd Caster\" build.")
        )));
        events.add(new Entry("Wandering Enchanter", "minecraft:lapis_lazuli", List.of(
            new Page("Arcane Services",
                "~6% chance between levels.\n\n" +
                "A robed specialist offers two services for emeralds:\n" +
                "- Enchant your held weapon\n" +
                "- Enhance a worn armor piece\n\n" +
                "A cheaper gamble than the Shrine, and it upgrades gear you already love.")
        )));
        events.add(new Entry("Treasure Vault", "minecraft:vault", List.of(
            new Page("Hidden Riches",
                "~4% chance between levels.\n\n" +
                "A vault with NO enemies. Ring the lodestone to open it and claim 3-5 random items from the trial loot pool.\n\n" +
                "The vault floor is pure gold. Take your time - nothing in here bites.")
        )));
        events.add(new Entry("Ominous Trial", "minecraft:ominous_trial_key", List.of(
            new Page("Ultimate Challenge",
                "~5% chance, LATE GAME ONLY (biome 10+).\n\n" +
                "An ominous trial with a WARDEN, a Breeze, and elite trial mobs on deepslate.\n\n" +
                "OPTIONAL but EXTREMELY dangerous.\n" +
                "Rewards: hero gear - pieces stacked with 3-5 max-level enchantments - plus ominous keys and supplies.")
        )));
        CATEGORIES.add(new Category("Random Events", "minecraft:trial_key",
            "Events that appear between levels.", events));

        // === MULTIPLAYER ===
        List<Entry> coop = new ArrayList<>();
        coop.add(new Entry("Playing in a Party", "minecraft:player_head", List.of(
            new Page("Making a Party",
                "Parties are run with chat commands, all under §e/craftics party§r:\n\n" +
                "create - start a party (you become leader)\n" +
                "invite <player> - leader invites someone; they get a clickable [ACCEPT]/[DECLINE]\n" +
                "accept / decline - answer an invite\n" +
                "list - show members and who's online\n" +
                "leave - leave your party\n" +
                "kick <player> / disband - leader only\n" +
                "name <text> - rename the party\n\n" +
                "Up to 4 players (server-configurable). The leader starts and controls biome entry from the hub, and the whole party joins the run together."),
            new Page("Co-op Basics",
                "Craftics is fully co-op. The party shares the campaign and fights together on the same grid.\n\n" +
                "- Players take their turns in rotation before the enemy phase\n" +
                "- Enemies gain +25% HP per extra player\n" +
                "- Loot and emeralds are attributed per player\n" +
                "- Each player needs their own boat for water\n" +
                "- Events (trials, shiny finds) are decided by party vote"),
            new Page("Keeping Each Other Alive",
                "Feed an ADJACENT teammate any food - same healing as eating it yourself.\n\n" +
                "A teammate knocked below the arena or downed is rescued through the combat flow instead of dying to the void - pick them back up and keep fighting.\n\n" +
                "Stealth, fire resistance and buffs are tracked per player, and bush stealth now holds for everyone hiding, not just whoever's turn it is.")
        )));
        CATEGORIES.add(new Category("Multiplayer", "minecraft:player_head",
            "Co-op rules and party play.", coop));

        // === WORLD & PROGRESSION ===
        List<Entry> worldEntries = new ArrayList<>();
        worldEntries.add(new Entry("Your Hub", "minecraft:campfire", List.of(
            new Page("Home Base",
                "The hub is your personal island in the Craftics dimension - where you start runs and return between fights. In multiplayer every player gets their own island.\n\n" +
                "Right-click the Level Select block to open the biome/level picker and launch a run. After every fight, event, or defeat you return here - fully healed - for the next attempt.\n\n" +
                "In a party, the leader picks the biome and starts the run from their hub; the whole party is pulled in together."),
            new Page("What You Do Here",
                "Everything you can't do mid-combat happens at the hub:\n\n" +
                "- Battle party: Shift+Right-Click a tamed or rideable mob to toggle it in your party\n" +
                "- Mounts & pets: equip a Saddle on a rideable party mob, and fit pet armor before taming\n" +
                "- Armor trims: apply trim patterns and materials at a Smithing Table\n" +
                "- Roster: pets you tame mid-run are waiting here after the fight\n\n" +
                "Sort your loadout, party, and trims here before stepping into the next biome.")
        )));
        worldEntries.add(new Entry("The Campaign", "minecraft:filled_map", List.of(
            new Page("Biomes & Regions",
                "The campaign runs through Overworld, Nether and End regions - each biome is a string of levels capped by a boss.\n\n" +
                "Arenas grow bigger and nastier the deeper you go. Night levels, hazard tiles and themed enemy crews keep each biome distinct.\n\n" +
                "Mid-biome surprises exist too - the Forest hides a Pale Garden where Creakings stalk..."),
            new Page("New Game Plus",
                "Defeat The Ender Dragon at the Dragon's Nest to trigger NEW GAME PLUS.\n\n" +
                "The campaign restarts with everything scaled up (+25% per cycle, stacking). You keep your levels, gear and party.\n\n" +
                "How deep can you go?")
        )));
        worldEntries.add(new Entry("Achievements", "minecraft:gold_ingot", List.of(
            new Page("Bragging Rights",
                "50+ achievements track your run: boss kills, class mastery, combat feats and more.\n\n" +
                "Check your progress in the achievements screen - some are sneaky (win a fight without moving, anyone?).")
        )));
        CATEGORIES.add(new Category("World & Progression", "minecraft:filled_map",
            "The hub, the campaign, and what comes after.", worldEntries));

        // === ADDON MODS ===
        List<Entry> addons = new ArrayList<>();
        addons.add(new Entry("Copper Age Backport", "minecraft:copper_ingot", List.of(
            new Page("Copper Gear",
                "§6Copper Age Backport§r adds copper weapons + armor on versions before vanilla added them.\n\n" +
                "Copper Sword, Axe, Pickaxe, Hoe, and Shovel slot in between stone and iron. Damage type matches the equivalent tool.\n\n" +
                "Full Copper armor set: §6Marksman§r - +4 Ranged Power and ranged ricochet. Six Copper hybrid sets stack on top of the standard 15. See Ranged Loadouts for the full table.")
        )));
        addons.add(new Entry("Artifacts", "minecraft:amethyst_shard", List.of(
            new Page("Head & Necklace",
                "§5Artifacts§r - wearable trinkets via the Accessories slot system.\n\n" +
                "Head: Night Vision Goggles +1 Range, Snorkel +1 Water + Soaked immune, Cowboy Hat pulls hits 1 tile closer, Villager Hat +50% emeralds.\n\n" +
                "Necklace: Flame Pendant burns adjacent 2 dmg/turn, Thorn Pendant reflects 25%, Cross Necklace halves the next hit, Shock Pendant 30% chain 3 dmg on hit."),
            new Page("Hands & Belt",
                "Hands: Power Glove +1 Melee, Golden Hook pulls hits closer, Lucky Scarf +1 Luck.\n\n" +
                "Belt: Antidote Vessel cleanses poison, Cloud in a Bottle +1 Speed + jump, Obsidian Skull immune to fire damage, Pickaxe Heater pickaxes ignore obstacle armor."),
            new Page("Feet & Curio",
                "Feet: Running Shoes +1 Speed, Bunny Hoppers double KB resist, Steadfast Spikes pierce armor on melee, Kitty Slippers cancel first fall.\n\n" +
                "Curio: Lucky Star double crit dmg, Eternal Steak non-consuming food, Pocket Piston pushes 2 tiles, Mimic kicks in custom mimic boss encounters at campsites.")
        )));
        addons.add(new Entry("Golem Overhaul", "minecraft:carved_pumpkin", List.of(
            new Page("Golem Allies",
                "§7Golem Overhaul§r adds 9 golem types as recruitable allies - from scrappy Cobblestone golems to the mighty Netherite Golem.\n\n" +
                "The Netherite Golem is RIDEABLE: a 1x3 walking fortress with lava-line attacks. Press M while mounted for its special ability.\n\n" +
                "Coal Golem: summon mid-fight for 3 AP - a disposable bodyguard on demand.")
        )));
        addons.add(new Entry("More Totems", "minecraft:totem_of_undying", List.of(
            new Page("Totem Variants",
                "§eMoreTotems§r adds 7 totem variants, each with its own revive twist - element resistances, buffs on trigger, and more.\n\n" +
                "All work like the vanilla totem: carry to auto-revive, or eat for the heal.")
        )));
        addons.add(new Entry("Basic Weapons", "minecraft:iron_sword", List.of(
            new Page("The Six Weapons",
                "§7Basic Weapons§r adds six weapon types in every tier from wood to netherite (no bronze):\n\n" +
                "Dagger - Slashing, 1 AP, range 1. Dual-wield two daggers for a second hit at 75% damage, about 1.75x per turn for 1 AP.\n" +
                "Spear - Slashing, 1 AP, range 2. Lower base damage that grows +20% per tile you walk before attacking, up to 2x.\n" +
                "Quarterstaff - Blunt, 1 AP, range 2. Reach 2 plus a small sweep to adjacent foes.\n" +
                "Club - Blunt, 2 AP, range 1. Chance to Slow on hit.\n" +
                "Hammer - Blunt, 3 AP, range 1. Mace-style knockback, stun, and shockwave.\n" +
                "Glaive - Cleaving, 3 AP, range 1. Full hit plus a half-damage wide cleave arc."),
            new Page("Damage & Might",
                "Damage tracks the vanilla weapons per tier:\n" +
                "Dagger & Quarterstaff = Sword damage minus 1\n" +
                "Spear = Sword damage minus 2 (before the movement bonus)\n" +
                "Club = Axe damage\n" +
                "Hammer & Glaive = Axe damage plus 1\n\n" +
                "The §bMight§r enchantment goes on the Blunt weapons (Club, Hammer, Quarterstaff): +1 bonus damage and +5% stun chance per level.\n\n" +
                "Daggers suit Slashing builds, Spears and Quarterstaffs give cheap reach, and the heavy Blunt and Cleaving weapons hit hardest for more AP.")
        )));
        addons.add(new Entry("Instruments", "minecraft:note_block", List.of(
            new Page("Battle Bards",
                "§dGenshin Instruments / Even More Instruments§r - 15 playable instruments double as Special-class performance weapons.\n\n" +
                "Yes, you can fight the Wither with a lyre. Special affinity scales the encore.")
        )));
        addons.add(new Entry("Mob Variant Mods", "minecraft:creeper_spawn_egg", List.of(
            new Page("Creeper Overhaul",
                "§2Creeper Overhaul§r adds biome-themed creepers.\n\n" +
                "Each variant inherits base Creeper combat AI but with biome-flavored explosions (Cave Creeper blinds, Snowy Creeper slows, etc.). Plan for the post-blast status effect."),
            new Page("Variants, Ventures & More",
                "§3Variants & Ventures§r adds zombie, skeleton, and spider sub-variants with stat tweaks. Stats roll within the species' Craftics range, so existing loadouts handle them.\n\n" +
                "§aSpring to Life§r adds variant cows, pigs, chickens - all taming + ally rules apply unchanged.\n\n" +
                "§fPale Garden Backport§r brings the Creaking fight to older Minecraft versions.\n\n" +
                "§bMulti Arrow Effects§r lets tipped arrows stack multiple effects per shot.")
        )));
        CATEGORIES.add(new Category("Addon Mods", "minecraft:copper_ingot",
            "Supported mods and what they add. Entries apply only if the mod is installed.", addons));

        // === HALL OF TESTERS ===
        // Custom-rendered from TesterRegistry by GuideBookScreen (it draws each
        // person's skin head and their live rank color), so no static entries
        // are needed here - the category just provides the sidebar tab.
        CATEGORIES.add(new Category("Hall of Testers", "minecraft:player_head",
            "The people who built, tested, and debugged Craftics. Thank you.", List.of()));
    }
}

