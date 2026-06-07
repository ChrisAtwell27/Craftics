package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client presses the mount-ability key while riding a combat mount. Carries no
 * data — the player (and thus their mount) is taken from the packet context
 * server-side. The netherite golem's ability spends AP to summon coal golem allies.
 */
public record MountAbilityPayload() implements CustomPayload {

    public static final CustomPayload.Id<MountAbilityPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "mount_ability"));

    public static final PacketCodec<RegistryByteBuf, MountAbilityPayload> CODEC =
        PacketCodec.unit(new MountAbilityPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
