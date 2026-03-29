package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Combat event notification.
 * eventType: 0=MOVED, 1=DAMAGED, 2=DIED, 3=PHASE_CHANGED, 4=COMBAT_WON, 5=COMBAT_LOST
 * Fields are overloaded per event type.
 */
public record CombatEventPayload(int eventType, int entityId,
                                  int valueA, int valueB,
                                  int targetX, int targetZ) implements CustomPayload {

    public static final int EVENT_MOVED = 0;
    public static final int EVENT_DAMAGED = 1;
    public static final int EVENT_DIED = 2;
    public static final int EVENT_PHASE_CHANGED = 3;
    public static final int EVENT_COMBAT_WON = 4;
    public static final int EVENT_COMBAT_LOST = 5;
    public static final int EVENT_TILE_WARNING = 6;
    public static final int EVENT_MOB_ATTACK_ANIM = 7;

    public static final CustomPayload.Id<CombatEventPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "combat_event"));

    public static final PacketCodec<RegistryByteBuf, CombatEventPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, CombatEventPayload::eventType,
            PacketCodecs.INTEGER, CombatEventPayload::entityId,
            PacketCodecs.INTEGER, CombatEventPayload::valueA,
            PacketCodecs.INTEGER, CombatEventPayload::valueB,
            PacketCodecs.INTEGER, CombatEventPayload::targetX,
            PacketCodecs.INTEGER, CombatEventPayload::targetZ,
            CombatEventPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
