package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: the player picked a dialogue choice (or a choiceless dialogue ended). */
public record DialogueChoicePayload(String action) implements CustomPayload {
    public static final CustomPayload.Id<DialogueChoicePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "dialogue_choice"));
    public static final PacketCodec<RegistryByteBuf, DialogueChoicePayload> CODEC =
        PacketCodec.tuple(PacketCodecs.STRING, DialogueChoicePayload::action, DialogueChoicePayload::new);
    /** Action value the client sends when the vanilla merchant screen is closed,
     *  so the server can show the "Are you done shopping?" dialogue. */
    public static final String ACTION_MERCHANT_CLOSED = "__merchant_closed__";
    /** Action value sent when a choiceless dialogue is clicked through to the end. */
    public static final String ACTION_DISMISS = "__dismiss__";
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
