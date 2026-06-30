package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: request to enter a merchant scene ("village" or "barter_station"). */
public record EnterScenePayload(String sceneName) implements CustomPayload {
    public static final CustomPayload.Id<EnterScenePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "enter_scene"));
    public static final PacketCodec<RegistryByteBuf, EnterScenePayload> CODEC =
        PacketCodecs.STRING.xmap(EnterScenePayload::new, EnterScenePayload::sceneName).cast();
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
