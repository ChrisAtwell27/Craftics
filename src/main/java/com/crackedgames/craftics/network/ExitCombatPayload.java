package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Exit combat mode.
 * won: true if player won, false if defeated.
 */
public record ExitCombatPayload(boolean won) implements CustomPayload {

    public static final CustomPayload.Id<ExitCombatPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "exit_combat"));

    public static final PacketCodec<RegistryByteBuf, ExitCombatPayload> CODEC =
        PacketCodecs.BOOL
            .xmap(ExitCombatPayload::new, ExitCombatPayload::won)
            .cast();

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
