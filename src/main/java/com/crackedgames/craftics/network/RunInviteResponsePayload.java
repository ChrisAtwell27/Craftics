package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: the player's answer to a {@link RunInvitePayload}. {@code accept} is 1 (join) or
 * 0 (decline / stay on the island). Encoded as a varInt to avoid the BOOL/BOOLEAN shard
 * split.
 */
public record RunInviteResponsePayload(int accept) implements CustomPayload {

    public static final CustomPayload.Id<RunInviteResponsePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "run_invite_response"));

    public static final PacketCodec<RegistryByteBuf, RunInviteResponsePayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, RunInviteResponsePayload::accept,
            RunInviteResponsePayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
