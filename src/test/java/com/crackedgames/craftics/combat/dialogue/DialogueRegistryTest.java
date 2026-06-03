package com.crackedgames.craftics.combat.dialogue;

import com.crackedgames.craftics.api.RegistrationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class DialogueRegistryTest {

    private static DialogueDefinition def(String id, String group) {
        return new DialogueDefinition(id, "minecraft:villager", group, List.of("Hi."), List.of());
    }

    @BeforeEach
    void clear() { DialogueRegistry.clearAllForTest(); }

    @Test
    void getByIdReturnsRegistered() {
        DialogueRegistry.register(def("craftics:a", "g"), RegistrationSource.CODE);
        assertEquals("craftics:a", DialogueRegistry.get("craftics:a").id());
        assertNull(DialogueRegistry.get("craftics:missing"));
    }

    @Test
    void pickFromGroupReturnsMember() {
        DialogueRegistry.register(def("craftics:a", "intro"), RegistrationSource.CODE);
        DialogueRegistry.register(def("craftics:b", "intro"), RegistrationSource.CODE);
        DialogueRegistry.register(def("craftics:c", "other"), RegistrationSource.CODE);
        DialogueDefinition picked = DialogueRegistry.pickFromGroup("intro", new Random(1));
        assertNotNull(picked);
        assertEquals("intro", picked.group());
    }

    @Test
    void pickFromGroupNullWhenNoMatch() {
        assertNull(DialogueRegistry.pickFromGroup("nope", new Random()));
    }

    @Test
    void clearDatapackEntriesKeepsCodeEntries() {
        DialogueRegistry.register(def("craftics:code", "g"), RegistrationSource.CODE);
        DialogueRegistry.register(def("craftics:dp", "g"), RegistrationSource.DATAPACK);
        DialogueRegistry.clearDatapackEntries();
        assertNotNull(DialogueRegistry.get("craftics:code"));
        assertNull(DialogueRegistry.get("craftics:dp"));
    }
}
