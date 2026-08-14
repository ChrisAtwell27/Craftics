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
 * S2C: Tells the client to show the victory choice screen.
 * emeraldsEarned: how many emeralds the player earned this level
 * totalEmeralds: player's total emerald count after earning
 * isBossLevel: true if the player just beat the biome boss
 * biomeName: display name of the current biome
 * levelIndex: current level index within biome (0-4)
 * nextIsBoss: true if the next level in the biome is a boss fight
 * isInfinite: true when this victory happened inside an INFINITE MODE run. The two modes
 *             give the Go Home button opposite meanings - a campaign run resets its biome
 *             progress, an infinite run PARKS at a save point and resumes exactly where it
 *             left off - so the button has to say which one it is doing. The client cannot
 *             work this out for itself: biomeName carries the biome's plain display name
 *             with no infinite marker on it.
 */
public record VictoryChoicePayload(int emeraldsEarned, int totalEmeralds,
                                    boolean isBossLevel, String biomeName,
                                    int levelIndex, boolean nextIsBoss,
                                    boolean isLeader, boolean isInfinite,
                                    List<ItemStack> rewards) implements CustomPayload {

    public static final CustomPayload.Id<VictoryChoicePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "victory_choice"));

    //? if <=1.21.3 {
    public static final PacketCodec<RegistryByteBuf, VictoryChoicePayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> {
                PacketCodecs.INTEGER.encode(buf, payload.emeraldsEarned);
                PacketCodecs.INTEGER.encode(buf, payload.totalEmeralds);
                PacketCodecs.BOOL.encode(buf, payload.isBossLevel);
                PacketCodecs.STRING.encode(buf, payload.biomeName);
                PacketCodecs.INTEGER.encode(buf, payload.levelIndex);
                PacketCodecs.BOOL.encode(buf, payload.nextIsBoss);
                PacketCodecs.BOOL.encode(buf, payload.isLeader);
                PacketCodecs.BOOL.encode(buf, payload.isInfinite);
                buf.writeVarInt(payload.rewards.size());
                for (ItemStack s : payload.rewards) ItemStack.PACKET_CODEC.encode(buf, s);
            },
            buf -> {
                int emeraldsEarned = PacketCodecs.INTEGER.decode(buf);
                int totalEmeralds = PacketCodecs.INTEGER.decode(buf);
                boolean isBossLevel = PacketCodecs.BOOL.decode(buf);
                String biomeName = PacketCodecs.STRING.decode(buf);
                int levelIndex = PacketCodecs.INTEGER.decode(buf);
                boolean nextIsBoss = PacketCodecs.BOOL.decode(buf);
                boolean isLeader = PacketCodecs.BOOL.decode(buf);
                boolean isInfinite = PacketCodecs.BOOL.decode(buf);
                int n = buf.readVarInt();
                List<ItemStack> rewards = new ArrayList<>(n);
                for (int i = 0; i < n; i++) rewards.add(ItemStack.PACKET_CODEC.decode(buf));
                return new VictoryChoicePayload(emeraldsEarned, totalEmeralds, isBossLevel,
                    biomeName, levelIndex, nextIsBoss, isLeader, isInfinite, rewards);
            }
        );
    //?} else {
    /*public static final PacketCodec<RegistryByteBuf, VictoryChoicePayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> {
                PacketCodecs.INTEGER.encode(buf, payload.emeraldsEarned);
                PacketCodecs.INTEGER.encode(buf, payload.totalEmeralds);
                PacketCodecs.BOOLEAN.encode(buf, payload.isBossLevel);
                PacketCodecs.STRING.encode(buf, payload.biomeName);
                PacketCodecs.INTEGER.encode(buf, payload.levelIndex);
                PacketCodecs.BOOLEAN.encode(buf, payload.nextIsBoss);
                PacketCodecs.BOOLEAN.encode(buf, payload.isLeader);
                PacketCodecs.BOOLEAN.encode(buf, payload.isInfinite);
                buf.writeVarInt(payload.rewards.size());
                for (ItemStack s : payload.rewards) ItemStack.PACKET_CODEC.encode(buf, s);
            },
            buf -> {
                int emeraldsEarned = PacketCodecs.INTEGER.decode(buf);
                int totalEmeralds = PacketCodecs.INTEGER.decode(buf);
                boolean isBossLevel = PacketCodecs.BOOLEAN.decode(buf);
                String biomeName = PacketCodecs.STRING.decode(buf);
                int levelIndex = PacketCodecs.INTEGER.decode(buf);
                boolean nextIsBoss = PacketCodecs.BOOLEAN.decode(buf);
                boolean isLeader = PacketCodecs.BOOLEAN.decode(buf);
                boolean isInfinite = PacketCodecs.BOOLEAN.decode(buf);
                int n = buf.readVarInt();
                List<ItemStack> rewards = new ArrayList<>(n);
                for (int i = 0; i < n; i++) rewards.add(ItemStack.PACKET_CODEC.decode(buf));
                return new VictoryChoicePayload(emeraldsEarned, totalEmeralds, isBossLevel,
                    biomeName, levelIndex, nextIsBoss, isLeader, isInfinite, rewards);
            }
        );
    *///?}

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
