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
 */
public record VictoryChoicePayload(int emeraldsEarned, int totalEmeralds,
                                    boolean isBossLevel, String biomeName,
                                    int levelIndex) implements CustomPayload {

    public static final CustomPayload.Id<VictoryChoicePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "victory_choice"));

    //? if <=1.21.1 {
    /*public static final PacketCodec<RegistryByteBuf, VictoryChoicePayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, VictoryChoicePayload::emeraldsEarned,
            PacketCodecs.INTEGER, VictoryChoicePayload::totalEmeralds,
            PacketCodecs.BOOL, VictoryChoicePayload::isBossLevel,
            PacketCodecs.STRING, VictoryChoicePayload::biomeName,
            PacketCodecs.INTEGER, VictoryChoicePayload::levelIndex,
            VictoryChoicePayload::new
        );
    *///?} else {
    public static final PacketCodec<RegistryByteBuf, VictoryChoicePayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, VictoryChoicePayload::emeraldsEarned,
            PacketCodecs.INTEGER, VictoryChoicePayload::totalEmeralds,
            PacketCodecs.BOOLEAN, VictoryChoicePayload::isBossLevel,
            PacketCodecs.STRING, VictoryChoicePayload::biomeName,
            PacketCodecs.INTEGER, VictoryChoicePayload::levelIndex,
            VictoryChoicePayload::new
        );
    //?}

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
