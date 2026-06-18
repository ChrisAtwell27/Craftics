package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: opens the piglin barter stepper on the client. {@code gold} is the player's current gold
 * ingot count; the stepper clamps the offer to {@code [1, min(maxOffer, gold)]}. {@code active=false}
 * closes the stepper (e.g. after the offer resolves).
 */
public record BarterContextPayload(boolean active, int gold, int maxOffer) implements CustomPayload {

    public static final CustomPayload.Id<BarterContextPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "barter_context"));

    //? if <=1.21.3 {
    public static final PacketCodec<RegistryByteBuf, BarterContextPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.BOOL, BarterContextPayload::active,
            PacketCodecs.INTEGER, BarterContextPayload::gold,
            PacketCodecs.INTEGER, BarterContextPayload::maxOffer,
            BarterContextPayload::new
        );
    //?} else {
    /*public static final PacketCodec<RegistryByteBuf, BarterContextPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.BOOLEAN, BarterContextPayload::active,
            PacketCodecs.INTEGER, BarterContextPayload::gold,
            PacketCodecs.INTEGER, BarterContextPayload::maxOffer,
            BarterContextPayload::new
        );
    *///?}

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
