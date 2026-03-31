package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Tells the client to show an achievement unlock toast.
 */
public record AchievementUnlockPayload(String achievementId, String displayName,
                                        String description, String categoryColor) implements CustomPayload {

    public static final CustomPayload.Id<AchievementUnlockPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "achievement_unlock"));

    public static final PacketCodec<RegistryByteBuf, AchievementUnlockPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, AchievementUnlockPayload::achievementId,
            PacketCodecs.STRING, AchievementUnlockPayload::displayName,
            PacketCodecs.STRING, AchievementUnlockPayload::description,
            PacketCodecs.STRING, AchievementUnlockPayload::categoryColor,
            AchievementUnlockPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
