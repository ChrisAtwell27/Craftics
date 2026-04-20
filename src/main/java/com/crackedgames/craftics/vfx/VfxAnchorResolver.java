package com.crackedgames.craftics.vfx;

import com.crackedgames.craftics.core.GridPos;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves VfxAnchor → Vec3d at phase-fire time.
 *
 * Pure logic except for AtEntity, which needs a world for entity lookup.
 * World may be null for unit tests that don't use AtEntity.
 */
public final class VfxAnchorResolver {

    private final VfxContext ctx;
    @Nullable private final ServerWorld world;

    public VfxAnchorResolver(VfxContext ctx, @Nullable ServerWorld world) {
        this.ctx = ctx;
        this.world = world;
    }

    public Vec3d resolve(VfxAnchor anchor) {
        return switch (anchor) {
            case VfxAnchor.AtPos a -> a.pos();
            case VfxAnchor.AtEntity a -> resolveEntity(a.entityId(), a.offset());
            case VfxAnchor.AtGridTile a -> resolveGrid(a);
            case VfxAnchor.AtOrigin a -> resolveEntityOrBlock(ctx.originEntityId(), ctx.originBlock());
            case VfxAnchor.AtTarget a -> resolveEntityOrBlock(ctx.targetEntityId(), ctx.targetBlock());
        };
    }

    private Vec3d resolveEntity(int entityId, Vec3d offset) {
        if (world == null) return Vec3d.ZERO;
        Entity e = world.getEntityById(entityId);
        if (e == null) return Vec3d.ZERO;
        return new Vec3d(e.getX() + offset.x, e.getY() + offset.y, e.getZ() + offset.z);
    }

    private Vec3d resolveEntityOrBlock(int entityId, BlockPos fallback) {
        if (world != null && entityId != -1) {
            Entity e = world.getEntityById(entityId);
            if (e != null) {
                return new Vec3d(e.getX(), e.getY() + 1.0, e.getZ());
            }
        }
        return new Vec3d(fallback.getX() + 0.5, fallback.getY() + 1.0, fallback.getZ() + 0.5);
    }

    private Vec3d resolveGrid(VfxAnchor.AtGridTile a) {
        if (ctx.arena() == null) return Vec3d.ZERO;
        BlockPos b = ctx.arena().gridToBlockPos(new GridPos(a.gx(), a.gz()));
        return new Vec3d(b.getX() + 0.5, b.getY() + a.yOffset(), b.getZ() + 0.5);
    }
}
