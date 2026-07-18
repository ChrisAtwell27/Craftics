package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: offer the infinite-mode class selection to a participant whose run just started
 * (or who just re-joined a resumed run with a clean slate). Carries no data - the client
 * builds the option list from the shared {@code PlayerProgression.Affinity} enum, so the
 * two sides can never disagree on what the classes are.
 *
 * <p>The client defers opening the screen until the world is stable (the run start
 * teleports the party between dimensions, which wipes any screen open at that moment).
 */
public record InfiniteClassOfferPayload() implements CustomPayload {

    public static final CustomPayload.Id<InfiniteClassOfferPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "infinite_class_offer"));

    public static final PacketCodec<RegistryByteBuf, InfiniteClassOfferPayload> CODEC =
        PacketCodec.unit(new InfiniteClassOfferPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
