package com.crackedgames.craftics.combat.dialogue;

/** One selectable choice at the end of a dialogue. {@code action} is a named key
 *  resolved by {@code DialogueActions} on the server.
 *
 *  <p>{@code tooltip} is optional hover text, newline-separated, for choices that need to say
 *  more than fits on a button - the enchanter's shortlist of what an enhancement might turn out
 *  to be, for one. Empty means no tooltip, which is every choice that predates it. */
public record DialogueChoice(String label, String action, String tooltip) {
    public DialogueChoice {
        if (tooltip == null) tooltip = "";
    }

    /** A choice with no hover text. */
    public DialogueChoice(String label, String action) {
        this(label, action, "");
    }
}
