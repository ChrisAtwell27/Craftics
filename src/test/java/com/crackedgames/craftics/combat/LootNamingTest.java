package com.crackedgames.craftics.combat;

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
 * Loot is named before it is handed over, never after.
 *
 * <p>{@code LootDelivery.deliver} passes the stack to the inventory, and inserting a stack empties
 * it in place. Reading its name afterwards therefore describes an empty stack, which Minecraft
 * calls "Air" - so the chat line announces "Caught: Air!" while the correct item sits in the
 * player's bag. Nothing throws and the loot itself is fine, so the only symptom is the message.
 *
 * <p>It happened in two separate places, which is what makes it worth a structural check rather
 * than a fix in each. Passing a {@code .copy()} to deliver is fine and stays fine - the original
 * is untouched - so only a bare variable handed straight over is flagged.
 */
class LootNamingTest {

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
        assertTrue(out.size() > 50, "expected the source tree, found " + out.size() + " files");
        return out;
    }

    @Test
    @DisplayName("a delivered stack is never named afterwards")
    void deliveredLootIsNamedFirst() throws IOException {
        // The second argument only when it is a plain identifier: a .copy() or a freshly built
        // stack leaves the caller's variable alone, and naming that afterwards is correct.
        Pattern delivery = Pattern.compile(
            "LootDelivery\\.deliver\\([^,]+,\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)");

        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = delivery.matcher(lines.get(i));
                if (!m.find()) continue;
                String var = m.group(1);

                int to = Math.min(lines.size(), i + 4);
                for (int j = i + 1; j < to; j++) {
                    if (lines.get(j).contains(var + ".getName(")) {
                        offenders.add(file.getFileName() + ":" + (j + 1) + "  " + lines.get(j).trim());
                        break;
                    }
                }
            }
        }

        if (!offenders.isEmpty()) {
            fail("these read a stack's name AFTER delivering it, which reports \"Air\" because "
                + "delivery empties the stack:\n  " + String.join("\n  ", offenders)
                + "\nTake the name before the deliver() call.");
        }
    }
}
