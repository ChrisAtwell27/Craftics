package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client: the biome atlas, as encoded by
 * {@link com.crackedgames.craftics.level.BiomeAtlasCodec}.
 *
 * <p>Sent because the client genuinely cannot work this out for itself. Biomes are loaded from
 * datapacks into a server-side registry, so on a dedicated server a client has no idea what a
 * biome's loot pool holds - and the level select screen only ever needed names and unlock
 * numbers, which come from the campaign definition instead.
 *
 * <p>One packet for the whole atlas rather than one per biome. It is a few kilobytes at most,
 * it is sent twice a session (on join, and after a datapack reload changes the answer), and a
 * single message means the client is never rendering a half-arrived atlas.
 */
public record BiomeAtlasPayload(String encoded) implements CustomPayload {

    public static final CustomPayload.Id<BiomeAtlasPayload> ID =
        new CustomPayload.Id<>(Identifier.of("craftics", "biome_atlas"));

    /**
     * Explicitly sized rather than {@code PacketCodecs.STRING}, whose 32767-character cap is
     * comfortable for eighteen vanilla biomes and not obviously comfortable for a campaign pack
     * with a hundred. Exceeding a string codec's limit is an encode-side exception, i.e. a
     * server-side crash on player join, so the cap is set where it cannot plausibly be reached.
     */
    public static final PacketCodec<RegistryByteBuf, BiomeAtlasPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.string(262_144), BiomeAtlasPayload::encoded,
            BiomeAtlasPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
