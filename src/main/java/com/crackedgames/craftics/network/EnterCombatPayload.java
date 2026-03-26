package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record EnterCombatPayload(int originX, int originY, int originZ,
                                  int width, int height, float cameraYaw) implements CustomPayload {

    /** Convenience constructor without camera yaw (defaults to -1 = use default SW angle). */
    public EnterCombatPayload(int originX, int originY, int originZ, int width, int height) {
        this(originX, originY, originZ, width, height, -1f);
    }

    public static final CustomPayload.Id<EnterCombatPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "enter_combat"));

    public static final PacketCodec<RegistryByteBuf, EnterCombatPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, EnterCombatPayload::originX,
            PacketCodecs.INTEGER, EnterCombatPayload::originY,
            PacketCodecs.INTEGER, EnterCombatPayload::originZ,
            PacketCodecs.INTEGER, EnterCombatPayload::width,
            PacketCodecs.INTEGER, EnterCombatPayload::height,
            PacketCodecs.FLOAT, EnterCombatPayload::cameraYaw,
            EnterCombatPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
