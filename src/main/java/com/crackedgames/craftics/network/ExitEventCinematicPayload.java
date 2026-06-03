package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: turn off the non-combat event camera + input lock for this client. */
public record ExitEventCinematicPayload() implements CustomPayload {
    public static final CustomPayload.Id<ExitEventCinematicPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "exit_event_cinematic"));
    public static final PacketCodec<RegistryByteBuf, ExitEventCinematicPayload> CODEC =
        PacketCodec.unit(new ExitEventCinematicPayload());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
