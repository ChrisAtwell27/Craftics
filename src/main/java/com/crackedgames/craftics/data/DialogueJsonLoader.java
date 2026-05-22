package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.dialogue.DialogueChoice;
import com.crackedgames.craftics.combat.dialogue.DialogueDefinition;
import com.crackedgames.craftics.combat.dialogue.DialogueRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads dialogue definitions from {@code data/<namespace>/craftics/dialogue/*.json}
 * into {@link DialogueRegistry}. Required: {@code id}, {@code speaker}, non-empty
 * {@code lines}. Optional: {@code group}, {@code choices} (each {@code label}+{@code action}).
 */
public final class DialogueJsonLoader extends CrafticsDataLoader<DialogueDefinition> {

    public DialogueJsonLoader() { super("craftics/dialogue", "dialogue"); }

    /** Exposed for unit tests — {@link #parse} is otherwise protected. */
    public DialogueDefinition parseForTest(Identifier fileId, JsonObject json) {
        return parse(fileId, json);
    }

    @Override
    protected DialogueDefinition parse(Identifier fileId, JsonObject json) {
        if (!json.has("id")) {
            CrafticsMod.LOGGER.warn("Dialogue JSON {} missing 'id' — skipping", fileId);
            return null;
        }
        if (!json.has("speaker")) {
            CrafticsMod.LOGGER.warn("Dialogue JSON {} missing 'speaker' — skipping", fileId);
            return null;
        }
        if (!json.has("lines")) {
            CrafticsMod.LOGGER.warn("Dialogue JSON {} missing 'lines' — skipping", fileId);
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("lines")) lines.add(e.getAsString());
        if (lines.isEmpty()) {
            CrafticsMod.LOGGER.warn("Dialogue JSON {} has no 'lines' — skipping", fileId);
            return null;
        }
        String group = json.has("group") ? json.get("group").getAsString() : "";

        List<DialogueChoice> choices = new ArrayList<>();
        if (json.has("choices")) {
            JsonArray arr = json.getAsJsonArray("choices");
            for (JsonElement e : arr) {
                JsonObject c = e.getAsJsonObject();
                if (!c.has("label") || !c.has("action")) {
                    CrafticsMod.LOGGER.warn("Dialogue {} has a choice missing label/action — skipping that choice", fileId);
                    continue;
                }
                choices.add(new DialogueChoice(c.get("label").getAsString(), c.get("action").getAsString()));
            }
        }
        return new DialogueDefinition(json.get("id").getAsString(),
            json.get("speaker").getAsString(), group, lines, choices);
    }

    @Override
    protected void register(DialogueDefinition parsed, RegistrationSource source) {
        DialogueRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        DialogueRegistry.clearDatapackEntries();
    }
}
