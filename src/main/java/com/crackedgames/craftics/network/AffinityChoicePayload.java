package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client sends which damage affinity they chose to upgrade on level-up.
 * affinityOrdinal: the ordinal of the chosen PlayerProgression.Affinity enum value
 */
public record AffinityChoicePayload(int affinityOrdinal) implements CustomPayload {

    public static final CustomPayload.Id<AffinityChoicePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "affinity_choice"));

    public static final PacketCodec<RegistryByteBuf, AffinityChoicePayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, AffinityChoicePayload::affinityOrdinal,
            AffinityChoicePayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
