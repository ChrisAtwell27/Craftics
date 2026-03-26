package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Player's choice after winning a level.
 * goHome: true = return to hub (biome run resets), false = continue to next level
 */
public record PostLevelChoicePayload(boolean goHome) implements CustomPayload {

    public static final CustomPayload.Id<PostLevelChoicePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "post_level_choice"));

    public static final PacketCodec<RegistryByteBuf, PostLevelChoicePayload> CODEC =
        PacketCodecs.BOOL
            .xmap(PostLevelChoicePayload::new, PostLevelChoicePayload::goHome)
            .cast();

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
