package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client requests to buy a trade at the given index.
 * tradeIndex: which trade slot (0-based)
 */
public record TraderBuyPayload(int tradeIndex) implements CustomPayload {

    public static final CustomPayload.Id<TraderBuyPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "trader_buy"));

    public static final PacketCodec<RegistryByteBuf, TraderBuyPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, TraderBuyPayload::tradeIndex,
            TraderBuyPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
