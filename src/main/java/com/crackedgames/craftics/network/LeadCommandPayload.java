package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Player commands one of their allies via a Lead during their own turn.
 * Costs 2 AP and does NOT consume the ally's own AI turn this round.
 *
 * <p>{@code allyEntityId} is the entity network id of the selected ally.
 * If {@code targetEntityId >= 0}, the ally attacks that enemy (which must be
 * within 1 tile of the ally). Otherwise the ally is moved to
 * {@code (targetX, targetZ)} — server validates walkability and occupancy.
 */
public record LeadCommandPayload(int allyEntityId, int targetX, int targetZ,
                                  int targetEntityId) implements CustomPayload {

    public static final CustomPayload.Id<LeadCommandPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "lead_command"));

    public static final PacketCodec<RegistryByteBuf, LeadCommandPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, LeadCommandPayload::allyEntityId,
            PacketCodecs.INTEGER, LeadCommandPayload::targetX,
            PacketCodecs.INTEGER, LeadCommandPayload::targetZ,
            PacketCodecs.INTEGER, LeadCommandPayload::targetEntityId,
            LeadCommandPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
