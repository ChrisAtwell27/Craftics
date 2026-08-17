package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Play the fighting-game style battle intro. {@code cast} is
 * "entityId:AFFINITY,entityId:AFFINITY,..." in camera order; {@code stepTicks}
 * is how long the camera dwells on each fighter. The client runs the whole
 * sequence locally (camera zoom-in per player, affinity intro animation, zoom
 * out at the end) once its own loading transition has cleared - each client's
 * transition finishes at its own moment, so the timeline cannot be stepped
 * from the server without the camera cutting mid-swipe for slower loaders.
 * The server locks combat input for a matching duration on its side.
 */
public record CombatIntroPayload(String cast, int stepTicks) implements CustomPayload {

    public static final CustomPayload.Id<CombatIntroPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "combat_intro"));

    public static final PacketCodec<RegistryByteBuf, CombatIntroPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, CombatIntroPayload::cast,
            PacketCodecs.INTEGER, CombatIntroPayload::stepTicks,
            CombatIntroPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
