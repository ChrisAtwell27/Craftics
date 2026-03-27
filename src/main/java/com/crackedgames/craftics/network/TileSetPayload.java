package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client payload containing valid move/attack/danger tiles and enemy grid positions.
 * Replaces the old carpet-block highlight system with client-side cached tile sets.
 */
public record TileSetPayload(
    int[] moveTiles,     // flat: [x1, z1, x2, z2, ...]
    int[] attackTiles,   // flat: [x1, z1, x2, z2, ...]
    int[] dangerTiles,   // flat: [x1, z1, x2, z2, ...]
    int[] warningTiles,  // flat: [x1, z1, x2, z2, ...] — boss attack telegraphs
    int[] enemyMap,      // flat: [x, z, entityId, x, z, entityId, ...]
    String enemyTypes    // pipe-separated entity type IDs parallel to enemyMap triplets
) implements CustomPayload {

    public static final Id<TileSetPayload> ID =
        new Id<>(Identifier.of("craftics", "tile_set"));

    public static final PacketCodec<RegistryByteBuf, TileSetPayload> CODEC =
        PacketCodec.of(TileSetPayload::encode, TileSetPayload::decode);

    private static TileSetPayload decode(RegistryByteBuf buf) {
        int[] move = readIntArray(buf);
        int[] attack = readIntArray(buf);
        int[] danger = readIntArray(buf);
        int[] warning = readIntArray(buf);
        int[] enemy = readIntArray(buf);
        String types = buf.readString();
        return new TileSetPayload(move, attack, danger, warning, enemy, types);
    }

    private void encode(RegistryByteBuf buf) {
        writeIntArray(buf, moveTiles);
        writeIntArray(buf, attackTiles);
        writeIntArray(buf, dangerTiles);
        writeIntArray(buf, warningTiles);
        writeIntArray(buf, enemyMap);
        buf.writeString(enemyTypes);
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
