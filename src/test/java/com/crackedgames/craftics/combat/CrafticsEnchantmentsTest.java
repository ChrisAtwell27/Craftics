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

    /**
     * A shovel enchantment must be supported by shovels, a hoe one by hoes, an axe one by axes -
     * and a MULTI-tool enchantment (Hilt, Dull) by ALL of its tools. The JSON's
     * {@code supported_items} must reference exactly the union of the entry's tools' tags: every
     * tag present, and no extra Craftics tag that the entry does not claim. This still catches an
     * enchant pointing at the wrong tag (the original bug), it just now accepts more than one.
     *
     * <p>Blunt coverage is deliberately NOT checked here: blunt weapons are addon-registered and
     * have no data-pack tag, so Hilt-on-blunt lives only in the runtime filter and the enchanter
     * pool, never in this JSON. See {@code multiToolEnchant_isReturnedByEachToolsPool}.
     */
    /**
     * Minecraft's enchantment {@code supported_items}/{@code primary_items} is either a SINGLE tag
     * string ("#namespace:path") or a LIST of plain item ids. A "#..." tag string inside a list is
     * NOT a valid resource location and makes the whole enchantment registry fail to load, which
     * crashes world creation with "Failed to load registries". The compile and the other unit tests
     * do not parse these the way the game does, so this format check is the only thing in the gate
     * that catches it. It exists because exactly this shipped and crashed a client (multi-tool Hilt
     * and Dull used a list of tag strings).
     */
    @Test
    void jsonItemFields_areGameLegalResourceLocations() throws IOException {
        java.util.regex.Pattern tagInList =
            java.util.regex.Pattern.compile("\"(?:supported_items|primary_items)\"\\s*:\\s*\\[[^\\]]*\"#");
        for (var e : CrafticsEnchantments.ALL) {
            String json = read(DATA.resolve("enchantment/" + e.id() + ".json"));
            assertTrue(!tagInList.matcher(json).find(),
                e.id() + ": supported_items/primary_items has a '#' tag inside a list. A tag must be "
                    + "a single string, not a list element. This crashes the enchantment registry at "
                    + "world load. Use one composite tag string instead.");
        }
    }

    /**
     * Every {@code exclusive_set} tag an enchantment references must exist as a real tag file. A
     * reference to a missing tag fails the enchantment registry load at world creation, the same
     * class of crash as a malformed item field, and neither the compile nor the other tests catch
     * it because they do not resolve the reference the way the game does.
     */
    @Test
    void referencedExclusiveSetTags_exist() throws IOException {
        var ref = java.util.regex.Pattern.compile(
            "\"exclusive_set\"\\s*:\\s*\"#craftics:(exclusive_set/[a-z_]+)\"");
        for (var e : CrafticsEnchantments.ALL) {
            String json = read(DATA.resolve("enchantment/" + e.id() + ".json"));
            var m = ref.matcher(json);
            while (m.find()) {
                java.nio.file.Path tagFile = DATA.resolve("tags/enchantment/" + m.group(1) + ".json");
                assertTrue(java.nio.file.Files.exists(tagFile),
                    e.id() + ": references exclusive_set tag " + m.group(1)
                        + " but no tag file exists at " + tagFile);
            }
        }
    }

    @Test
    void jsonSupportedItems_matchesTheEntrysTools() throws IOException {
        for (var e : CrafticsEnchantments.ALL) {
            String json = read(DATA.resolve("enchantment/" + e.id() + ".json"));
            String block = supportedItemsBlock(json, e.id());
            // Minecraft's supported_items takes a SINGLE tag string, never a list of tag
            // strings ("#..." is not a valid resource location inside a list). A multi-tool
            // enchant therefore points at ONE composite tag whose values are the per-tool tags,
            // exactly as vanilla Sharpness points at #minecraft:enchantable/sharp_weapon. So the
            // set of tags this enchant reaches is the block's own tags plus anything a composite
            // tag it names pulls in one level down. Resolve that before checking coverage.
            java.util.Set<String> reached = tagsReachedBy(block);
            for (var tool : e.tools()) {
                String tag = "craftics:enchantable/" + toolTagName(tool);
                assertTrue(reached.contains(tag),
                    e.id() + ": supported_items should reach " + tag + " (reached: " + reached + ")");
            }
            // No OTHER Craftics tool tag may be reached - a wrong tag is exactly the bug this guards.
            for (var tool : CrafticsEnchantments.Tool.values()) {
                if (e.appliesTo(tool)) continue;
                String tag = "craftics:enchantable/" + toolTagName(tool);
                assertTrue(!reached.contains(tag),
                    e.id() + ": supported_items reaches " + tag + " but the entry does not list that tool");
            }
        }
    }

    /**
     * The per-tool enchantable tags reachable from a supported_items block, following one level of
     * composite tag (a tag whose values are other tags, like vanilla sharp_weapon). Returns bare
     * ids without the leading '#'.
     */
    private static java.util.Set<String> tagsReachedBy(String block) throws IOException {
        java.util.Set<String> out = new java.util.HashSet<>();
        var m = java.util.regex.Pattern.compile("#(craftics:enchantable/[a-z_]+)").matcher(block);
        while (m.find()) {
            String id = m.group(1);
            out.add(id);
            // If this names a composite tag file, pull in the per-tool tags it references.
            java.nio.file.Path tagFile = DATA.resolve("tags/item/" + id.substring("craftics:".length()) + ".json");
            if (java.nio.file.Files.exists(tagFile)) {
                var inner = java.util.regex.Pattern.compile("#(craftics:enchantable/[a-z_]+)")
                    .matcher(read(tagFile));
                while (inner.find()) out.add(inner.group(1));
            }
        }
        return out;
    }

    /**
     * Isolate the {@code supported_items} value so the "no extra tag" check can't be fooled by a
     * tag that appears elsewhere in the file (e.g. {@code primary_items}). Handles both the
     * single-string form ({@code "supported_items": "#..."}) and the list form
     * ({@code "supported_items": [ "#...", "#..." ]}).
     */
    private static String supportedItemsBlock(String json, String id) {
        int key = json.indexOf("\"supported_items\"");
        assertTrue(key >= 0, id + ": JSON has no supported_items");
        int colon = json.indexOf(':', key);
        assertTrue(colon >= 0, id + ": malformed supported_items");
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i < json.length() && json.charAt(i) == '[') {
            int end = json.indexOf(']', i);
            assertTrue(end >= 0, id + ": unterminated supported_items list");
            return json.substring(i, end + 1);
        }
        int end = json.indexOf('\n', i);
        return json.substring(i, end < 0 ? json.length() : end);
    }

    /**
     * The tag path each tool's enchantments must point at. An exhaustive switch on purpose: a new
     * Tool constant should fail the build here rather than silently assert against the wrong tag,
     * which is what the old shovel/hoe ternary did.
     */
    private static String toolTagName(CrafticsEnchantments.Tool tool) {
        return switch (tool) {
            case SHOVEL -> "shovel";
            case HOE -> "hoe";
            case AXE -> "axe";
            case SWORD -> "sword";
        };
    }

    /** Every tool tag an entry points at must actually exist, or the enchantment goes on nothing. */
    @Test
    void everyToolTag_exists() {
        for (var e : CrafticsEnchantments.ALL) {
            for (var tool : e.tools()) {
                Path tag = DATA.resolve("tags/item/enchantable/" + toolTagName(tool) + ".json");
                assertTrue(Files.exists(tag),
                    e.id() + " is a " + tool + " enchantment but " + tag + " does not exist");
            }
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

    /**
     * A multi-tool enchantment must be offered on EVERY one of its tools' pools, and a single-tool
     * enchantment must never leak into a pool it does not belong to. This is the loot/enchanter
     * half of the multi-tool feature: Hilt and Dull go on axes as well as swords now.
     */
    @Test
    void multiToolEnchant_isReturnedByEachToolsPool() {
        var swords = java.util.List.of(CrafticsEnchantments.poolFor(CrafticsEnchantments.Tool.SWORD));
        var axes = java.util.List.of(CrafticsEnchantments.poolFor(CrafticsEnchantments.Tool.AXE));

        // Hilt and Dull are sword+axe, so both pools carry them.
        assertTrue(swords.contains("hilt"), "sword pool should include hilt");
        assertTrue(axes.contains("hilt"), "axe pool should include hilt");
        assertTrue(swords.contains("dull"), "sword pool should include dull");
        assertTrue(axes.contains("dull"), "axe pool should include dull");

        // Single-tool enchants stay in their own lane: Serrated is sword-only, Facade axe-only.
        assertTrue(swords.contains("serrated"), "sword pool should include serrated");
        assertTrue(!axes.contains("serrated"), "serrated is sword-only and must not be in the axe pool");
        assertTrue(axes.contains("facade"), "axe pool should include facade");
        assertTrue(!swords.contains("facade"), "facade is axe-only and must not be in the sword pool");
    }

    /**
     * Blunt coverage has no Tool and no tag, so it rides {@code poolForBlunt} instead. Hilt opts
     * into blunt; Dull does not. Nothing else in the table is blunt-eligible today.
     */
    @Test
    void bluntPool_holdsOnlyBluntFlaggedEnchants() {
        var blunt = java.util.List.of(CrafticsEnchantments.poolForBlunt());
        assertTrue(blunt.contains("hilt"), "hilt applies to blunt weapons and must be in the blunt pool");
        assertTrue(!blunt.contains("dull"), "dull is sword+axe only and must not be in the blunt pool");
        assertTrue(!blunt.contains("serrated"), "serrated is not blunt-eligible");
    }

    /** The primary tool (what tooltips/affinity key off) is the first tool listed. */
    @Test
    void primaryTool_isTheFirstListedTool() {
        assertEquals(CrafticsEnchantments.Tool.SWORD, CrafticsEnchantments.HILT.tool());
        assertEquals(CrafticsEnchantments.Tool.SWORD, CrafticsEnchantments.DULL.tool());
        assertEquals(CrafticsEnchantments.Tool.AXE, CrafticsEnchantments.FACADE.tool());
        assertTrue(CrafticsEnchantments.HILT.appliesTo(CrafticsEnchantments.Tool.AXE));
        assertTrue(CrafticsEnchantments.HILT.appliesToBlunt());
        assertTrue(!CrafticsEnchantments.DULL.appliesToBlunt());
    }

    @Test
    void byId_acceptsBareAndQualifiedIds() {
        assertEquals(CrafticsEnchantments.HONED, CrafticsEnchantments.byId("honed"));
        assertEquals(CrafticsEnchantments.HONED, CrafticsEnchantments.byId("craftics:honed"));
        assertEquals(null, CrafticsEnchantments.byId("minecraft:sharpness"));
        assertEquals(null, CrafticsEnchantments.byId(null));
    }
}
