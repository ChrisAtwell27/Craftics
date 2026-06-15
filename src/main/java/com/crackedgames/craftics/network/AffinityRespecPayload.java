package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client sends an affinity respec request.
 * affinityDeltas is a colon-separated list of integers (one per affinity ordinal).
 * Negative = refund, positive = re-allocate. The sum must be 0 - affinity points
 * are redistributed, never gained or lost. Each refunded point costs 1 XP level.
 */
public record AffinityRespecPayload(String affinityDeltas) implements CustomPayload {

    public static final CustomPayload.Id<AffinityRespecPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "affinity_respec"));

    public static final PacketCodec<RegistryByteBuf, AffinityRespecPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, AffinityRespecPayload::affinityDeltas,
            AffinityRespecPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
