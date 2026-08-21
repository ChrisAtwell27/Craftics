package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server-to-client: a ping one party member placed, relayed to the party.
 *
 * <p>Sent to the pinger too, not just to everyone else. Two reasons: the marker they see is then
 * the same marker their teammates see, drawn by the same code from the same packet; and it is
 * honest feedback - if the rate limiter dropped the ping, nothing appears, rather than the
 * sender watching a confident marker nobody else got.
 */
public record TeammatePingPayload(UUID playerUuid, String playerName,
                                  int gridX, int gridZ, int type) implements CustomPayload {

    public static final Id<TeammatePingPayload> ID =
        new Id<>(Identifier.of("craftics", "teammate_ping"));

    public static final PacketCodec<RegistryByteBuf, TeammatePingPayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> {
                buf.writeUuid(payload.playerUuid);
                buf.writeString(payload.playerName);
                buf.writeVarInt(payload.gridX);
                buf.writeVarInt(payload.gridZ);
                buf.writeVarInt(payload.type);
            },
            buf -> new TeammatePingPayload(buf.readUuid(), buf.readString(),
                                           buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
