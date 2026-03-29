package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Tells the client to show an interactive event room screen.
 * eventType: "shrine", "traveler", "vault"
 * eventData: serialized choices (format depends on event type)
 *
 * Shrine data:  "smallCost:medCost:largeCost:playerEmeralds"
 * Traveler data: "idx:itemName:tier|idx:itemName:tier|..."
 * Vault data:    "biomeOrdinal"
 */
public record EventRoomPayload(String eventType, String eventData) implements CustomPayload {

    public static final CustomPayload.Id<EventRoomPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "event_room"));

    public static final PacketCodec<RegistryByteBuf, EventRoomPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, EventRoomPayload::eventType,
            PacketCodecs.STRING, EventRoomPayload::eventData,
            EventRoomPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
