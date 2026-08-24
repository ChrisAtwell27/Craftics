package com.crackedgames.craftics.network;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DialoguePayloadCodecTest {

    @Test
    void encodeDecodeLines() {
        List<String> lines = List.of("Hello, friend.", "Care to trade?");
        String encoded = DialoguePayload.encodeLines(lines);
        assertEquals(lines, DialoguePayload.decodeLines(encoded));
    }

    @Test
    void encodeDecodeChoicesWithTooltips() {
        List<String> labels = List.of("Iron Sword", "Bow");
        List<String> actions = List.of("enchanter:pick:3", "enchanter:pick:5");
        List<String> tooltips = List.of("Sharpness III" + DialoguePayload.TOOLTIP_LINE + "Dull", "");
        String encoded = DialoguePayload.encodeChoices(labels, actions, tooltips);
        assertEquals(labels, DialoguePayload.decodeChoiceLabels(encoded));
        assertEquals(actions, DialoguePayload.decodeChoiceActions(encoded));
        assertEquals(tooltips, DialoguePayload.decodeChoiceTooltips(encoded));
    }

    @Test
    void choicesWithoutTooltipsDecodeToEmptyStrings() {
        // The two-argument form is what every dialogue but the enchanter uses; it must keep
        // producing something the tooltip-aware decoder reads as "no tooltip".
        String encoded = DialoguePayload.encodeChoices(List.of("Yes"), List.of("finish"));
        assertEquals(List.of(""), DialoguePayload.decodeChoiceTooltips(encoded));
    }

    @Test
    void encodeDecodeChoices() {
        List<String> labels = List.of("Yes", "No");
        List<String> actions = List.of("finish", "reopen_shop");
        String encoded = DialoguePayload.encodeChoices(labels, actions);
        assertEquals(labels, DialoguePayload.decodeChoiceLabels(encoded));
        assertEquals(actions, DialoguePayload.decodeChoiceActions(encoded));
    }

    @Test
    void emptyLinesRoundTrip() {
        String encoded = DialoguePayload.encodeLines(java.util.List.of());
        assertTrue(DialoguePayload.decodeLines(encoded).isEmpty());
    }

    @Test
    void singleLineRoundTrip() {
        java.util.List<String> lines = java.util.List.of("Hello.");
        assertEquals(lines, DialoguePayload.decodeLines(DialoguePayload.encodeLines(lines)));
    }

    @Test
    void emptyChoicesRoundTrip() {
        String encoded = DialoguePayload.encodeChoices(List.of(), List.of());
        assertTrue(DialoguePayload.decodeChoiceLabels(encoded).isEmpty());
        assertTrue(DialoguePayload.decodeChoiceActions(encoded).isEmpty());
    }
}
