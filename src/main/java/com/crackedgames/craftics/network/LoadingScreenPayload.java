package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Show or dismiss a full-screen loading overlay.
 * show: true to start the overlay, false to fade out.
 * title: main text (e.g. "Creating World...")
 * subtitle: smaller text below (e.g. "Generating arenas...")
 */
public record LoadingScreenPayload(boolean show, String title, String subtitle) implements CustomPayload {

    public static final CustomPayload.Id<LoadingScreenPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "loading_screen"));

    //? if <=1.21.3 {
    public static final PacketCodec<RegistryByteBuf, LoadingScreenPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.BOOL, LoadingScreenPayload::show,
            PacketCodecs.STRING, LoadingScreenPayload::title,
            PacketCodecs.STRING, LoadingScreenPayload::subtitle,
            LoadingScreenPayload::new
        );
    //?} else {
    /*public static final PacketCodec<RegistryByteBuf, LoadingScreenPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.BOOLEAN, LoadingScreenPayload::show,
            PacketCodecs.STRING, LoadingScreenPayload::title,
            PacketCodecs.STRING, LoadingScreenPayload::subtitle,
            LoadingScreenPayload::new
        );
    *///?}

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
