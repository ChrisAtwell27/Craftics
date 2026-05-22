package com.crackedgames.craftics.combat.dialogue;

import java.util.List;

/**
 * A single dialogue: ordered text {@code lines} the player clicks through, then an
 * optional list of {@code choices}. {@code speaker} is an entity-type id used for the
 * portrait and per-word voice sound. {@code group} is an optional tag for variant
 * selection ({@code DialogueRegistry.pickFromGroup}).
 */
public record DialogueDefinition(String id, String speaker, String group,
                                 List<String> lines, List<DialogueChoice> choices) {
    public DialogueDefinition {
        if (group == null) group = "";
        if (choices == null) choices = List.of();
        if (lines == null) lines = List.of();
    }
}
