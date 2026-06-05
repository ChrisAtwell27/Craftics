package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: flash a set of tiles (packed alternating x,z) in {@code color} for {@code durationTicks}. */
public record TileFlashPayload(int[] tiles, int color, int durationTicks) implements CustomPayload {

    public static final Id<TileFlashPayload> ID = new Id<>(Identifier.of("craftics", "tile_flash"));

    public static final PacketCodec<RegistryByteBuf, TileFlashPayload> CODEC =
        PacketCodec.of(TileFlashPayload::encode, TileFlashPayload::decode);

    private static TileFlashPayload decode(RegistryByteBuf buf) {
        int[] tiles = readIntArray(buf);
        int color = buf.readInt();
        int duration = buf.readInt();
        return new TileFlashPayload(tiles, color, duration);
    }

    private void encode(RegistryByteBuf buf) {
        writeIntArray(buf, tiles);
        buf.writeInt(color);
        buf.writeInt(durationTicks);
    }

    private static void writeIntArray(RegistryByteBuf buf, int[] arr) {
        buf.writeVarInt(arr.length);
        for (int v : arr) buf.writeVarInt(v);
    }

    private static int[] readIntArray(RegistryByteBuf buf) {
        int len = buf.readVarInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = buf.readVarInt();
        return arr;
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
