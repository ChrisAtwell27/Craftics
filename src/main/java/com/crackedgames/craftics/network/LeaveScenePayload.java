package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: leave the current merchant scene and return to the hub. */
public record LeaveScenePayload() implements CustomPayload {
    public static final CustomPayload.Id<LeaveScenePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "leave_scene"));
    public static final PacketCodec<RegistryByteBuf, LeaveScenePayload> CODEC =
        PacketCodec.unit(new LeaveScenePayload());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
