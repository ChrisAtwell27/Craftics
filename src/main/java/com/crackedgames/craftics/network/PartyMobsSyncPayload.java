package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: the entity UUIDs of the mobs in the receiving player's battle party,
 * encoded as a single {@code '|'}-separated string. The client renders an
 * "Active In Party" label above each matching mob.
 */
public record PartyMobsSyncPayload(String mobUuids) implements CustomPayload {

    public static final CustomPayload.Id<PartyMobsSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "party_mobs_sync"));

    public static final PacketCodec<RegistryByteBuf, PartyMobsSyncPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, PartyMobsSyncPayload::mobUuids,
            PartyMobsSyncPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
