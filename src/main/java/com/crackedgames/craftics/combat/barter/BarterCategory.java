package com.crackedgames.craftics.combat.barter;

/**
 * A piglin barter "type" (analogous to {@code TraderSystem.TraderType}), but registry-driven
 * so addons can add their own. {@code id} is namespaced (e.g. {@code "craftics:warmonger"} or
 * {@code "mymod:warlord"}). {@code dialogueHint} is the short category word shown in the intro
 * dialogue (e.g. "weapons"), never the odds. {@code icon} is a chat-formatted glyph like the
 * trader types use.
 */
public record BarterCategory(String id, String displayName, String icon,
                             String dialogueHint, int minBiomeTier) {
    public BarterCategory {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("BarterCategory id required");
        if (displayName == null) displayName = id;
        if (icon == null) icon = "";
        if (dialogueHint == null) dialogueHint = "";
        if (minBiomeTier < 0) minBiomeTier = 0;
    }

    /** The local path of a namespaced id, e.g. "warmonger" from "craftics:warmonger". */
    public String localId() {
        int idx = id.indexOf(':');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }
}
