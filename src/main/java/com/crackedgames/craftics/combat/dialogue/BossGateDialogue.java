package com.crackedgames.craftics.combat.dialogue;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialogue a refused boss gate shows, and the two answers it takes.
 *
 * <p>A {@link com.crackedgames.craftics.api.BossGate} supplies the reason. Everything after
 * that is the same for every gate and lives here: what each way out does, who is choosing, and
 * the action keys the choices send back. Nothing in this class touches a live server, so the
 * wording and the keys can be tested.
 */
public final class BossGateDialogue {

    private BossGateDialogue() {}

    public static final String ID = "craftics:boss_gate";

    public static final String ACTION_GO_HOME = "boss_gate:home";
    public static final String ACTION_RESTART = "boss_gate:restart";

    public static final String LABEL_GO_HOME = "Go home";
    public static final String LABEL_RESTART = "Start again from level 1";

    /** What the two choices do. Follows every gate's own lines, so no gate has to say it. */
    public static final List<String> CHOICE_LINES = List.of(
        "Going home ends this run.",
        "Starting again from level 1 keeps everything you carry.");

    /** What a dialogue action means to a waiting gate. */
    public enum Answer { GO_HOME, RESTART, NONE }

    /**
     * Read a dialogue action as an answer. Anything that is not one of the two choices is
     * {@link Answer#NONE}, the dismiss sentinel included: closing the box is not a decision.
     */
    public static Answer answerOf(String action) {
        if (ACTION_GO_HOME.equals(action)) return Answer.GO_HOME;
        if (ACTION_RESTART.equals(action)) return Answer.RESTART;
        return Answer.NONE;
    }

    /** For the player who decides: the reason, what each choice does, and the two choices. */
    public static DialogueDefinition forDecider(List<String> reason) {
        return new DialogueDefinition(ID, "", "boss_gate", lines(reason, null, false),
            List.of(new DialogueChoice(LABEL_GO_HOME, ACTION_GO_HOME),
                    new DialogueChoice(LABEL_RESTART, ACTION_RESTART)));
    }

    /** For everyone else: the same text, then who is choosing. Nothing to pick. */
    public static DialogueDefinition forWatcher(List<String> reason, String deciderName) {
        return new DialogueDefinition(ID, "", "boss_gate", lines(reason, deciderName, true), List.of());
    }

    private static List<String> lines(List<String> reason, String deciderName, boolean watching) {
        List<String> out = new ArrayList<>();
        if (reason != null) out.addAll(reason);
        out.addAll(CHOICE_LINES);
        if (watching) {
            boolean named = deciderName != null && !deciderName.isBlank();
            out.add((named ? deciderName : "Your party leader") + " is choosing for the party.");
        }
        return List.copyOf(out);
    }
}
