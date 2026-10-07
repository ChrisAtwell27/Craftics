package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client: which traits each bestiary entry carries, as encoded by
 * {@link com.crackedgames.craftics.level.MobTraitSync}.
 *
 * <p>In a fight the client reads a mob's traits off the combat sync. The guide book has no
 * fight to read from, and its entries are keyed by display name ("The Revenant") rather than
 * by anything the client could resolve back to an entity type and a boss key - so the server,
 * which knows both, sends the answer.
 *
 * <p>Sized like the biome atlas for the same reason: a string codec's cap is an encode-side
 * exception, which on this packet would be a crash on player join.
 */
public record MobTraitCatalogPayload(String encoded) implements CustomPayload {

    public static final CustomPayload.Id<MobTraitCatalogPayload> ID =
        new CustomPayload.Id<>(Identifier.of("craftics", "mob_trait_catalog"));

    public static final PacketCodec<RegistryByteBuf, MobTraitCatalogPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.string(262_144), MobTraitCatalogPayload::encoded,
            MobTraitCatalogPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
