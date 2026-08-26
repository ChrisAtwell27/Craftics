package com.crackedgames.craftics.compat.simplyswords;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every Simply Swords unique that gets a Craftics ability must also get a tooltip describing it.
 *
 * <p>These are two lists in two files that have to agree, and nothing structural keeps them
 * together: the abilities are registered server-side in {@code SimplySwordsUniques}, the tooltip
 * text lives client-side in {@code SimplySwordsTooltips}, and adding a weapon to one silently
 * leaves the other behind. That is exactly what happened when the ten 1.70 uniques were added -
 * all ten fought correctly and none of them said what they did.
 *
 * <h2>Why this reads source files</h2>
 *
 * <p>The tooltip table is in the client source set, which the test source set cannot see, so the
 * two lists cannot be compared as code from here. Reading them as text is the honest option
 * remaining: it checks the real tables rather than a third copy of the list maintained alongside
 * them, which would just be one more thing to forget.
 */
class UniqueTooltipCoverageTest {

    /** {@code u("emberblade", ...)} - one registration in the abilities table. */
    private static final Pattern REGISTRATION = Pattern.compile("\\bu\\(\"([a-z0-9_]+)\"");

    /** {@code case "emberblade" ->} - one entry in the tooltip table. */
    private static final Pattern TOOLTIP_CASE = Pattern.compile("case \"([a-z0-9_]+)\"");

    /**
     * The repository root, found by walking up from the working directory.
     *
     * <p>Gradle runs these from the version subproject, not the repo root, and the layout differs
     * between shards - so the root is located rather than assumed.
     */
    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src/main/java/com/crackedgames/craftics"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException(
            "could not find the repo root from " + Path.of("").toAbsolutePath());
    }

    private static Set<String> matches(Path file, Pattern pattern) throws IOException {
        return matchesIn(Files.readString(file, StandardCharsets.UTF_8), pattern);
    }

    private static Set<String> matchesIn(String src, Pattern pattern) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = pattern.matcher(src);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    /**
     * The body of one method, from its signature to the first line that closes it.
     *
     * <p>Scoping matters more than it looks. The tooltip file holds TWO tables of the same shape -
     * one keyed by weapon type, one by unique path - and the first version of this test searched
     * the whole file. Ten unique entries had been added to the wrong table, where nothing would
     * ever look them up, and the test passed anyway because the strings were technically present.
     * A guard that cannot tell the right table from the wrong one is worse than none: it reports
     * success over a broken feature.
     */
    private static String methodBody(Path file, String signature) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(signature)) { start = i; break; }
        }
        assertTrue(start >= 0, "could not find " + signature + " in " + file.getFileName());
        int end = -1;
        for (int i = start + 1; i < lines.size(); i++) {
            if (lines.get(i).equals("    }")) { end = i; break; }
        }
        assertTrue(end > start, "could not find the end of " + signature);
        return String.join(System.lineSeparator(), lines.subList(start, end + 1));
    }

    @Test
    @DisplayName("every unique with an ability also has a tooltip describing it")
    void everyRegisteredUniqueIsDescribed() throws IOException {
        Path root = repoRoot();
        Set<String> registered = matches(
            root.resolve("src/main/java/com/crackedgames/craftics/compat/simplyswords/SimplySwordsUniques.java"),
            REGISTRATION);
        // Scoped to uniqueLines: the same file holds a type-keyed table that must NOT count.
        Set<String> described = matchesIn(methodBody(
            root.resolve("src/client/java/com/crackedgames/craftics/client/SimplySwordsTooltips.java"),
            "private static String[] uniqueLines(String path)"), TOOLTIP_CASE);

        // Guard the guard: if either pattern stops matching, this test would pass vacuously and
        // quietly stop protecting anything.
        assertTrue(registered.size() > 40,
            "only found " + registered.size() + " registrations - has the table's shape changed?");
        assertTrue(described.size() > 40,
            "only found " + described.size() + " tooltip entries - has the table's shape changed?");

        List<String> undescribed = new ArrayList<>();
        for (String path : registered) {
            if (!described.contains(path)) undescribed.add(path);
        }
        assertTrue(undescribed.isEmpty(),
            "these uniques have a Craftics ability but no tooltip saying so: " + undescribed);
    }

    @Test
    @DisplayName("the ten weapons added in Simply Swords 1.70 are all covered")
    void the170AdditionsAreCovered() throws IOException {
        Set<String> described = matchesIn(methodBody(
            repoRoot().resolve("src/client/java/com/crackedgames/craftics/client/SimplySwordsTooltips.java"),
            "private static String[] uniqueLines(String path)"), TOOLTIP_CASE);
        List<String> missing = new ArrayList<>();
        for (String path : List.of("bloodwake", "wraithmaw", "soulstalker", "dreadwhisper",
                "gloampiercer", "riftmane", "stormscale", "ionbound_stormscale", "dawnquiver",
                "the_devourer")) {
            if (!described.contains(path)) missing.add(path);
        }
        assertTrue(missing.isEmpty(), "1.70 uniques with no tooltip: " + missing);
    }

    @Test
    @DisplayName("the level select block inherits a GUI transform, so it renders isometric")
    void levelSelectBlockIsNotDrawnFaceOn() throws IOException {
        // A Blockbench export has no parent, so it inherits none of minecraft:block/block -
        // including the display block holding the [30, 225, 0] rotation every block item is drawn
        // with. Without it the item renders with identity transforms: flat, edge-on, and wrong.
        Path model = repoRoot().resolve(
            "src/main/resources/assets/craftics/models/block/level_select_block.json");
        String json = Files.readString(model, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"parent\""),
            "the level select block model has no parent, so it will render face-on in the GUI");
        assertFalse(json.replaceAll("\\s+", "").contains("\"parent\":\"\""),
            "empty parent is the same problem with extra steps");
    }
}
