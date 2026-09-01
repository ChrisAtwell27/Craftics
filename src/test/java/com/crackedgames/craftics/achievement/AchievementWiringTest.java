package com.crackedgames.craftics.achievement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every achievement counter has something that writes to it.
 *
 * <p>An achievement breaks silently in a way almost nothing else does. The feat is defined, its
 * advancement file exists, it appears in the guide book, the grant condition reads a counter -
 * and if nothing ever calls the recorder, the counter stays at its initial value and the feat is
 * simply unearnable. Nothing errors, nothing logs, and the only symptom is a player eventually
 * asking why they never got it. Nine of them were in exactly that state at once.
 *
 * <p>So the invariant is checked structurally: every {@code record*} method anywhere in the mod
 * must be called from somewhere. Deliberately not just the tracker's own: several recorders are
 * reached through a thin forwarder on CombatManager, and checking only the tracker would call
 * those wired while the forwarder itself was dead code - the orphan simply moves up one level and
 * the test goes green. Requiring every link in the chain to have a caller closes that.
 *
 * <p>This cannot tell whether a call site is CORRECT, only that the wire exists at all. That is
 * the failure that actually happened.
 */
class AchievementWiringTest {

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("src/main/java/com/crackedgames/craftics"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not find the repo root from " + Path.of("").toAbsolutePath());
    }

    private static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String tree : new String[]{"src/main/java", "src/client/java"}) {
            Path root = repoRoot().resolve(tree);
            if (!Files.isDirectory(root)) continue;
            try (var walk = Files.walk(root)) {
                walk.filter(f -> f.toString().endsWith(".java")).forEach(out::add);
            }
        }
        return out;
    }

    @Test
    @DisplayName("every achievement recorder is called from somewhere")
    void noRecorderIsOrphaned() throws IOException {
        // Digits included on purpose: recordBossKilledBeforePhase2 reads as orphaned under a
        // letters-only pattern, which is a false alarm that costs real time to chase down.
        Pattern declaration = Pattern.compile("public void (record[A-Za-z0-9]+)");

        List<String> recorders = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        for (Path f : sources()) {
            String src = Files.readString(f, StandardCharsets.UTF_8);
            bodies.add(stripLineComments(src));
            Matcher m = declaration.matcher(src);
            while (m.find()) recorders.add(m.group(1));
        }
        assertTrue(recorders.size() > 20,
            "expected to find the recorders, found " + recorders.size());

        List<String> orphans = new ArrayList<>();
        for (String recorder : recorders) {
            // The dotted form, so a declaration never counts as its own caller.
            String call = "." + recorder + "(";
            if (bodies.stream().noneMatch(b -> b.contains(call))) orphans.add(recorder);
        }

        if (!orphans.isEmpty()) {
            fail("these achievement counters are never written to, so their feats can never be "
                + "earned:\n  " + String.join("\n  ", orphans)
                + "\nEither call them where the event happens, or delete the counter and its feat.");
        }
    }

    /**
     * Drop {@code //} comments, so commented-out code does not read as a live call site.
     *
     * <p>Block comments are deliberately KEPT: Stonecutter comments out the branches that belong
     * to other Minecraft versions, and a call that only exists on the 1.21.5 shard is still a real
     * call. Stripping those would fail this test for code that is perfectly well wired.
     */
    private static String stripLineComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        for (String line : src.split("\\n", -1)) {
            int c = line.indexOf("//");
            out.append(c >= 0 ? line.substring(0, c) : line).append('\n');
        }
        return out.toString();
    }
}
