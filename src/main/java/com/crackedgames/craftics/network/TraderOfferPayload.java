package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Sends trader offer data to the client for display.
 * traderName: display name like "Weaponsmith"
 * traderIcon: formatting code + icon
 * tradeData: serialized trade entries (itemId~count~cost~stock~desc, pipe-separated)
 * playerEmeralds: current emerald count
 * openScreen: 1 = the player just opened this booth (open a shop screen if none
 *             is showing); 0 = post-purchase refresh (update in place only -
 *             never resurrect a screen the player already closed)
 */
public record TraderOfferPayload(String traderName, String traderIcon,
                                  String tradeData, int playerEmeralds,
                                  int openScreen) implements CustomPayload {

    public static final CustomPayload.Id<TraderOfferPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "trader_offer"));

    public static final PacketCodec<RegistryByteBuf, TraderOfferPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, TraderOfferPayload::traderName,
            PacketCodecs.STRING, TraderOfferPayload::traderIcon,
            PacketCodecs.STRING, TraderOfferPayload::tradeData,
            PacketCodecs.INTEGER, TraderOfferPayload::playerEmeralds,
            PacketCodecs.INTEGER, TraderOfferPayload::openScreen,
            TraderOfferPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
