package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Tells the client to show the victory choice screen.
 * emeraldsEarned: how many emeralds the player earned this level
 * totalEmeralds: player's total emerald count after earning
 * isBossLevel: true if the player just beat the biome boss
 * biomeName: display name of the current biome
 * levelIndex: current level index within biome (0-4)
 * nextIsBoss: true if the next level in the biome is a boss fight
 */
public record VictoryChoicePayload(int emeraldsEarned, int totalEmeralds,
                                    boolean isBossLevel, String biomeName,
                                    int levelIndex, boolean nextIsBoss) implements CustomPayload {

    public static final CustomPayload.Id<VictoryChoicePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "victory_choice"));

    //? if <=1.21.3 {
    /*public static final PacketCodec<RegistryByteBuf, VictoryChoicePayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> {
                PacketCodecs.INTEGER.encode(buf, payload.emeraldsEarned);
                PacketCodecs.INTEGER.encode(buf, payload.totalEmeralds);
                PacketCodecs.BOOL.encode(buf, payload.isBossLevel);
                PacketCodecs.STRING.encode(buf, payload.biomeName);
                PacketCodecs.INTEGER.encode(buf, payload.levelIndex);
                PacketCodecs.BOOL.encode(buf, payload.nextIsBoss);
            },
            buf -> new VictoryChoicePayload(
                PacketCodecs.INTEGER.decode(buf),
                PacketCodecs.INTEGER.decode(buf),
                PacketCodecs.BOOL.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.INTEGER.decode(buf),
                PacketCodecs.BOOL.decode(buf)
            )
        );
    *///?} else {
    public static final PacketCodec<RegistryByteBuf, VictoryChoicePayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> {
                PacketCodecs.INTEGER.encode(buf, payload.emeraldsEarned);
                PacketCodecs.INTEGER.encode(buf, payload.totalEmeralds);
                PacketCodecs.BOOLEAN.encode(buf, payload.isBossLevel);
                PacketCodecs.STRING.encode(buf, payload.biomeName);
                PacketCodecs.INTEGER.encode(buf, payload.levelIndex);
                PacketCodecs.BOOLEAN.encode(buf, payload.nextIsBoss);
            },
            buf -> new VictoryChoicePayload(
                PacketCodecs.INTEGER.decode(buf),
                PacketCodecs.INTEGER.decode(buf),
                PacketCodecs.BOOLEAN.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.INTEGER.decode(buf),
                PacketCodecs.BOOLEAN.decode(buf)
            )
        );
    //?}

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
