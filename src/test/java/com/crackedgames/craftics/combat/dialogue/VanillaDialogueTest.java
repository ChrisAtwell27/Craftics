package com.crackedgames.craftics.combat.dialogue;

import com.crackedgames.craftics.api.VanillaDialogue;
import com.crackedgames.craftics.combat.TraderSystem;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class VanillaDialogueTest {

    @Test
    void everyTraderTypeHasAnIntroGroup() {
        DialogueRegistry.clearAllForTest();
        VanillaDialogue.register();
        for (TraderSystem.TraderType t : TraderSystem.TraderType.values()) {
            String group = "trader_intro_" + t.name().toLowerCase();
            assertNotNull(DialogueRegistry.pickFromGroup(group, new Random()),
                "missing intro dialogue group: " + group);
        }
    }

    @Test
    void traderDoneHasTwoChoices() {
        DialogueRegistry.clearAllForTest();
        VanillaDialogue.register();
        DialogueDefinition done = DialogueRegistry.get("craftics:trader_done");
        assertNotNull(done);
        assertEquals(2, done.choices().size());
        assertEquals("finish", done.choices().get(0).action());
        assertEquals("reopen_shop", done.choices().get(1).action());
        assertEquals("Yes, I'm done", done.choices().get(0).label());
        assertEquals("No, keep shopping", done.choices().get(1).label());
    }
}
