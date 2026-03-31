package com.crackedgames.craftics.achievement;

/**
 * All achievements in Craftics, organized by category.
 * Each achievement has a unique ID, display name, description, and category.
 */
public enum Achievement {
    // === Biome Boss Kills (18) ===
    BOSS_PLAINS("Plains Pacifier", "Defeat the Plains boss", Category.BOSS),
    BOSS_FOREST("Forest's Bane", "Defeat the Dark Forest boss", Category.BOSS),
    BOSS_SNOWY("Frostbreaker", "Defeat the Snowy Tundra boss", Category.BOSS),
    BOSS_MOUNTAIN("Peak Conqueror", "Defeat the Rockbreaker", Category.BOSS),
    BOSS_RIVER("Tidecaller's End", "Defeat the Tidecaller", Category.BOSS),
    BOSS_DESERT("Sunscorched", "Defeat the Scorching Desert boss", Category.BOSS),
    BOSS_JUNGLE("Jungle's Reckoning", "Defeat the Dense Jungle boss", Category.BOSS),
    BOSS_CAVE("Depths Delver", "Defeat the Underground Caverns boss", Category.BOSS),
    BOSS_DEEP_DARK("Silence the Dark", "Defeat the Warden", Category.BOSS),
    BOSS_NETHER_WASTES("Nether Survivor", "Defeat the Nether Wastes boss", Category.BOSS),
    BOSS_SOUL_SAND("Soul Reaper", "Defeat the Hollow King", Category.BOSS),
    BOSS_CRIMSON("Crimson Clearer", "Defeat the Crimson Ravager", Category.BOSS),
    BOSS_WARPED("Hex Breaker", "Defeat the Hexweaver", Category.BOSS),
    BOSS_BASALT("Molten Slayer", "Defeat the Molten King", Category.BOSS),
    BOSS_OUTER_END("Void Touched", "Defeat the Void Walker", Category.BOSS),
    BOSS_END_CITY("Architect's Fall", "Defeat the Shulker Architect", Category.BOSS),
    BOSS_CHORUS("Mind Over Matter", "Defeat the Chorus Mind", Category.BOSS),
    BOSS_DRAGONS_NEST("Dragon Slayer", "Defeat the Void Herald", Category.BOSS),

    // === Dimension Mastery (3) ===
    DIM_OVERWORLD("Overworld Champion", "Defeat all Overworld bosses", Category.BOSS),
    DIM_NETHER("Nether Conqueror", "Defeat all Nether bosses", Category.BOSS),
    DIM_END("Ender of Ends", "Defeat all End bosses", Category.BOSS),

    // === Class Mastery — Weapon Restricted (8) ===
    CLASS_SWORDMASTER("Swordmaster", "Defeat the Warden using only Slashing weapons", Category.CLASS),
    CLASS_LUMBERJACK("Lumberjack", "Defeat a Nether boss using only Cleaving weapons", Category.CLASS),
    CLASS_BONECRUSHER("Bonecrusher", "Defeat the Warden using only Blunt weapons", Category.CLASS),
    CLASS_TIDEBRINGER("Tidebringer", "Defeat the Dragon's Nest boss using only Water weapons", Category.CLASS),
    CLASS_REAPER("Reaper's Harvest", "Defeat a Nether boss using only Special weapons", Category.CLASS),
    CLASS_BEAST_TAMER("Beast Tamer", "Defeat an End boss using only Pet weapons", Category.CLASS),
    CLASS_SHARPSHOOTER("Sharpshooter", "Defeat the Shulker Architect using only Ranged weapons", Category.CLASS),
    CLASS_BARE_KNUCKLE("Bare Knuckle", "Defeat an End boss with only your fists", Category.CLASS),

    // === Class Mastery — Armor Restricted (7) ===
    ARMOR_BRAWLER("Brawler's Pride", "Defeat an End boss in full Leather armor", Category.CLASS),
    ARMOR_ROGUE("Rogue's Honor", "Defeat a Nether boss in full Chainmail armor", Category.CLASS),
    ARMOR_GUARD("Guard's Duty", "Defeat an End boss in full Iron armor", Category.CLASS),
    ARMOR_GAMBLER("Gambler's Luck", "Defeat a Nether boss in full Gold armor", Category.CLASS),
    ARMOR_KNIGHT("Knight's Valor", "Defeat the Warden in full Diamond armor", Category.CLASS),
    ARMOR_JUGGERNAUT("Juggernaut", "Defeat the Dragon's Nest boss in full Netherite armor", Category.CLASS),
    ARMOR_AQUATIC("Aquatic Assault", "Defeat a Nether boss in full Turtle armor", Category.CLASS),

    // === Combat Feats — Weapon Skills (12) ===
    FEAT_SKEWER("Skewer", "Hit 3+ enemies with a single Crossbow pierce shot", Category.COMBAT),
    FEAT_WHIRLWIND("Whirlwind", "Sweep 4+ enemies with a single sword swing", Category.COMBAT),
    FEAT_EXECUTION("Execution", "Kill an enemy with Netherite Sword's Execute ability", Category.COMBAT),
    FEAT_SHOCKWAVE("Shockwave", "Hit 5+ enemies with a single Mace shockwave", Category.COMBAT),
    FEAT_CORAL_REEF("Coral Reef", "Hit 4+ enemies with a single Coral Fan splash", Category.COMBAT),
    FEAT_MIND_GAMES("Mind Games", "Confuse an enemy into killing another enemy", Category.COMBAT),
    FEAT_CHAIN_STUN("Chain Stun", "Stun the same enemy 3 turns in a row", Category.COMBAT),
    FEAT_GLASS_CANNON("Glass Cannon", "Deal 20+ damage in a single hit", Category.COMBAT),
    FEAT_ARMOR_CRUSH("Armor Crush", "Ignore 5+ defense with a single Cleaving hit", Category.COMBAT),
    // FEAT_SPEAR_WALL removed — Spears not in MC 1.21.1
    FEAT_COUNTER_KILL("Counter Kill", "Kill an enemy with a Physical counterattack", Category.COMBAT),
    FEAT_CRITICAL_STREAK("Critical Streak", "Land 3 critical hits in a row", Category.COMBAT),

    // === Combat Feats — Items & Tactics (10) ===
    FEAT_UNDYING("Undying", "Survive lethal damage with a Totem of Undying", Category.COMBAT),
    FEAT_ZOOKEEPER("Zookeeper", "Have 3+ tamed allies alive at the same time", Category.COMBAT),
    FEAT_FISHERMAN("Fisherman's Luck", "Get a rare item from Fishing Rod", Category.COMBAT),
    FEAT_DEMOLITIONS("Demolitions Expert", "Kill 3+ enemies with a single TNT explosion", Category.COMBAT),
    FEAT_LIGHTNING_STRIKE("Lightning Strike", "Kill a Soaked enemy with a Lightning Rod", Category.COMBAT),
    FEAT_CHEF("Chef's Kiss", "Eat 5 different food items in a single combat", Category.COMBAT),
    FEAT_FORTRESS_BUILDER("Fortress Builder", "Place 5+ utility items in one combat", Category.COMBAT),
    FEAT_PEARL_CLUTCH("Pearl Clutch", "Ender Pearl to dodge a boss's telegraphed attack", Category.COMBAT),
    FEAT_MILK_SAVE("Milk Save", "Clear 3+ debuffs with a single Milk Bucket use", Category.COMBAT),
    FEAT_HORN_SECTION("Horn Section", "Use 4 different Goat Horns in a single combat", Category.COMBAT),

    // === Combat Feats — Status Effects (5) ===
    FEAT_ALCHEMIST("Alchemist", "Have 5+ different buffs active simultaneously", Category.COMBAT),
    FEAT_PLAGUE_DOCTOR("Plague Doctor", "Kill an enemy with Poison tick damage", Category.COMBAT),
    FEAT_PYROMANIAC("Pyromaniac", "Kill an enemy with Burn tick damage", Category.COMBAT),
    FEAT_DROWNED("Drowned", "Kill a Soaked enemy with Water damage", Category.COMBAT),
    FEAT_WITHERED("Withered Away", "Kill an enemy with Wither tick damage", Category.COMBAT),

    // === Boss Challenges (6) ===
    BOSS_FLAWLESS("Flawless", "Defeat any boss without taking damage", Category.CHALLENGE),
    BOSS_PACIFIST("Pacifist General", "Defeat a boss where only pets deal damage", Category.CHALLENGE),
    BOSS_SPEED_RUN("Speed Run", "Defeat any boss in 5 turns or fewer", Category.CHALLENGE),
    BOSS_ENDURANCE("Endurance", "Survive a boss fight lasting 30+ turns", Category.CHALLENGE),
    BOSS_SECOND_WIND("Second Wind", "Defeat a boss after Totem revival", Category.CHALLENGE),
    BOSS_PHASE_SKIPPER("Phase Skipper", "Kill a boss before it reaches Phase 2", Category.CHALLENGE),

    // === Collection (8) ===
    COLLECT_WARDROBE("Full Wardrobe", "Own every armor set type", Category.COLLECTION),
    COLLECT_TRIMS("Trim Collector", "Apply 5 different trim patterns", Category.COLLECTION),
    COLLECT_MATERIALS("Material Hoarder", "Use 5 different trim materials", Category.COLLECTION),
    COLLECT_FULL_SET("Complete Set", "Equip a full 4-piece matching trim set", Category.COLLECTION),
    COLLECT_PETS("Pet Collector", "Tame 5 different species", Category.COLLECTION),
    COLLECT_ARMORY("Armory", "Use every weapon type at least once", Category.COLLECTION),
    COLLECT_EMERALDS("Emerald Hoarder", "Accumulate 100 emeralds", Category.COLLECTION),
    COLLECT_HORNS("Horn Collector", "Find all 8 Goat Horn variants", Category.COLLECTION),

    // === Progression (5) ===
    PROG_NG_PLUS("New Game+", "Complete the game and enter NG+", Category.PROGRESSION),
    PROG_NG_PLUS_2("New Game++", "Reach NG+2", Category.PROGRESSION),
    PROG_MAX_LEVEL("Max Level", "Reach player level 20", Category.PROGRESSION),
    PROG_SPECIALIST("Specialist", "Put 5+ points into a single affinity", Category.PROGRESSION),
    PROG_JACK_OF_ALL("Jack of All Trades", "Put at least 1 point into every affinity", Category.PROGRESSION),

    // === Coral-Specific (4) ===
    CORAL_CRUSADER("Coral Crusader", "Defeat a boss using only Coral weapons", Category.CLASS),
    CORAL_BRAIN_DRAIN("Brain Drain", "Confuse 5 different enemies in one combat", Category.COMBAT),
    CORAL_FAN_CLUB("Fan Club", "Kill 3 enemies using only Coral Fan splashes", Category.COMBAT),
    CORAL_REEF_DWELLER("Reef Dweller", "Defeat a boss with Turtle armor + Coral weapon + Coast trim", Category.CLASS);

    public final String displayName;
    public final String description;
    public final Category category;

    Achievement(String displayName, String description, Category category) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
    }

    public enum Category {
        BOSS("Boss Kills", "\u00a76"),
        CLASS("Class Mastery", "\u00a7b"),
        COMBAT("Combat Feats", "\u00a7c"),
        CHALLENGE("Boss Challenges", "\u00a7d"),
        COLLECTION("Collection", "\u00a7e"),
        PROGRESSION("Progression", "\u00a7a");

        public final String displayName;
        public final String color;

        Category(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    /** Get the advancement resource path (lowercase enum name). */
    public String advancementPath() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Get the biome ID associated with a boss achievement, or null. */
    public String getBossbiomeId() {
        return switch (this) {
            case BOSS_PLAINS -> "plains";
            case BOSS_FOREST -> "forest";
            case BOSS_SNOWY -> "snowy";
            case BOSS_MOUNTAIN -> "mountain";
            case BOSS_RIVER -> "river";
            case BOSS_DESERT -> "desert";
            case BOSS_JUNGLE -> "jungle";
            case BOSS_CAVE -> "cave";
            case BOSS_DEEP_DARK -> "deep_dark";
            case BOSS_NETHER_WASTES -> "nether_wastes";
            case BOSS_SOUL_SAND -> "soul_sand_valley";
            case BOSS_CRIMSON -> "crimson_forest";
            case BOSS_WARPED -> "warped_forest";
            case BOSS_BASALT -> "basalt_deltas";
            case BOSS_OUTER_END -> "outer_end_islands";
            case BOSS_END_CITY -> "end_city";
            case BOSS_CHORUS -> "chorus_grove";
            case BOSS_DRAGONS_NEST -> "dragons_nest";
            default -> null;
        };
    }

    /** Look up boss achievement by biome ID. */
    public static Achievement getBossAchievementForBiome(String biomeId) {
        for (Achievement a : values()) {
            if (a.category == Category.BOSS && biomeId.equals(a.getBossbiomeId())) {
                return a;
            }
        }
        return null;
    }
}
