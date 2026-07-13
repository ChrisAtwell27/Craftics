package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the seam between {@link CrafticsEnchantments}' Java table and the JSON that actually
 * defines each enchantment to the game.
 *
 * <p>The two halves are written separately and nothing at compile time ties them together, so
 * they can silently disagree: a JSON capped at 3 while the Java table promises 5 means tooltips
 * advertise a level the game will never grant. These tests read the real resource files and fail
 * the build on any drift.
 *
 * <p>Deliberately string/file based rather than loading Minecraft - the test JVM has no MC
 * bootstrap, so registries and item classes are unavailable here.
 */
class CrafticsEnchantmentsTest {

    /**
     * Stonecutter runs each version's tests from {@code versions/<ver>/}, not the repo root, so a
     * plain relative path misses the shared resource tree. Walk up until we find it.
     */
    private static final Path ROOT = findRoot();
    private static final Path DATA = ROOT.resolve("src/main/resources/data/craftics");
    private static final Path LANG = ROOT.resolve("src/main/resources/assets/craftics/lang/en_us.json");

    private static Path findRoot() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && p != null; i++) {
            if (Files.isDirectory(p.resolve("src/main/resources/data/craftics"))) return p;
            p = p.getParent();
        }
        throw new IllegalStateException("could not locate the repo root from " + Path.of("").toAbsolutePath());
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** Pull a top-level integer field out of a JSON blob without a JSON parser on the test path. */
    private static int intField(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(m.find(), "missing field " + field);
        return Integer.parseInt(m.group(1));
    }

    @Test
    void everyEnchantment_hasAJsonDefinition() {
        for (var e : CrafticsEnchantments.ALL) {
            Path json = DATA.resolve("enchantment/" + e.id() + ".json");
            assertTrue(Files.exists(json),
                e.id() + " is in CrafticsEnchantments.ALL but has no " + json);
        }
    }

    @Test
    void jsonMaxLevel_matchesTheJavaTable() throws IOException {
        for (var e : CrafticsEnchantments.ALL) {
            String json = read(DATA.resolve("enchantment/" + e.id() + ".json"));
            assertEquals(e.maxLevel(), intField(json, "max_level"),
                e.id() + ": JSON max_level disagrees with CrafticsEnchantments.Entry.maxLevel");
        }
    }

    /** A shovel enchantment must be supported by shovels, and a hoe one by hoes. */
    @Test
    void jsonSupportedItems_matchesTheEntrysTool() throws IOException {
        for (var e : CrafticsEnchantments.ALL) {
            String json = read(DATA.resolve("enchantment/" + e.id() + ".json"));
            String expected = "#craftics:enchantable/"
                + (e.tool() == CrafticsEnchantments.Tool.SHOVEL ? "shovel" : "hoe");
            assertTrue(json.contains("\"supported_items\": \"" + expected + "\""),
                e.id() + ": JSON supported_items should be " + expected);
        }
    }

    /**
     * Without this tag entry the enchantment exists but can never be found: the enchanting table
     * and the random-book loot both roll out of #minecraft:non_treasure.
     */
    @Test
    void everyEnchantment_isDiscoverable() throws IOException {
        String tag = read(ROOT.resolve("src/main/resources/data/minecraft/tags/enchantment/non_treasure.json"));
        for (var e : CrafticsEnchantments.ALL) {
            assertTrue(tag.contains("\"" + e.fullId() + "\""),
                e.fullId() + " is missing from the non_treasure tag, so nothing can ever roll it");
        }
    }

    @Test
    void everyEnchantment_hasATranslation() throws IOException {
        String lang = read(LANG);
        for (var e : CrafticsEnchantments.ALL) {
            assertTrue(lang.contains("\"enchantment.craftics." + e.id() + "\""),
                e.id() + " has no enchantment.craftics." + e.id() + " translation key");
        }
    }

    @Test
    void ids_areUniqueAndFullyQualified() {
        var seen = new java.util.HashSet<String>();
        for (var e : CrafticsEnchantments.ALL) {
            assertTrue(seen.add(e.id()), "duplicate enchantment id: " + e.id());
            assertEquals("craftics:" + e.id(), e.fullId());
        }
    }

    @Test
    void poolFor_returnsOnlyThatToolsEnchantments() {
        var shovels = java.util.List.of(CrafticsEnchantments.poolFor(CrafticsEnchantments.Tool.SHOVEL));
        var hoes = java.util.List.of(CrafticsEnchantments.poolFor(CrafticsEnchantments.Tool.HOE));
        assertTrue(shovels.contains("honed"));
        assertTrue(shovels.contains("fire_fang"));
        assertTrue(hoes.contains("reserving"));
        assertTrue(hoes.contains("radiant"));
        // The two pools must not bleed into each other - a hoe enchant on a shovel would be
        // rolled by loot but could never be read back by the shovel-filtered level lookup.
        for (String s : shovels) assertTrue(!hoes.contains(s), s + " is in both pools");
    }

    @Test
    void byId_acceptsBareAndQualifiedIds() {
        assertEquals(CrafticsEnchantments.HONED, CrafticsEnchantments.byId("honed"));
        assertEquals(CrafticsEnchantments.HONED, CrafticsEnchantments.byId("craftics:honed"));
        assertEquals(null, CrafticsEnchantments.byId("minecraft:sharpness"));
        assertEquals(null, CrafticsEnchantments.byId(null));
    }
}
