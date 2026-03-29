package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Player's choice from an event room screen.
 * choiceIndex: 0+ = specific choice, -1 = skip/walk away
 */
public record EventChoicePayload(int choiceIndex) implements CustomPayload {

    public static final CustomPayload.Id<EventChoicePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "event_choice"));

    public static final PacketCodec<RegistryByteBuf, EventChoicePayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, EventChoicePayload::choiceIndex,
            EventChoicePayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
