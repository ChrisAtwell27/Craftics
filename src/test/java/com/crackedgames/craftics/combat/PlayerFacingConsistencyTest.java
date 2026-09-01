package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Turning a player has to move all three angles.
 *
 * <p>A player carries three rotations: {@code yaw} (where they are looking), {@code headYaw} (where
 * the head model points) and {@code bodyYaw} (where the torso points). Setting two of the three
 * leaves the model physically bent - head facing the scene, torso still facing wherever they were
 * walking when the level ended - and it stays bent because nothing turns a standing player's body
 * back.
 *
 * <p>Six between-level event scenes did exactly that. They are not reachable from a test - they
 * need a live server, a world and a party - so the invariant is checked against the source instead.
 * That is weaker than exercising the code, and it is the reason the rule is written as "any place
 * that sets a player's yaw and headYaw together must set bodyYaw too" rather than as a list of the
 * six sites that were wrong: a seventh scene added later is caught by the same rule.
 */
class PlayerFacingConsistencyTest {

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("src/main/java/com/crackedgames/craftics"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not find the repo root from " + Path.of("").toAbsolutePath());
    }

    /** Java sources under src/main and src/client. */
    private static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String tree : new String[]{"src/main/java", "src/client/java"}) {
            Path root = repoRoot().resolve(tree);
            if (!Files.isDirectory(root)) continue;
            try (var walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
            }
        }
        assertTrue(out.size() > 50, "expected to find the source tree, found " + out.size() + " files");
        return out;
    }

    @Test
    @DisplayName("a player's yaw and headYaw are never set without bodyYaw")
    void turningAPlayerSetsAllThreeAngles() throws IOException {
        // Mobs are excluded on purpose: a mob legitimately turns its head alone to look at a
        // target, and does it constantly. This is about re-seating a PLAYER to face a scene.
        List<String> offenders = new ArrayList<>();

        for (Path file : sources()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).contains("setHeadYaw(")) continue;

                String receiver = receiverOf(lines.get(i));
                if (receiver == null || isMob(receiver)) continue;

                // The same statement, or the handful of lines around it, has to seat the body.
                int from = Math.max(0, i - 8);
                int to = Math.min(lines.size(), i + 9);
                String window = String.join("\n", lines.subList(from, to));
                if (window.contains(receiver + ".setBodyYaw(")) continue;

                offenders.add(file.getFileName() + ":" + (i + 1) + "  " + lines.get(i).trim());
            }
        }

        if (!offenders.isEmpty()) {
            fail("these set a player's head without seating the body, which renders the model bent:\n  "
                + String.join("\n  ", offenders));
        }
    }

    /** The variable a {@code .setHeadYaw(} call is made on, or null if the line is not a call. */
    private static String receiverOf(String line) {
        String s = line.trim();
        int call = s.indexOf(".setHeadYaw(");
        if (call < 0) return null;
        int start = call;
        while (start > 0 && (Character.isJavaIdentifierPart(s.charAt(start - 1)))) start--;
        String name = s.substring(start, call);
        return name.isEmpty() ? null : name;
    }

    private static boolean isMob(String receiver) {
        String r = receiver.toLowerCase(java.util.Locale.ROOT);
        return r.contains("mob") || r.contains("entity") || r.equals("e") || r.equals("m");
    }
}
