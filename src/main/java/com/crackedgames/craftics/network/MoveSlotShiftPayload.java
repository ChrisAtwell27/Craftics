package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: Client asks the server to rotate the locked Move-item slot by one
 * hotbar position. {@code direction} is -1 (left) or +1 (right); other values
 * are ignored server-side.
 */
public record MoveSlotShiftPayload(int direction) implements CustomPayload {

    public static final CustomPayload.Id<MoveSlotShiftPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "move_slot_shift"));

    public static final PacketCodec<RegistryByteBuf, MoveSlotShiftPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, MoveSlotShiftPayload::direction,
            MoveSlotShiftPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
