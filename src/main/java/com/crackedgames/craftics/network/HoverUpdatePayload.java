package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server: tells server which grid tile this player is hovering.
 * Sent at 4-5Hz (every ~200ms) when hovered tile changes.
 * Server relays to party members as TeammateHoverPayload.
 */
public record HoverUpdatePayload(int gridX, int gridZ) implements CustomPayload {

    public static final Id<HoverUpdatePayload> ID =
        new Id<>(Identifier.of("craftics", "hover_update"));

    public static final PacketCodec<RegistryByteBuf, HoverUpdatePayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> { buf.writeVarInt(payload.gridX); buf.writeVarInt(payload.gridZ); },
            buf -> new HoverUpdatePayload(buf.readVarInt(), buf.readVarInt())
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
