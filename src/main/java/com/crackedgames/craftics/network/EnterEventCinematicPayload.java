package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: turn on the non-combat event camera + input lock for this client. */
public record EnterEventCinematicPayload() implements CustomPayload {
    public static final CustomPayload.Id<EnterEventCinematicPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "enter_event_cinematic"));
    public static final PacketCodec<RegistryByteBuf, EnterEventCinematicPayload> CODEC =
        PacketCodec.unit(new EnterEventCinematicPayload());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
