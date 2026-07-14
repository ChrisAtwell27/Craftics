package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: toggles the client's "in a merchant scene" flag AND carries the scene's
 * grid bounds so the client can seed {@code CombatState}'s arena origin/size for
 * {@link com.crackedgames.craftics.client.TileRaycast TileRaycast} (a scene never
 * calls {@code enterCombat}, so without this the raycast reads all-zero bounds and
 * every floor click resolves to null). Sent {@code active=true} with real bounds at
 * the end of {@code SceneController.build}, and {@code active=false} with zero bounds
 * on leave. Distinct from the cinematic-active flag, which non-combat EVENTS
 * (shrine/trader/vault) also raise and so can't mean "in scene".
 *
 * <p>Bounds convention (must match {@code TileRaycast}): {@code ox}/{@code oz} are the
 * floor-block origin; {@code oy} is the floor-BLOCK Y (one below the walkable top
 * surface), because {@code TileRaycast} intersects the floor plane at
 * {@code arenaOriginY + 1}. {@code w}/{@code h} are the floor footprint (x span / z span).
 *
 * <p>{@code boothData} carries the scene's REAL booth rectangles in WORLD coords, one
 * per entry: {@code minX,minZ,maxX,maxZ,occupied} (occupied 1/0), semicolon-separated,
 * empty when inactive. The client's booth-glow renderer used to rebuild the PROCEDURAL
 * layout locally, which glowed phantom rectangles that had nothing to do with a
 * schematic hall's actual booths.
 */
public record SceneStatePayload(boolean active, int ox, int oy, int oz, int w, int h,
                                String boothData)
        implements CustomPayload {

    public static final CustomPayload.Id<SceneStatePayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "scene_state"));

    // Manual codec: 7 fields outruns the tuple() helper arities available everywhere.
    public static final PacketCodec<RegistryByteBuf, SceneStatePayload> CODEC =
        PacketCodec.of(SceneStatePayload::encode, SceneStatePayload::decode);

    private void encode(RegistryByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeInt(ox);
        buf.writeInt(oy);
        buf.writeInt(oz);
        buf.writeInt(w);
        buf.writeInt(h);
        buf.writeString(boothData != null ? boothData : "");
    }

    private static SceneStatePayload decode(RegistryByteBuf buf) {
        return new SceneStatePayload(buf.readBoolean(), buf.readInt(), buf.readInt(),
            buf.readInt(), buf.readInt(), buf.readInt(), buf.readString());
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
