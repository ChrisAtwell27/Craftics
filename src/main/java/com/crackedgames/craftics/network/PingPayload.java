package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server: "I am pinging this tile, with this meaning".
 *
 * <p>The client sends only what it cannot be wrong about - which tile, which of the six
 * {@link com.crackedgames.craftics.core.PingType} entries. Everything else (who sent it, who
 * should see it, whether it is allowed at all) is decided server-side, because a client that
 * could name its own sender or its own audience is a client that could impersonate a teammate
 * or ping the whole server.
 */
public record PingPayload(int gridX, int gridZ, int type) implements CustomPayload {

    public static final CustomPayload.Id<PingPayload> ID =
        new CustomPayload.Id<>(Identifier.of("craftics", "ping"));

    public static final PacketCodec<RegistryByteBuf, PingPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, PingPayload::gridX,
            PacketCodecs.INTEGER, PingPayload::gridZ,
            PacketCodecs.INTEGER, PingPayload::type,
            PingPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
