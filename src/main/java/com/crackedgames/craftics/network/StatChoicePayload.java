package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client sends back which stat they chose to upgrade.
 * statOrdinal: the ordinal of the chosen PlayerProgression.Stat enum value
 */
public record StatChoicePayload(int statOrdinal) implements CustomPayload {

    public static final CustomPayload.Id<StatChoicePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "stat_choice"));

    public static final PacketCodec<RegistryByteBuf, StatChoicePayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, StatChoicePayload::statOrdinal,
            StatChoicePayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
