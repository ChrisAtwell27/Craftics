package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: open a dialogue on the client. {@code lines} and {@code choices} are encoded
 * as delimited strings (this repo uses no list packet codecs). {@code speaker} is an
 * entity-type id for the portrait + voice.
 */
public record DialoguePayload(String speaker, String lines, String choices) implements CustomPayload {

    /** U+001F unit separator: between lines, and between a choice's label and action. */
    private static final String UNIT = "\u001F";
    /** U+001E record separator: between choice entries. */
    private static final String RECORD = "\u001E";

    public static final CustomPayload.Id<DialoguePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "dialogue"));

    public static final PacketCodec<RegistryByteBuf, DialoguePayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, DialoguePayload::speaker,
            PacketCodecs.STRING, DialoguePayload::lines,
            PacketCodecs.STRING, DialoguePayload::choices,
            DialoguePayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    // Lines use UNIT as a flat delimiter; choices use UNIT within each RECORD entry (label<UNIT>action).
    public static String encodeLines(List<String> lines) { return String.join(UNIT, lines); }

    public static List<String> decodeLines(String encoded) {
        List<String> out = new ArrayList<>();
        if (encoded.isEmpty()) return out;
        for (String s : encoded.split(UNIT, -1)) out.add(s);
        return out;
    }

    public static String encodeChoices(List<String> labels, List<String> actions) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) parts.add(labels.get(i) + UNIT + actions.get(i));
        return String.join(RECORD, parts);
    }

    public static List<String> decodeChoiceLabels(String encoded) { return decodeChoicePart(encoded, 0); }
    public static List<String> decodeChoiceActions(String encoded) { return decodeChoicePart(encoded, 1); }

    private static List<String> decodeChoicePart(String encoded, int index) {
        List<String> out = new ArrayList<>();
        if (encoded.isEmpty()) return out;
        for (String rec : encoded.split(RECORD, -1)) {
            String[] kv = rec.split(UNIT, -1);
            out.add(kv.length > index ? kv[index] : "");
        }
        return out;
    }
}
