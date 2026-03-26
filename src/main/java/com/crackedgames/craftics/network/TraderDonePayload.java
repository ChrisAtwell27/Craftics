package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client signals they are done trading and ready for next level.
 */
public record TraderDonePayload() implements CustomPayload {

    public static final CustomPayload.Id<TraderDonePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "trader_done"));

    public static final PacketCodec<RegistryByteBuf, TraderDonePayload> CODEC =
        PacketCodec.unit(new TraderDonePayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
