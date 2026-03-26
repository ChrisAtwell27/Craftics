package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Syncs player progression stats to the client for display.
 * Sent on join and whenever stats change.
 */
public record PlayerStatsSyncPayload(int playerLevel, int unspentPoints,
                                       String statData, int emeralds) implements CustomPayload {

    public static final CustomPayload.Id<PlayerStatsSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "player_stats_sync"));

    public static final PacketCodec<RegistryByteBuf, PlayerStatsSyncPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, PlayerStatsSyncPayload::playerLevel,
            PacketCodecs.INTEGER, PlayerStatsSyncPayload::unspentPoints,
            PacketCodecs.STRING, PlayerStatsSyncPayload::statData,
            PacketCodecs.INTEGER, PlayerStatsSyncPayload::emeralds,
            PlayerStatsSyncPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
