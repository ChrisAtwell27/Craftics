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
}
