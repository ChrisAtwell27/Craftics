package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: a player at the level select block has confirmed the New Game+ prompt and is asking
 * the island to advance a cycle.
 *
 * <p>{@code cycle} is the NG+ number the confirm dialog showed, and it is a check, not an
 * instruction. The island is still resolved server-side (via {@code getEffectiveWorldOwner})
 * and the cycle it moves to is still whatever its record says plus one. The server only acts
 * when that equals {@code cycle}, so a dialog left open while a teammate took the cycle, or an
 * admin rolled it back, cannot confirm a different NG+ from the one the player agreed to.
 */
public record NewGamePlusPayload(int cycle) implements CustomPayload {

    public static final CustomPayload.Id<NewGamePlusPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "new_game_plus"));

    public static final PacketCodec<RegistryByteBuf, NewGamePlusPayload> CODEC =
        PacketCodec.tuple(PacketCodecs.INTEGER, NewGamePlusPayload::cycle, NewGamePlusPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
