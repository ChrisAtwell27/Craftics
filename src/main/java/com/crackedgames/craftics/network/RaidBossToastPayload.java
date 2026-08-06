package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Tells the client to show a raid boss toast. Used for the broadcast-to-everyone
 * scheduling messages (announce, window open, join countdown, cancel, arrival) so they
 * stop spamming chat -see RaidBossSchedule for the send sites. Command replies
 * (/raidboss, /raidboss info, /raidboss list, admin feedback) stay on chat; they are
 * responses to something the player just typed, not broadcasts.
 */
public record RaidBossToastPayload(String title, String subtitle) implements CustomPayload {

    public static final CustomPayload.Id<RaidBossToastPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "raid_boss_toast"));

    public static final PacketCodec<RegistryByteBuf, RaidBossToastPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, RaidBossToastPayload::title,
            PacketCodecs.STRING, RaidBossToastPayload::subtitle,
            RaidBossToastPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
