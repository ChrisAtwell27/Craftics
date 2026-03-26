package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Tells the client to show the level-up screen.
 * playerLevel: new level after level-up
 * unspentPoints: how many points to spend
 * statData: serialized current stat values "speed:ap:melee:ranged:vitality:defense:luck:resourceful"
 */
public record LevelUpPayload(int playerLevel, int unspentPoints,
                               String statData) implements CustomPayload {

    public static final CustomPayload.Id<LevelUpPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "level_up"));

    public static final PacketCodec<RegistryByteBuf, LevelUpPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, LevelUpPayload::playerLevel,
            PacketCodecs.INTEGER, LevelUpPayload::unspentPoints,
            PacketCodecs.STRING, LevelUpPayload::statData,
            LevelUpPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
