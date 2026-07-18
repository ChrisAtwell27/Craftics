package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: the infinite-mode class the player picked.
 *
 * @param affinityOrdinal ordinal into {@code PlayerProgression.Affinity}, or -1 for
 *                        "no class" (skip - the player keeps just the log bootstrap)
 */
public record InfiniteClassPickPayload(int affinityOrdinal) implements CustomPayload {

    /** Sentinel ordinal for declining a class. */
    public static final int SKIP = -1;

    public static final CustomPayload.Id<InfiniteClassPickPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "infinite_class_pick"));

    public static final PacketCodec<RegistryByteBuf, InfiniteClassPickPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, InfiniteClassPickPayload::affinityOrdinal,
            InfiniteClassPickPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
