package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: tells the client which soundtrack to play. {@code trackKey} is a
 * {@link com.crackedgames.craftics.sound.MusicTracks#key}; an empty string means
 * "fade out and stop" (e.g. back in the hub). The client resolves the key to a
 * registered SoundEvent + attribution and cross-fades to it.
 */
public record MusicSyncPayload(String trackKey) implements CustomPayload {

    public static final CustomPayload.Id<MusicSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "music_sync"));

    public static final PacketCodec<RegistryByteBuf, MusicSyncPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, MusicSyncPayload::trackKey,
            MusicSyncPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
