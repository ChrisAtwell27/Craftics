package com.crackedgames.craftics.combat.sherd;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every sherd moves the arena when it is cast.
 *
 * <p>A sherd without staging still works - it falls back to the streak and puff they all used
 * to share - which is exactly why one would go unnoticed: the new sherd casts, hits, and looks
 * like a relic next to the other twenty-three. This reads the registry as text, because the
 * spells themselves are built from items that only exist with the game running.
 */
class SherdStagingCoverageTest {

    private static final Pattern SPELL = Pattern.compile("register\\(SherdSpell\\.of\\(Items\\.\\w+, \"([^\"]+)\"\\)");
    private static final Pattern STAGED = Pattern.compile("\\.choreography\\(SherdStaging::(\\w+)\\)");

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src/main/java/com/crackedgames/craftics"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not find the repo root from " + Path.of("").toAbsolutePath());
    }

    private static String read(String name) throws IOException {
        return Files.readString(repoRoot().resolve(
            "src/main/java/com/crackedgames/craftics/combat/sherd/" + name), StandardCharsets.UTF_8);
    }

    /** Each spell's name with the source of its own registration block. */
    private static List<String[]> spells() throws IOException {
        String registry = read("SherdRegistry.java");
        List<String[]> spells = new ArrayList<>();
        Matcher m = SPELL.matcher(registry);
        int lastStart = -1;
        String lastName = null;
        while (m.find()) {
            if (lastName != null) spells.add(new String[]{lastName, registry.substring(lastStart, m.start())});
            lastStart = m.start();
            lastName = m.group(1);
        }
        if (lastName != null) spells.add(new String[]{lastName, registry.substring(lastStart)});
        return spells;
    }

    @Test
    void everySherdHasStaging() throws IOException {
        List<String[]> spells = spells();
        assertTrue(spells.size() >= 23, "expected every sherd, found " + spells.size());
        for (String[] spell : spells) {
            assertTrue(STAGED.matcher(spell[1]).find(),
                spell[0] + " has no choreography: add one in SherdStaging and attach it here");
        }
    }

    @Test
    void everyStagingASherdNamesExists() throws IOException {
        String staging = read("SherdStaging.java");
        Matcher m = STAGED.matcher(read("SherdRegistry.java"));
        int count = 0;
        while (m.find()) {
            count++;
            assertTrue(staging.contains("static void " + m.group(1) + "(SpellStage stage)"),
                "SherdStaging has no " + m.group(1));
        }
        assertTrue(count >= 23);
    }

    @Test
    void noStagingIsLeftUnused() throws IOException {
        // A choreography nobody plays is one somebody wrote and forgot to attach.
        String registry = read("SherdRegistry.java");
        Matcher m = Pattern.compile("    static void (\\w+)\\(SpellStage stage\\)").matcher(read("SherdStaging.java"));
        while (m.find()) {
            assertTrue(registry.contains("SherdStaging::" + m.group(1) + ")"),
                m.group(1) + " is written but no sherd uses it");
        }
    }
}
