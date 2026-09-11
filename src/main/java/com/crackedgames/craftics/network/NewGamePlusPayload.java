package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: a player at the level select block has confirmed the New Game+ prompt and is asking
 * the island to advance a cycle.
 *
 * <p>Carries no data on purpose. The island is resolved from the packet context server-side
 * (via {@code getEffectiveWorldOwner}), and the NG+ level to move to is whatever the island's
 * record says plus one - never a number the client picks. The client screen is only allowed
 * to say "yes, do it"; a spoofed packet can do no more than press a button the sender could
 * already press, and the server re-checks that the offer is actually open before acting.
 */
public record NewGamePlusPayload() implements CustomPayload {

    public static final CustomPayload.Id<NewGamePlusPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "new_game_plus"));

    public static final PacketCodec<RegistryByteBuf, NewGamePlusPayload> CODEC =
        PacketCodec.unit(new NewGamePlusPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
