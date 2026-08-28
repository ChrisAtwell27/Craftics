package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: an addon has selected an ally for this player through
 * {@code CrafticsAPI.selectAlly}, so the client must enter ally-command mode
 * without a Lead in hand. {@code allyEntityId = -1} leaves that mode.
 *
 * <p>The Lead's own selection travels the other way ({@link LeadSelectPayload}, client to
 * server) because the client already knows it: the item is in the player's hand. An addon's
 * selection exists only on the server, so it has to be told.
 */
public record AllySelectionPayload(int allyEntityId) implements CustomPayload {

    public static final CustomPayload.Id<AllySelectionPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "ally_selection"));

    public static final PacketCodec<RegistryByteBuf, AllySelectionPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, AllySelectionPayload::allyEntityId,
            AllySelectionPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
