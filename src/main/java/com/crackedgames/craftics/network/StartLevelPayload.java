package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StartLevelPayload(String biomeId) implements CustomPayload {

    public static final CustomPayload.Id<StartLevelPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "start_level"));

    public static final PacketCodec<RegistryByteBuf, StartLevelPayload> CODEC =
        PacketCodecs.STRING
            .xmap(StartLevelPayload::new, StartLevelPayload::biomeId)
            .cast();

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
