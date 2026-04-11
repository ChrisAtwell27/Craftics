package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Syncs the server scoreboard to clients for display in the TAB player list.
 * scoreData format: "playerName,score|playerName,score|..." sorted descending by score.
 */
public record ScoreboardSyncPayload(String scoreData) implements CustomPayload {
    public static final CustomPayload.Id<ScoreboardSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "scoreboard_sync"));

    public static final PacketCodec<RegistryByteBuf, ScoreboardSyncPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public ScoreboardSyncPayload decode(RegistryByteBuf buf) {
                return new ScoreboardSyncPayload(buf.readString());
            }

            @Override
            public void encode(RegistryByteBuf buf, ScoreboardSyncPayload payload) {
                buf.writeString(payload.scoreData);
            }
        };

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
