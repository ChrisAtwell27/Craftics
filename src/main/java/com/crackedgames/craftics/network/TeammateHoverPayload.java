package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server-to-client: relays a party member's hover position to other members.
 */
public record TeammateHoverPayload(UUID playerUuid, String playerName,
                                    int gridX, int gridZ) implements CustomPayload {

    public static final Id<TeammateHoverPayload> ID =
        new Id<>(Identifier.of("craftics", "teammate_hover"));

    public static final PacketCodec<RegistryByteBuf, TeammateHoverPayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> {
                buf.writeUuid(payload.playerUuid);
                buf.writeString(payload.playerName);
                buf.writeVarInt(payload.gridX);
                buf.writeVarInt(payload.gridZ);
            },
            buf -> new TeammateHoverPayload(buf.readUuid(), buf.readString(),
                                            buf.readVarInt(), buf.readVarInt())
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
