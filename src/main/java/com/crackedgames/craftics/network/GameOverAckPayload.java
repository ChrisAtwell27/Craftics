package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: the player's game-over coin-flip animation finished (or they clicked
 * Continue). Tells the server it may apply that player's pre-rolled loss and,
 * once everyone has acked, run the teardown.
 */
public record GameOverAckPayload() implements CustomPayload {

    public static final CustomPayload.Id<GameOverAckPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "game_over_ack"));

    public static final PacketCodec<RegistryByteBuf, GameOverAckPayload> CODEC =
        PacketCodec.unit(new GameOverAckPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
