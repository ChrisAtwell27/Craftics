package com.crackedgames.craftics.combat.dialogue;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DialogueDefinitionTest {

    @Test
    void storesFieldsAndChoices() {
        DialogueDefinition d = new DialogueDefinition(
            "craftics:test", "minecraft:villager", "greetings",
            List.of("Hello.", "Welcome."),
            List.of(new DialogueChoice("Trade", "open_trade"),
                    new DialogueChoice("Bye", "close")));
        assertEquals("craftics:test", d.id());
        assertEquals("minecraft:villager", d.speaker());
        assertEquals("greetings", d.group());
        assertEquals(2, d.lines().size());
        assertEquals("open_trade", d.choices().get(0).action());
    }

    @Test
    void choicesDefaultsToEmptyNotNull() {
        DialogueDefinition d = new DialogueDefinition(
            "craftics:x", "minecraft:villager", "", List.of("Hi."), null);
        assertNotNull(d.choices());
        assertTrue(d.choices().isEmpty());
    }
}
