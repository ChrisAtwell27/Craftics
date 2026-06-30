package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: a scene-local grid tile the player clicked to walk to. */
public record SceneClickPayload(int tx, int tz) implements CustomPayload {
    public static final CustomPayload.Id<SceneClickPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "scene_click"));
    public static final PacketCodec<RegistryByteBuf, SceneClickPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, SceneClickPayload::tx,
            PacketCodecs.INTEGER, SceneClickPayload::tz,
            SceneClickPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
