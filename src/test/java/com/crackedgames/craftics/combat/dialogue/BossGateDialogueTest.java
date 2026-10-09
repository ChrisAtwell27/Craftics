package com.crackedgames.craftics.combat.dialogue;

import com.crackedgames.craftics.network.DialogueChoicePayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The boss gate's dialogue: who gets the two choices, what they are told, and which actions
 * count as an answer. What each answer then does is the combat manager's, and needs a server.
 */
class BossGateDialogueTest {

    private static final List<String> REASON = List.of("Nobody in your party is carrying a key.");

    @Test
    void theDeciderGetsTheTwoChoicesAndNoOthers() {
        DialogueDefinition def = BossGateDialogue.forDecider(REASON);

        assertEquals(2, def.choices().size());
        assertEquals("Go home", def.choices().get(0).label());
        assertEquals(BossGateDialogue.ACTION_GO_HOME, def.choices().get(0).action());
        assertEquals("Start again from level 1", def.choices().get(1).label());
        assertEquals(BossGateDialogue.ACTION_RESTART, def.choices().get(1).action());
    }

    @Test
    void everyoneElseGetsNothingToPress() {
        DialogueDefinition def = BossGateDialogue.forWatcher(REASON, "Alex");

        assertTrue(def.choices().isEmpty());
        assertEquals("Alex is choosing for the party.", def.lines().get(def.lines().size() - 1));
    }

    @Test
    void theReasonComesFirst_thenWhatEachChoiceDoes() {
        List<String> lines = BossGateDialogue.forDecider(REASON).lines();

        assertEquals(REASON.get(0), lines.get(0));
        assertEquals(BossGateDialogue.CHOICE_LINES, lines.subList(1, lines.size()));
        // Both ways out are spelled out, in words that match the two labels.
        String told = String.join(" ", BossGateDialogue.CHOICE_LINES);
        assertTrue(told.contains("Going home ends this run"), told);
        assertTrue(told.contains("Starting again from level 1 keeps everything you carry"), told);
    }

    @Test
    void aWatcherIsToldTheSameThingAsTheDecider() {
        List<String> decider = BossGateDialogue.forDecider(REASON).lines();
        List<String> watcher = BossGateDialogue.forWatcher(REASON, "Alex").lines();

        assertEquals(decider, watcher.subList(0, decider.size()));
        assertEquals(decider.size() + 1, watcher.size());
    }

    @Test
    void itIsNarrated() {
        assertEquals("", BossGateDialogue.forDecider(REASON).speaker());
        assertEquals("", BossGateDialogue.forWatcher(REASON, "Alex").speaker());
    }

    @Test
    void onlyTheTwoChoicesAreAnswers() {
        assertEquals(BossGateDialogue.Answer.GO_HOME,
            BossGateDialogue.answerOf(BossGateDialogue.ACTION_GO_HOME));
        assertEquals(BossGateDialogue.Answer.RESTART,
            BossGateDialogue.answerOf(BossGateDialogue.ACTION_RESTART));
        // Closing the box is what an ordinary dialogue reports as a dismissal. Here it is
        // not a decision, and neither is anything another dialogue's buttons might send.
        assertEquals(BossGateDialogue.Answer.NONE,
            BossGateDialogue.answerOf(DialogueChoicePayload.ACTION_DISMISS));
        assertEquals(BossGateDialogue.Answer.NONE, BossGateDialogue.answerOf("finish"));
        assertEquals(BossGateDialogue.Answer.NONE, BossGateDialogue.answerOf(""));
        assertEquals(BossGateDialogue.Answer.NONE, BossGateDialogue.answerOf(null));
    }

    @Test
    void everyChoiceTheDeciderIsShown_isOneTheGateTakes() {
        for (DialogueChoice choice : BossGateDialogue.forDecider(REASON).choices()) {
            assertNotEquals(BossGateDialogue.Answer.NONE, BossGateDialogue.answerOf(choice.action()),
                choice.label());
        }
    }

    @Test
    void aGateWithNothingToSay_stillExplainsTheChoices() {
        assertEquals(BossGateDialogue.CHOICE_LINES, BossGateDialogue.forDecider(List.of()).lines());
        assertEquals(BossGateDialogue.CHOICE_LINES, BossGateDialogue.forDecider(null).lines());
    }

    @Test
    void aWatcherWithNoNameToShow_isStillToldSomebodyIsChoosing() {
        for (String name : new String[]{null, "", "  "}) {
            List<String> lines = BossGateDialogue.forWatcher(REASON, name).lines();
            assertEquals("Your party leader is choosing for the party.", lines.get(lines.size() - 1));
        }
    }

    @Test
    void theEnginesOwnLinesAreShortAndPlain() {
        // The narrator box draws one unwrapped line. 16 characters is the longest player name.
        List<String> lines = BossGateDialogue.forWatcher(List.of(), "ABCDEFGHIJKLMNOP").lines();
        for (String line : lines) {
            assertTrue(line.length() <= 64, line);
            // 0x2014 and 0x2013 are the em and en dash.
            assertTrue(line.chars().noneMatch(c -> c == 0x2014 || c == 0x2013), line);
        }
    }
}
