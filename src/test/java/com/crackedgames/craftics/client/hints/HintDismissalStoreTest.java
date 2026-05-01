package com.crackedgames.craftics.client.hints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HintDismissalStoreTest {

    @Test
    void load_returnsEmpty_whenFileMissing(@TempDir Path tmp) {
        HintDismissalStore store = new HintDismissalStore(tmp.resolve("missing.json"));
        assertTrue(store.dismissed().isEmpty());
    }

    @Test
    void load_returnsEmpty_whenFileCorrupt(@TempDir Path tmp) throws IOException {
        Path p = tmp.resolve("corrupt.json");
        Files.writeString(p, "this is not json {{{");
        HintDismissalStore store = new HintDismissalStore(p);
        assertTrue(store.dismissed().isEmpty());
    }

    @Test
    void markDismissed_persistsAcrossInstances(@TempDir Path tmp) {
        Path p = tmp.resolve("hints.json");
        HintDismissalStore a = new HintDismissalStore(p);
        a.markDismissed("combat.first_combat");
        a.markDismissed("hub.level_select_arrow");

        HintDismissalStore b = new HintDismissalStore(p);
        assertEquals(Set.of("combat.first_combat", "hub.level_select_arrow"), b.dismissed());
    }

    @Test
    void markDismissed_isIdempotent(@TempDir Path tmp) {
        Path p = tmp.resolve("hints.json");
        HintDismissalStore a = new HintDismissalStore(p);
        a.markDismissed("x");
        a.markDismissed("x");
        a.markDismissed("x");
        assertEquals(Set.of("x"), a.dismissed());
    }

    @Test
    void clear_emptiesStoreAndDeletesFile(@TempDir Path tmp) {
        Path p = tmp.resolve("hints.json");
        HintDismissalStore a = new HintDismissalStore(p);
        a.markDismissed("x");
        a.clear();
        assertTrue(a.dismissed().isEmpty());
        assertFalse(Files.exists(p));
    }
}
