package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client asks the server to remove every mob from the player's battle
 * party (hub-only convenience keybind). Carries no data — the player is taken
 * from the packet context server-side.
 */
public record ClearPartyPayload() implements CustomPayload {

    public static final CustomPayload.Id<ClearPartyPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "clear_party"));

    public static final PacketCodec<RegistryByteBuf, ClearPartyPayload> CODEC =
        PacketCodec.unit(new ClearPartyPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
