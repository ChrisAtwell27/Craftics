package com.crackedgames.craftics.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tile-set payload carries every grid layer the client paints. The breath-cloud layer is the
 * only thing that tells the client where the Ender Dragon's breath is hanging - the particles
 * alone vanish under reduced particle settings - so it has to survive the trip intact and not
 * shift the layers packed around it.
 */
class TileSetPayloadCodecTest {

    private static TileSetPayload roundTrip(TileSetPayload in) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        TileSetPayload.CODEC.encode(buf, in);
        return TileSetPayload.CODEC.decode(buf);
    }

    @Test
    void breathCloudsSurviveTheTripAlongsideTheOtherLayers() {
        int[] clouds = { 2, 3, 3, 4, 7, 1, 2, 5 }; // (2,3) 3 turns 4 dmg, (7,1) 2 turns 5 dmg
        TileSetPayload in = new TileSetPayload(
            new int[]{1, 1}, new int[]{2, 2}, new int[0], new int[]{5, 5},
            new int[]{3, 3, 99}, "minecraft:zombie", new int[0], new int[]{0, 0, 1, 0},
            new int[0], new int[0], new int[]{4, 4}, clouds);

        TileSetPayload out = roundTrip(in);

        assertArrayEquals(clouds, out.breathClouds());
        assertArrayEquals(new int[]{5, 5}, out.warningTiles());
        assertArrayEquals(new int[]{4, 4}, out.castTiles(), "the layer before it is untouched");
        assertEquals("minecraft:zombie", out.enemyTypes());
    }

    @Test
    void noCloudsIsAnEmptyLayer() {
        TileSetPayload in = new TileSetPayload(
            new int[0], new int[0], new int[0], new int[0], new int[0], "", new int[0],
            new int[0], new int[0], new int[0], new int[0], new int[0]);

        assertEquals(0, roundTrip(in).breathClouds().length);
    }
}
