package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Syncs addon equipment scanner bonus data to the client for UI display.
 * Sent whenever TrimEffects.scan() runs (combat start, equipment change, etc.).
 *
 * Format: comma-separated "KEY:VALUE" pairs, e.g. "WATER_POWER:1,SPEED:2"
 * Empty string means no addon bonuses.
 */
public record AddonBonusSyncPayload(String bonusData) implements CustomPayload {

    public static final CustomPayload.Id<AddonBonusSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "addon_bonus_sync"));

    public static final PacketCodec<RegistryByteBuf, AddonBonusSyncPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, AddonBonusSyncPayload::bonusData,
            AddonBonusSyncPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
