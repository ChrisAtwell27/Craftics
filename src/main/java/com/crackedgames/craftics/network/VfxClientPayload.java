package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * S2C batch of client-side VfxPrimitives firing in one scheduled phase.
 * Each primitive is prefixed by a 1-byte tag for a compact, flat encoding.
 */
public record VfxClientPayload(UUID runId, int phaseIndex, List<ClientPrim> primitives)
    implements CustomPayload {

    public static final Id<VfxClientPayload> ID =
        new Id<>(Identifier.of("craftics", "vfx_client"));

    public static final PacketCodec<RegistryByteBuf, VfxClientPayload> CODEC =
        PacketCodec.of(VfxClientPayload::encode, VfxClientPayload::decode);

    public sealed interface ClientPrim {
        record Shake(float intensity, int durationTicks) implements ClientPrim {}
        record ScreenFlash(int argb, int durationTicks) implements ClientPrim {}
        record HitPause(int freezeTicks) implements ClientPrim {}
        record FloatingText(double x, double y, double z, String text, int color, int lifetimeTicks) implements ClientPrim {}
        record Vignette(int typeOrdinal, int level, int durationTicks) implements ClientPrim {}
    }

    private void encode(RegistryByteBuf buf) {
        buf.writeUuid(runId);
        buf.writeVarInt(phaseIndex);
        buf.writeVarInt(primitives.size());
        for (ClientPrim p : primitives) {
            switch (p) {
                case ClientPrim.Shake s -> {
                    buf.writeByte(0);
                    buf.writeFloat(s.intensity());
                    buf.writeVarInt(s.durationTicks());
                }
                case ClientPrim.ScreenFlash s -> {
                    buf.writeByte(1);
                    buf.writeInt(s.argb());
                    buf.writeVarInt(s.durationTicks());
                }
                case ClientPrim.HitPause s -> {
                    buf.writeByte(2);
                    buf.writeVarInt(s.freezeTicks());
                }
                case ClientPrim.FloatingText s -> {
                    buf.writeByte(3);
                    buf.writeDouble(s.x());
                    buf.writeDouble(s.y());
                    buf.writeDouble(s.z());
                    buf.writeString(s.text());
                    buf.writeInt(s.color());
                    buf.writeVarInt(s.lifetimeTicks());
                }
                case ClientPrim.Vignette s -> {
                    buf.writeByte(4);
                    buf.writeVarInt(s.typeOrdinal());
                    buf.writeVarInt(s.level());
                    buf.writeVarInt(s.durationTicks());
                }
            }
        }
    }

    private static VfxClientPayload decode(RegistryByteBuf buf) {
        UUID id = buf.readUuid();
        int phase = buf.readVarInt();
        int n = buf.readVarInt();
        List<ClientPrim> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            byte tag = buf.readByte();
            switch (tag) {
                case 0 -> list.add(new ClientPrim.Shake(buf.readFloat(), buf.readVarInt()));
                case 1 -> list.add(new ClientPrim.ScreenFlash(buf.readInt(), buf.readVarInt()));
                case 2 -> list.add(new ClientPrim.HitPause(buf.readVarInt()));
                case 3 -> list.add(new ClientPrim.FloatingText(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readString(), buf.readInt(), buf.readVarInt()));
                case 4 -> list.add(new ClientPrim.Vignette(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                default -> throw new IllegalStateException("Unknown VFX client primitive tag: " + tag);
            }
        }
        return new VfxClientPayload(id, phase, list);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    @Override public boolean equals(Object o) {
        return o instanceof VfxClientPayload p
            && runId.equals(p.runId) && phaseIndex == p.phaseIndex && primitives.equals(p.primitives);
    }
    @Override public int hashCode() { return Objects.hash(runId, phaseIndex, primitives); }
}
