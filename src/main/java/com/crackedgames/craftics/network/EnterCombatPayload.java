package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Combat-start payload. Carries arena origin/size, the isometric camera yaw, and
 * (for non-rectangular arenas) a packed polygon mask so the client can restrict
 * the cursor/hover to the actual playable shape instead of the bounding box.
 *
 * <p>{@code polygonMask} is row-major bits over {@code width × height}
 * (bit index {@code x * height + z}); an empty array means a rectangular arena
 * (whole bbox is in-bounds).
 */
public record EnterCombatPayload(int originX, int originY, int originZ,
                                  int width, int height, float cameraYaw,
                                  byte[] polygonMask) implements CustomPayload {

    /** No camera yaw (default SW angle), no polygon mask. */
    public EnterCombatPayload(int originX, int originY, int originZ, int width, int height) {
        this(originX, originY, originZ, width, height, -1f, new byte[0]);
    }

    /** Camera yaw, no polygon mask (rectangular arena). */
    public EnterCombatPayload(int originX, int originY, int originZ, int width, int height, float cameraYaw) {
        this(originX, originY, originZ, width, height, cameraYaw, new byte[0]);
    }

    public static final CustomPayload.Id<EnterCombatPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "enter_combat"));

    public static final PacketCodec<RegistryByteBuf, EnterCombatPayload> CODEC =
        PacketCodec.of(EnterCombatPayload::encode, EnterCombatPayload::decode);

    private void encode(RegistryByteBuf buf) {
        buf.writeInt(originX);
        buf.writeInt(originY);
        buf.writeInt(originZ);
        buf.writeInt(width);
        buf.writeInt(height);
        buf.writeFloat(cameraYaw);
        buf.writeByteArray(polygonMask);
    }

    private static EnterCombatPayload decode(RegistryByteBuf buf) {
        int ox = buf.readInt();
        int oy = buf.readInt();
        int oz = buf.readInt();
        int w = buf.readInt();
        int h = buf.readInt();
        float yaw = buf.readFloat();
        byte[] mask = buf.readByteArray();
        return new EnterCombatPayload(ox, oy, oz, w, h, yaw, mask);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
