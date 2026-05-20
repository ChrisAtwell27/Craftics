package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client telling the server which ally is selected by the Lead. Server
 * applies the glow flag so the data tracker sync makes it visible to
 * everyone in the party. Send {@code allyEntityId = -1} to clear selection.
 */
public record LeadSelectPayload(int allyEntityId) implements CustomPayload {

    public static final CustomPayload.Id<LeadSelectPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "lead_select"));

    public static final PacketCodec<RegistryByteBuf, LeadSelectPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, LeadSelectPayload::allyEntityId,
            LeadSelectPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
