package com.crackedgames.craftics.combat.dialogue;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialogueActionsTest {

    @Test
    void knownActionsResolve() {
        assertEquals(DialogueActions.Outcome.OPEN_TRADE, DialogueActions.resolve("open_trade"));
        assertEquals(DialogueActions.Outcome.REOPEN_SHOP, DialogueActions.resolve("reopen_shop"));
        assertEquals(DialogueActions.Outcome.FINISH, DialogueActions.resolve("finish"));
        assertEquals(DialogueActions.Outcome.CLOSE, DialogueActions.resolve("close"));
    }

    @Test
    void unknownActionDegradesToClose() {
        assertEquals(DialogueActions.Outcome.CLOSE, DialogueActions.resolve("banana"));
        assertEquals(DialogueActions.Outcome.CLOSE, DialogueActions.resolve(""));
        assertEquals(DialogueActions.Outcome.CLOSE, DialogueActions.resolve(null));
    }
}
