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
public record LoadingScreenPayload(boolean show, String title, String subtitle, String cast)
        implements CustomPayload {

    /**
     * Three-arg form for every loading screen that shows no walkers.
     *
     * <p>Kept so the ten existing call sites are untouched by the cast being added - a loading
     * screen without a party behind it (world creation, going home alone) has nothing to walk.
     */
    public LoadingScreenPayload(boolean show, String title, String subtitle) {
        this(show, title, subtitle, "");
    }

    public static final CustomPayload.Id<LoadingScreenPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "loading_screen"));

    //? if <=1.21.3 {
    public static final PacketCodec<RegistryByteBuf, LoadingScreenPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.BOOL, LoadingScreenPayload::show,
            PacketCodecs.STRING, LoadingScreenPayload::title,
            PacketCodecs.STRING, LoadingScreenPayload::subtitle,
            PacketCodecs.STRING, LoadingScreenPayload::cast,
            LoadingScreenPayload::new
        );
    //?} else {
    /*public static final PacketCodec<RegistryByteBuf, LoadingScreenPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.BOOLEAN, LoadingScreenPayload::show,
            PacketCodecs.STRING, LoadingScreenPayload::title,
            PacketCodecs.STRING, LoadingScreenPayload::subtitle,
            PacketCodecs.STRING, LoadingScreenPayload::cast,
            LoadingScreenPayload::new
        );
    *///?}

    /** Split the wire form back into affinity names. Empty string = no walkers. */
    public java.util.List<String> castList() {
        if (cast == null || cast.isBlank()) return java.util.List.of();
        return java.util.Arrays.asList(cast.split(","));
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
