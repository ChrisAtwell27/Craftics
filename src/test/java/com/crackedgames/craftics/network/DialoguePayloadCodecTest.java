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
