package com.crackedgames.craftics.combat.dialogue;

import com.crackedgames.craftics.api.VanillaDialogue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VanillaDialogueShrineTest {

    @Test
    void shrineIntroIsNarratorWithFourChoices() {
        DialogueRegistry.clearAllForTest();
        VanillaDialogue.register();
        DialogueDefinition d = DialogueRegistry.get("craftics:shrine_intro");
        assertNotNull(d);
        assertEquals("", d.speaker(), "shrine intro is narrator (empty speaker)");
        assertEquals(4, d.choices().size());
        assertEquals("shrine:small",  d.choices().get(0).action());
        assertEquals("shrine:medium", d.choices().get(1).action());
        assertEquals("shrine:large",  d.choices().get(2).action());
        assertEquals("shrine:leave",  d.choices().get(3).action());
    }
}
