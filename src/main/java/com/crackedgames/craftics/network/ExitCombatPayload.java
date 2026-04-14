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
 * eventRoomFollows: true if an event room screen is about to appear (skip first-person switch).
 */
public record ExitCombatPayload(boolean won, boolean eventRoomFollows) implements CustomPayload {

    public ExitCombatPayload(boolean won) {
        this(won, false);
    }

    public static final CustomPayload.Id<ExitCombatPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "exit_combat"));

    //? if <=1.21.1 {
    /*public static final PacketCodec<RegistryByteBuf, ExitCombatPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.BOOL, ExitCombatPayload::won,
            PacketCodecs.BOOL, ExitCombatPayload::eventRoomFollows,
            ExitCombatPayload::new
        );
    *///?} else {
    public static final PacketCodec<RegistryByteBuf, ExitCombatPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.BOOLEAN, ExitCombatPayload::won,
            PacketCodecs.BOOLEAN, ExitCombatPayload::eventRoomFollows,
            ExitCombatPayload::new
        );
    //?}

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
