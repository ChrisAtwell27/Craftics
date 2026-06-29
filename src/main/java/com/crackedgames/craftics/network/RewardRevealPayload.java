package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: opens the reward-reveal screen - the staggered, sound-backed loot reveal that
 * shares the Game Over sequence's feel. Used for outcome moments that hand the player
 * items mid-event: the piglin barter gamble and the treasure-vault loot.
 *
 * <p>{@code style} selects the intro flourish (see {@code RewardRevealScreen});
 * {@code success} drives the gamble coin's heads/tails landing (1 = paid off). The
 * items are display snapshots - they're already in the player's inventory by the time
 * this is sent. On close the screen sends a {@link DialogueChoicePayload#ACTION_DISMISS}
 * which routes back into the active event's finish path, exactly like the result
 * dialogue it replaces.
 *
 * <p>Encoded with only varInt / String / ItemStack so there is no {@code BOOL} vs
 * {@code BOOLEAN} version split across stonecutter shards.
 */
public record RewardRevealPayload(int style, int success, String title, String subtitle,
                                  List<ItemStack> items) implements CustomPayload {

    /** Staggered drop-in (default). */
    public static final int STYLE_DROP_IN = 0;
    /** Coin-flip gamble intro, then the loot drops in (piglin barter). */
    public static final int STYLE_GAMBLE = 1;
    /** Chest-burst (treasure vault / trial loot). */
    public static final int STYLE_CHEST = 2;

    public static final CustomPayload.Id<RewardRevealPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "reward_reveal"));

    public static final PacketCodec<RegistryByteBuf, RewardRevealPayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> {
                buf.writeVarInt(payload.style);
                buf.writeVarInt(payload.success);
                PacketCodecs.STRING.encode(buf, payload.title);
                PacketCodecs.STRING.encode(buf, payload.subtitle);
                buf.writeVarInt(payload.items.size());
                for (ItemStack s : payload.items) ItemStack.PACKET_CODEC.encode(buf, s);
            },
            buf -> {
                int style = buf.readVarInt();
                int success = buf.readVarInt();
                String title = PacketCodecs.STRING.decode(buf);
                String subtitle = PacketCodecs.STRING.decode(buf);
                int n = buf.readVarInt();
                List<ItemStack> items = new ArrayList<>(n);
                for (int i = 0; i < n; i++) items.add(ItemStack.PACKET_CODEC.decode(buf));
                return new RewardRevealPayload(style, success, title, subtitle, items);
            }
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
