package com.crackedgames.craftics.combat.dialogue;

import com.crackedgames.craftics.api.VanillaDialogue;
import com.crackedgames.craftics.combat.TraderSystem;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class VanillaDialogueTest {

    /**
     * Every VANILLA trader needs an intro line. Addon traders are allowed to have none - the
     * trader event tolerates a missing group - so this only covers the ones we ship.
     */
    @Test
    void everyTraderTypeHasAnIntroGroup() {
        DialogueRegistry.clearAllForTest();
        VanillaDialogue.register();
        com.crackedgames.craftics.api.registry.TraderCategoryRegistry.clearAllForTest();
        com.crackedgames.craftics.combat.VanillaTraderContent.register();
        for (var t : com.crackedgames.craftics.api.registry.TraderCategoryRegistry.all()) {
            String group = "trader_intro_" + t.localId();
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
