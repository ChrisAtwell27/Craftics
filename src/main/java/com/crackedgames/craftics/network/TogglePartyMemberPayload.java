package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: the player pressed the battle-party key while looking at a mob.
 *
 * <p>The original way in was Shift + Right-Click, which is exactly what Carry On uses to pick a
 * mob up - so on any setup with both mods a single click did both things. A keybind cannot collide
 * that way: it is listed in the controls screen, the player can move it, and Minecraft resolves
 * conflicts between binds itself.
 *
 * <p>Carries the entity's network id, because the client is the only side that knows what the
 * player is looking at. The server does not trust it: it resolves the id in the player's own
 * world, checks it is a mob, checks it is close enough to have been legitimately targeted, and
 * runs the same eligibility rules the click path always did.
 */
public record TogglePartyMemberPayload(int entityId) implements CustomPayload {

    public static final CustomPayload.Id<TogglePartyMemberPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "toggle_party_member"));

    public static final PacketCodec<RegistryByteBuf, TogglePartyMemberPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.INTEGER, TogglePartyMemberPayload::entityId,
            TogglePartyMemberPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
