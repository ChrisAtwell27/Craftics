package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Syncs the full set of unlocked guide book entries to the client.
 * Sent on join and whenever a new entry is unlocked server-side.
 * unlockedEntries is a pipe-separated string of entry names.
 */
public record GuideBookSyncPayload(String unlockedEntries) implements CustomPayload {

    public static final CustomPayload.Id<GuideBookSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "guide_book_sync"));

    public static final PacketCodec<RegistryByteBuf, GuideBookSyncPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, GuideBookSyncPayload::unlockedEntries,
            GuideBookSyncPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
