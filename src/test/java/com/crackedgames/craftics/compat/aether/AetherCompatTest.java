package com.crackedgames.craftics.compat.aether;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of the Aether compat that are rules rather than registrations.
 *
 * <p>Nothing here can look an Aether item up - that needs a bootstrapped registry and the mod
 * itself - so these pin the arithmetic and the name handling, which is where a compat like
 * this goes quietly wrong: a formula off by a rounding, a suffix that also matches something
 * it should not.
 */
class AetherCompatTest {

    // ── Zanite wear scaling ──

    @Test
    @DisplayName("a zanite weapon matches the Aether's own curve")
    void zaniteFollowsTheAetherCurve() {
        // The Aether's 5-damage zanite sword: nothing until a quarter worn, +3 at half,
        // +5 at three quarters, +8 on the swing before it breaks.
        assertEquals(0, AetherCompat.zaniteBonus(5, 0, 250));
        assertEquals(0, AetherCompat.zaniteBonus(5, 62, 250));
        assertEquals(3, AetherCompat.zaniteBonus(5, 125, 250));
        assertEquals(5, AetherCompat.zaniteBonus(5, 188, 250));
        assertEquals(8, AetherCompat.zaniteBonus(5, 250, 250));
    }

    @Test
    @DisplayName("the bonus scales with the weapon's own power and never goes negative")
    void zaniteScalesAndFloors() {
        assertEquals(14, AetherCompat.zaniteBonus(9, 250, 250), "1.5x of a 9-damage blade, rounded");
        assertEquals(0, AetherCompat.zaniteBonus(9, 10, 250), "new: no bonus, and no penalty");
        assertEquals(0, AetherCompat.zaniteBonus(0, 200, 250));
        assertEquals(0, AetherCompat.zaniteBonus(9, 200, 0), "an unbreakable stack has no wear");
        assertEquals(14, AetherCompat.zaniteBonus(9, 999, 250), "over-worn clamps at fully worn");
    }

    @Test
    @DisplayName("the bonus only ever grows as the weapon wears")
    void zaniteIsMonotonic() {
        int last = 0;
        for (int damage = 0; damage <= 250; damage++) {
            int bonus = AetherCompat.zaniteBonus(9, damage, 250);
            assertTrue(bonus >= last, "bonus dropped at " + damage + " durability used");
            last = bonus;
        }
    }

    // ── Creature lists ──

    @Test
    @DisplayName("the Pig Slayer's list is the Aether's pig tag")
    void pigs() {
        for (String pig : new String[]{"minecraft:pig", "aether:phyg", "minecraft:piglin",
                "minecraft:piglin_brute", "minecraft:zombified_piglin", "minecraft:hoglin",
                "minecraft:zoglin"}) {
            assertTrue(AetherCompat.isPig(pig), pig);
        }
        assertFalse(AetherCompat.isPig("minecraft:cow"));
        assertFalse(AetherCompat.isPig("aether:flying_cow"));
        assertFalse(AetherCompat.isPig(null));
    }

    @Test
    @DisplayName("cloud crystals hit blazes and fire minions harder, nothing else")
    void fireMobs() {
        assertTrue(AetherCompat.isFireMob("minecraft:blaze"));
        assertTrue(AetherCompat.isFireMob("aether:fire_minion"));
        assertFalse(AetherCompat.isFireMob("minecraft:magma_cube"));
        assertFalse(AetherCompat.isFireMob(null));
    }

    // ── Worn gear ──

    @Test
    @DisplayName("an armor piece gives up its material; nothing else does")
    void materialOf() {
        assertEquals("valkyrie", AetherScanner.materialOf("valkyrie_chestplate"));
        assertEquals("neptune", AetherScanner.materialOf("neptune_helmet"));
        assertEquals("sentry", AetherScanner.materialOf("sentry_boots"));
        assertNull(AetherScanner.materialOf("valkyrie_gloves"), "gloves are an accessory, not armor");
        assertNull(AetherScanner.materialOf("valkyrie_lance"));
        assertNull(AetherScanner.materialOf(null));
    }

    @Test
    @DisplayName("gloves are worth 1, the Aether's strong gloves 2")
    void gloveBonus() {
        assertEquals(1, AetherScanner.gloveBonus("leather_gloves", false));
        assertEquals(1, AetherScanner.gloveBonus("iron_gloves", false));
        assertEquals(1, AetherScanner.gloveBonus("neptune_gloves", false));
        assertEquals(2, AetherScanner.gloveBonus("gravitite_gloves", false));
        assertEquals(2, AetherScanner.gloveBonus("valkyrie_gloves", false));
        assertEquals(2, AetherScanner.gloveBonus("netherite_gloves", false));
        assertEquals(0, AetherScanner.gloveBonus("zanite_ring", false));
        assertEquals(0, AetherScanner.gloveBonus(null, false));
    }

    @Test
    @DisplayName("zanite gloves hit harder once worn, and only zanite ones")
    void zaniteGloves() {
        assertEquals(1, AetherScanner.gloveBonus("zanite_gloves", false));
        assertEquals(2, AetherScanner.gloveBonus("zanite_gloves", true));
        assertEquals(1, AetherScanner.gloveBonus("iron_gloves", true));
    }

    @Test
    @DisplayName("gloves raise Physical by their level, and leave Melee Power alone")
    void glovesArePhysical() {
        // Gloves make a bare hand hit harder. As Melee Power they raised every sword, axe and
        // hammer the wearer picked up as well.
        com.crackedgames.craftics.api.StatModifiers weak = new com.crackedgames.craftics.api.StatModifiers();
        AetherScanner.applyAccessories(weak, java.util.Map.of("iron_gloves", 1), false);
        com.crackedgames.craftics.api.StatModifiers strong = new com.crackedgames.craftics.api.StatModifiers();
        AetherScanner.applyAccessories(strong, java.util.Map.of("valkyrie_gloves", 1), false);

        assertEquals(1, weak.get(com.crackedgames.craftics.combat.TrimEffects.Bonus.PHYSICAL_POWER));
        assertEquals(2, strong.get(com.crackedgames.craftics.combat.TrimEffects.Bonus.PHYSICAL_POWER));
        assertEquals(0, weak.get(com.crackedgames.craftics.combat.TrimEffects.Bonus.MELEE_POWER));
        assertEquals(0, strong.get(com.crackedgames.craftics.combat.TrimEffects.Bonus.MELEE_POWER));
    }

    @Test
    @DisplayName("a Physical bonus counts toward Physical affinity and no other type")
    void physicalBonusIsOnlyPhysical() {
        var scan = new com.crackedgames.craftics.combat.TrimEffects.TrimScan(
            java.util.Map.of(com.crackedgames.craftics.combat.TrimEffects.Bonus.PHYSICAL_POWER, 2),
            com.crackedgames.craftics.combat.TrimEffects.SetBonus.NONE, "", 0,
            java.util.Map.of(), java.util.Map.of(), java.util.List.of());

        for (var type : com.crackedgames.craftics.combat.DamageType.values()) {
            int expected = type == com.crackedgames.craftics.combat.DamageType.PHYSICAL ? 2 : 0;
            assertEquals(expected, com.crackedgames.craftics.combat.DamageType.getTrimBonus(scan, type),
                type.name());
        }
    }

    // ── Gear from below ──

    @Test
    @DisplayName("an outsider weapon gives up a fifth, not the Aether's two thirds")
    void outsiderWeaponIsALean() {
        assertEquals(10, AetherEffects.Outsider.weaponDamage(12), "the Aether itself would leave 4");
        assertEquals(8, AetherEffects.Outsider.weaponDamage(9), "7.2 rounds up: never more than a fifth lost");
        assertEquals(4, AetherEffects.Outsider.weaponDamage(5));
        assertEquals(3, AetherEffects.Outsider.weaponDamage(3), "too small to lose a whole point");
        assertEquals(1, AetherEffects.Outsider.weaponDamage(1), "a hit that lands still hurts");
        assertEquals(0, AetherEffects.Outsider.weaponDamage(0), "a miss stays a miss");
    }

    @Test
    @DisplayName("outsider armor lets a twentieth more through per piece, capped at four")
    void outsiderArmorIsALean() {
        assertEquals(20, AetherEffects.Outsider.armorDamage(20, 0), "Aether armor, or none: no change");
        assertEquals(21, AetherEffects.Outsider.armorDamage(20, 1));
        assertEquals(24, AetherEffects.Outsider.armorDamage(20, 4));
        assertEquals(24, AetherEffects.Outsider.armorDamage(20, 9), "four slots, four pieces at most");
        assertEquals(6, AetherEffects.Outsider.armorDamage(5, 4), "small hits round up, by one");
        assertEquals(0, AetherEffects.Outsider.armorDamage(0, 4));
    }

    @Test
    @DisplayName("the penalty never outweighs the gear")
    void penaltyStaysMild() {
        for (int damage = 1; damage <= 60; damage++) {
            assertTrue(AetherEffects.Outsider.weaponDamage(damage) * 10 >= damage * 8,
                "weapon lost more than a fifth at " + damage);
            assertTrue(AetherEffects.Outsider.armorDamage(damage, 4) <= damage * 1.2 + 1,
                "armor let through more than 20% (+1 rounding) at " + damage);
        }
    }

    // ── Armor sets: one effect each, and no two the same ──

    @Test
    @DisplayName("gravitite armor levitates an attacker a quarter of the time with the full set")
    void gravititeUpdraftChance() {
        assertEquals(0.25, AetherEffects.Updraft.chance(4), 1e-9);
        assertEquals(0.0625, AetherEffects.Updraft.chance(1), 1e-9, "each piece is a quarter of it");
        assertEquals(0.125, AetherEffects.Updraft.chance(2), 1e-9);
        assertEquals(0.0, AetherEffects.Updraft.chance(0), 1e-9);
        assertEquals(0.25, AetherEffects.Updraft.chance(9), 1e-9, "there are only four slots");
        assertEquals(0.0, AetherEffects.Updraft.chance(-1), 1e-9);
    }

    @Test
    @DisplayName("zanite armor takes 1 more off each hit as the fight wears on, up to 3")
    void zaniteHardens() {
        assertEquals(8, AetherEffects.Hardened.reduced(8, 0), "the first hit lands in full");
        assertEquals(7, AetherEffects.Hardened.reduced(8, 1));
        assertEquals(6, AetherEffects.Hardened.reduced(8, 2));
        assertEquals(5, AetherEffects.Hardened.reduced(8, 3));
        assertEquals(5, AetherEffects.Hardened.reduced(8, 12), "and no further than 3");
    }

    @Test
    @DisplayName("hardened zanite never turns a hit into nothing")
    void zaniteLeavesAHitAHit() {
        assertEquals(1, AetherEffects.Hardened.reduced(2, 3));
        assertEquals(1, AetherEffects.Hardened.reduced(1, 3));
        assertEquals(0, AetherEffects.Hardened.reduced(0, 3), "a hit that already did nothing still does");
        assertEquals(8, AetherEffects.Hardened.reduced(8, -2));
    }

    @Test
    @DisplayName("obsidian armor halves a hit, rounding in the attacker's favour")
    void obsidianHalves() {
        assertEquals(5, AetherEffects.Tempered.halved(10));
        assertEquals(5, AetherEffects.Tempered.halved(9), "half of 9, rounded up");
        assertEquals(1, AetherEffects.Tempered.halved(2));
        assertEquals(1, AetherEffects.Tempered.halved(1), "a hit of 1 is still a hit");
        assertEquals(0, AetherEffects.Tempered.halved(0));
    }

    @Test
    @DisplayName("zanite is the armor that carries Pet affinity, and no other Aether set does")
    void zaniteIsThePetSet() throws IOException {
        // Nothing else in the game gives Pet affinity from armor, so a pet build has exactly
        // one set to wear. Read from the source, as the tooltip checks below are: the sets
        // are registered against items that only exist with the Aether installed.
        String sets = method(
            read("src/main/java/com/crackedgames/craftics/compat/aether/AetherCompat.java"),
            "private static boolean registerArmorSets()");

        assertTrue(sets.contains("set(\"zanite\", DamageType.PET,"), "zanite should be the Pet set");
        assertEquals(1, sets.split("DamageType\\.PET", -1).length - 1, "and the only one");
    }

    // ── Tooltip parity ──
    // The tooltip table lives in the client source set, which tests cannot see, so the two
    // files are compared as text - the same approach as the Simply Swords coverage test.

    private static final Pattern WEAPON = Pattern.compile("\\b(?:melee|ranged)\\(\"([a-z_]+)\"");
    private static final Pattern USABLE = Pattern.compile("\\busable\\(\"([a-z_]+)\"");

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src/main/java/com/crackedgames/craftics"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException(
            "could not find the repo root from " + Path.of("").toAbsolutePath());
    }

    private static String read(String relative) throws IOException {
        return Files.readString(repoRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static Set<String> matches(String src, Pattern pattern) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = pattern.matcher(src);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    /** The body of one method, so a string in the wrong table cannot satisfy the check. */
    private static String method(String src, String signature) {
        int start = src.indexOf(signature);
        assertTrue(start >= 0, "method not found: " + signature);
        int end = src.indexOf("\n    }\n", start);
        assertTrue(end > start, "could not find the end of " + signature);
        return src.substring(start, end);
    }

    @Test
    @DisplayName("every weapon with an ability says so in its tooltip")
    void weaponsAreDescribed() throws IOException {
        String compat = read("src/main/java/com/crackedgames/craftics/compat/aether/AetherCompat.java");
        String tooltips = read("src/client/java/com/crackedgames/craftics/client/AetherTooltips.java");
        String table = method(tooltips, "static String[] weaponLines(String path)");

        Set<String> weapons = matches(method(compat, "private static boolean registerWeapons()"), WEAPON);
        assertTrue(weapons.size() >= 12, "expected the dungeon and ranged weapons, found " + weapons);
        for (String weapon : weapons) {
            assertTrue(table.contains("\"" + weapon + "\""),
                weapon + " is registered with an ability but has no tooltip line");
        }
        // The tiers are registered through tier(), which the pattern above cannot expand.
        for (String material : new String[]{"skyroot", "holystone", "zanite", "gravitite"}) {
            assertTrue(table.contains("\"" + material + "_sword\""), material + " sword");
            assertTrue(table.contains("\"" + material + "_axe\""), material + " axe");
        }
        // Gravitite gives every one of its tools something to do, not only the two that swing.
        assertTrue(table.contains("\"gravitite_shovel\""), "gravitite shovel");
        assertTrue(table.contains("\"gravitite_hoe\""), "gravitite hoe");
        String gear = method(tooltips, "static String[] gearLines(String path)");
        assertTrue(gear.contains("\"gravitite_pickaxe\""), "gravitite pickaxe");
    }

    /** A name in a {@code case} label: {@code case "iron_ring" ->}, or one of several on a line. */
    private static final Pattern CASE_NAME = Pattern.compile("\"([a-z_]+)\"(?=[^\n]*->)");

    @Test
    @DisplayName("plain rings and pendants each do one small thing, and two rings do it twice")
    void plainJewellery() {
        com.crackedgames.craftics.api.StatModifiers mods = new com.crackedgames.craftics.api.StatModifiers();
        AetherScanner.applyAccessories(mods, java.util.Map.of(
            "iron_ring", 2, "iron_pendant", 1, "golden_ring", 2, "golden_pendant", 1), false);

        assertEquals(2, mods.get(com.crackedgames.craftics.combat.TrimEffects.Bonus.MAX_HP), "iron rings");
        assertEquals(1, mods.get(com.crackedgames.craftics.combat.TrimEffects.Bonus.DEFENSE), "iron pendant");
        assertEquals(2, mods.get(com.crackedgames.craftics.combat.TrimEffects.Bonus.ARMOR_PEN), "golden rings");
        assertEquals(1, mods.get(com.crackedgames.craftics.combat.TrimEffects.Bonus.SPECIAL_POWER), "golden pendant");
        assertEquals(0, mods.get(com.crackedgames.craftics.combat.TrimEffects.Bonus.LUCK), "that is zanite's");
        assertTrue(mods.getCombatEffects().isEmpty(), "and nothing that fires in a fight");
    }

    @Test
    @DisplayName("each dyed cape takes a turn off one kind of trouble, and no two the same kind")
    void dyedCapes() {
        var weak = com.crackedgames.craftics.combat.CombatEffects.EffectType.WEAKNESS;
        var lifted = com.crackedgames.craftics.combat.CombatEffects.EffectType.LEVITATION;
        var poison = com.crackedgames.craftics.combat.CombatEffects.EffectType.POISON;
        var burn = com.crackedgames.craftics.combat.CombatEffects.EffectType.BURNING;
        var soulBurn = com.crackedgames.craftics.combat.CombatEffects.EffectType.SOUL_BURNING;

        assertTrue(AetherScanner.cape("red_cape").covers(weak));
        assertTrue(AetherScanner.cape("blue_cape").covers(lifted));
        assertTrue(AetherScanner.cape("white_cape").covers(poison));
        assertTrue(AetherScanner.cape("yellow_cape").covers(burn));
        assertTrue(AetherScanner.cape("yellow_cape").covers(soulBurn), "a burn is a burn");
        for (String cape : new String[]{"red_cape", "blue_cape", "white_cape", "yellow_cape"}) {
            int kinds = 0;
            for (var kind : new com.crackedgames.craftics.combat.CombatEffects.EffectType[]{weak, lifted, poison, burn}) {
                if (AetherScanner.cape(cape).covers(kind)) kinds++;
            }
            assertEquals(1, kinds, cape + " keeps off one thing");
        }
        assertNull(AetherScanner.cape("agility_cape"), "the capes that already did something are not these");
        assertNull(AetherScanner.cape("swet_cape"));
        assertNull(AetherScanner.cape(null));

        assertEquals(2, AetherEffects.Shortened.left(3));
        assertEquals(0, AetherEffects.Shortened.left(1), "a short one does not take at all");
        assertEquals(0, AetherEffects.Shortened.left(0));

        com.crackedgames.craftics.api.StatModifiers mods = new com.crackedgames.craftics.api.StatModifiers();
        AetherScanner.applyAccessories(mods, java.util.Map.of("blue_cape", 1), false);
        assertEquals(1, mods.getCombatEffects().size(), "worn, it is one effect in the fight");
    }

    @Test
    @DisplayName("every accessory that does something says so, the Swet Cape included")
    void accessoriesAreDescribed() throws IOException {
        String scanner = read("src/main/java/com/crackedgames/craftics/compat/aether/AetherScanner.java");
        String tooltips = read("src/client/java/com/crackedgames/craftics/client/AetherTooltips.java");
        String table = method(tooltips, "static String[] gearLines(String path)");

        Set<String> worn = matches(
            method(scanner, "static void applyAccessories(StatModifiers mods, Map<String, Integer> worn, boolean wornZanite)"),
            CASE_NAME);
        assertTrue(worn.size() >= 18, "expected every accessory with an effect, found " + worn);
        for (String name : new String[]{"iron_ring", "iron_pendant", "golden_ring", "golden_pendant",
                "red_cape", "blue_cape", "white_cape", "yellow_cape"}) {
            assertTrue(worn.contains(name), name + " is still an ornament");
        }
        for (String name : worn) {
            assertTrue(table.contains("\"" + name + "\""), name + " does something and its tooltip does not say what");
        }
        // Read by the swets, not by the scanner, so the loop above cannot see it.
        assertTrue(table.contains("\"swet_cape\""), "the Swet Cape works and never said so");
    }

    @Test
    @DisplayName("Obsidian is Phoenix gear that got wet, and both ends of that say so")
    void obsidianSaysWhereItComesFrom() throws IOException {
        // No loot table pays Obsidian and nothing crafts it. The only way to it is the
        // Aether's own: Phoenix armor cools to it in water. A set nobody is told how to get
        // is a set nobody has.
        String tooltips = read("src/client/java/com/crackedgames/craftics/client/AetherTooltips.java");
        String gear = method(tooltips, "static String[] gearLines(String path)");
        assertTrue(gear.contains("\"phoenix_gloves\"") && gear.contains("PHOENIX_COOLS"));
        assertTrue(gear.contains("\"obsidian_gloves\"") && gear.contains("OBSIDIAN_FROM"));
        String note = method(tooltips, "static String armorNote(String path)");
        assertTrue(note.contains("\"phoenix\"") && note.contains("PHOENIX_COOLS"));
        assertTrue(note.contains("\"obsidian\"") && note.contains("OBSIDIAN_FROM"));
    }

    @Test
    @DisplayName("every Valkyrie tool there is drops from the Valkyrie Queen, the hoe with the rest")
    void theQueenPaysEveryValkyrieTool() throws IOException {
        // The hoe is a Special weapon here like any hoe. It was the one Valkyrie tool in no
        // loot table, and nothing crafts it.
        String silver = read("src/main/resources/data/craftics/craftics/compat/aether/biomes/aether_silver_dungeon.json");
        String treasure = silver.substring(silver.indexOf("\"boss_loot\""));
        for (String tool : new String[]{"axe", "pickaxe", "shovel", "hoe", "lance"}) {
            assertTrue(treasure.contains("\"aether:valkyrie_" + tool + "\""), "no Valkyrie " + tool);
        }
    }

    @Test
    @DisplayName("every usable item says what it does")
    void usablesAreDescribed() throws IOException {
        String compat = read("src/main/java/com/crackedgames/craftics/compat/aether/AetherCompat.java");
        String tooltips = read("src/client/java/com/crackedgames/craftics/client/AetherTooltips.java");
        String table = method(tooltips, "static String[] gearLines(String path)");

        Set<String> usables = matches(method(compat, "private static boolean registerUsables()"), USABLE);
        assertEquals(5, usables.size(), "usables found: " + usables);
        for (String usable : usables) {
            assertTrue(table.contains("\"" + usable + "\""), usable + " has no tooltip line");
        }
    }

    @Test
    void trappedDungeonStoneIsLaidAsPlainStoneInAnArena() {
        // Stepped on, a trapped block spawns a live mob with its own AI. An arena floor is
        // walked on every turn.
        assertEquals("aether:carved_stone", AetherCompat.arenaSafeBlockId("aether:trapped_carved_stone"));
        assertEquals("aether:light_angelic_stone", AetherCompat.arenaSafeBlockId("aether:trapped_light_angelic_stone"));
        assertEquals("aether:hellfire_stone", AetherCompat.arenaSafeBlockId("aether:trapped_hellfire_stone"));
        // The lid over a loot room is floor here, and is laid as the locked stone around it.
        assertEquals("aether:locked_angelic_stone",
            AetherCompat.arenaSafeBlockId("aether:treasure_doorway_angelic_stone"));
        assertEquals("aether:locked_light_hellfire_stone",
            AetherCompat.arenaSafeBlockId("aether:treasure_doorway_light_hellfire_stone"));
        // A boss doorway is left as it was saved: an open door stays an open door.
        assertEquals("aether:boss_doorway_angelic_stone",
            AetherCompat.arenaSafeBlockId("aether:boss_doorway_angelic_stone"));
        // Everything else is left exactly as it was saved.
        assertEquals("aether:locked_carved_stone", AetherCompat.arenaSafeBlockId("aether:locked_carved_stone"));
        assertEquals("aether:carved_stone", AetherCompat.arenaSafeBlockId("aether:carved_stone"));
        assertEquals("minecraft:trapped_chest", AetherCompat.arenaSafeBlockId("minecraft:trapped_chest"));
        assertNull(AetherCompat.arenaSafeBlockId(null));
    }

    @Test
    void theThreeDungeonsKeepTheirRoomsAsBuilt() {
        assertTrue(AetherCompat.isHandBuiltArena(AetherCompat.BRONZE_DUNGEON));
        assertTrue(AetherCompat.isHandBuiltArena(AetherCompat.SILVER_DUNGEON));
        assertTrue(AetherCompat.isHandBuiltArena(AetherCompat.GOLD_DUNGEON));
        assertFalse(AetherCompat.isHandBuiltArena("plains"));
        assertFalse(AetherCompat.isHandBuiltArena(null));
    }
}
