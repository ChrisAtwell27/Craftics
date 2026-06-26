package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: opens the party-death coin-flip game-over screen. Carries the player's
 * losable items GROUPED by type (each {@code items} entry's count is the total
 * owned), the number of units lost per group (the coin shows the X when > 0),
 * and the emerald/XP-level losses to summarize. The two lists are the same
 * length.
 */
public record GameOverItemsPayload(List<ItemStack> items, List<Integer> lostCounts,
                                   int emeraldsLost, int xpLevelsLost) implements CustomPayload {

    public GameOverItemsPayload {
        if (lostCounts.size() != items.size()) {
            throw new IllegalArgumentException("GameOverItemsPayload list lengths must match: items="
                + items.size() + " lostCounts=" + lostCounts.size());
        }
    }

    public static final CustomPayload.Id<GameOverItemsPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "game_over_items"));

    public static final PacketCodec<RegistryByteBuf, GameOverItemsPayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> {
                buf.writeVarInt(payload.items.size());
                for (ItemStack s : payload.items) ItemStack.PACKET_CODEC.encode(buf, s);
                buf.writeVarInt(payload.lostCounts.size());
                for (Integer n : payload.lostCounts) buf.writeVarInt(n);
                buf.writeVarInt(payload.emeraldsLost);
                buf.writeVarInt(payload.xpLevelsLost);
            },
            buf -> {
                int ni = buf.readVarInt();
                List<ItemStack> items = new ArrayList<>(ni);
                for (int i = 0; i < ni; i++) items.add(ItemStack.PACKET_CODEC.decode(buf));
                int nl = buf.readVarInt();
                List<Integer> lostCounts = new ArrayList<>(nl);
                for (int i = 0; i < nl; i++) lostCounts.add(buf.readVarInt());
                int emeraldsLost = buf.readVarInt();
                int xpLevelsLost = buf.readVarInt();
                return new GameOverItemsPayload(items, lostCounts, emeraldsLost, xpLevelsLost);
            }
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
