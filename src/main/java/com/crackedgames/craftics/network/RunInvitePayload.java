package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: a party member started a biome run and is inviting this player to join. The
 * client opens a Yes/No popup ({@code RunJoinScreen}). Replying sends a
 * {@link RunInviteResponsePayload}; if the player doesn't reply within
 * {@code timeoutSeconds} the screen auto-declines (and the server times them out too),
 * so one AFK player can't hold the run back.
 *
 * <p>Encoded with only String / varInt so there's no {@code BOOL} vs {@code BOOLEAN}
 * shard split.
 */
public record RunInvitePayload(String biomeName, String starterName, int timeoutSeconds)
        implements CustomPayload {

    public static final CustomPayload.Id<RunInvitePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "run_invite"));

    public static final PacketCodec<RegistryByteBuf, RunInvitePayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, RunInvitePayload::biomeName,
            PacketCodecs.STRING, RunInvitePayload::starterName,
            PacketCodecs.INTEGER, RunInvitePayload::timeoutSeconds,
            RunInvitePayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
