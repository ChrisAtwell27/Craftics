package com.crackedgames.craftics.vfx;

import com.crackedgames.craftics.network.VfxClientPayload;
import com.crackedgames.craftics.network.VfxClientPayload.ClientPrim;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VfxClientPayloadCodecTest {

    @Test
    void roundTripsShake() {
        var payload = new VfxClientPayload(UUID.randomUUID(), 0,
            List.of(new ClientPrim.Shake(0.7f, 12)));
        assertEquals(payload, encodeDecode(payload));
    }

    @Test
    void roundTripsAllVariants() {
        UUID id = UUID.randomUUID();
        var payload = new VfxClientPayload(id, 3, List.of(
            new ClientPrim.Shake(0.5f, 6),
            new ClientPrim.ScreenFlash(0x88FF0000, 8),
            new ClientPrim.HitPause(3),
            new ClientPrim.FloatingText(10.5, 64.0, 20.5, "EXECUTE", 0xFFFF2222, 30),
            new ClientPrim.Vignette(0, 1, 8)
        ));
        assertEquals(payload, encodeDecode(payload));
    }

    private VfxClientPayload encodeDecode(VfxClientPayload in) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        VfxClientPayload.CODEC.encode(buf, in);
        return VfxClientPayload.CODEC.decode(buf);
    }
}
