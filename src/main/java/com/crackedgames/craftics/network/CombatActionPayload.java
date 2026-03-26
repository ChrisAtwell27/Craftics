package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Player combat action.
 * actionType: 0=MOVE, 1=ATTACK, 2=END_TURN
 * targetX/targetZ: grid position (for MOVE)
 * targetEntityId: entity network ID (for ATTACK)
 */
public record CombatActionPayload(int actionType, int targetX, int targetZ,
                                   int targetEntityId) implements CustomPayload {

    public static final int ACTION_MOVE = 0;
    public static final int ACTION_ATTACK = 1;
    public static final int ACTION_END_TURN = 2;
    public static final int ACTION_USE_ITEM = 3;
    public static final int ACTION_HOVER = 4;     // targetX/Z = hovered grid tile, or -1,-1 for no hover

    public static final CustomPayload.Id<CombatActionPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "combat_action"));

    public static final PacketCodec<RegistryByteBuf, CombatActionPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, CombatActionPayload::actionType,
            PacketCodecs.INTEGER, CombatActionPayload::targetX,
            PacketCodecs.INTEGER, CombatActionPayload::targetZ,
            PacketCodecs.INTEGER, CombatActionPayload::targetEntityId,
            CombatActionPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
