package com.crackedgames.craftics.data;

import com.crackedgames.craftics.combat.dialogue.DialogueDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialogueJsonLoaderTest {

    private static JsonObject json(String s) { return new Gson().fromJson(s, JsonObject.class); }
    private final DialogueJsonLoader loader = new DialogueJsonLoader();
    private final Identifier fid = Identifier.of("craftics", "dialogue/x");

    @Test
    void parsesValidDialogue() {
        DialogueDefinition d = loader.parseForTest(fid, json("""
            {"id":"craftics:trader_intro_armorer","speaker":"minecraft:wandering_trader",
             "group":"trader_intro_armorer","lines":["Sturdy gear here.","Take a look?"],
             "choices":[{"label":"Let's trade","action":"open_trade"},
                        {"label":"Maybe later","action":"close"}]}"""));
        assertNotNull(d);
        assertEquals("craftics:trader_intro_armorer", d.id());
        assertEquals(2, d.lines().size());
        assertEquals("open_trade", d.choices().get(0).action());
    }

    @Test
    void skipsWhenMissingId() {
        assertNull(loader.parseForTest(fid, json(
            "{\"speaker\":\"minecraft:villager\",\"lines\":[\"Hi\"]}")));
    }

    @Test
    void skipsWhenMissingSpeaker() {
        assertNull(loader.parseForTest(fid, json(
            "{\"id\":\"craftics:x\",\"lines\":[\"Hi\"]}")));
    }

    @Test
    void skipsWhenNoLines() {
        assertNull(loader.parseForTest(fid, json(
            "{\"id\":\"craftics:x\",\"speaker\":\"minecraft:villager\",\"lines\":[]}")));
    }

    @Test
    void malformedChoiceIsSkipped() {
        DialogueDefinition d = loader.parseForTest(fid, json("""
            {"id":"craftics:x","speaker":"minecraft:villager","lines":["Hi"],
             "choices":[{"label":"ok","action":"close"},{"label":"broken"}]}"""));
        assertNotNull(d);
        assertEquals(1, d.choices().size());
        assertEquals("close", d.choices().get(0).action());
    }

    @Test
    void choicesOptional() {
        DialogueDefinition d = loader.parseForTest(fid, json(
            "{\"id\":\"craftics:x\",\"speaker\":\"minecraft:villager\",\"lines\":[\"Hi\"]}"));
        assertNotNull(d);
        assertTrue(d.choices().isEmpty());
    }
}
