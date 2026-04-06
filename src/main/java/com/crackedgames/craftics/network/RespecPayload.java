package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client sends a respec request.
 * statDeltas is a colon-separated list of integers (one per stat ordinal).
 * Negative = refund, positive = allocate. Sum must be 0. Each refund costs 1 XP level.
 */
public record RespecPayload(String statDeltas) implements CustomPayload {

    public static final CustomPayload.Id<RespecPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "respec"));

    public static final PacketCodec<RegistryByteBuf, RespecPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, RespecPayload::statDeltas,
            RespecPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
