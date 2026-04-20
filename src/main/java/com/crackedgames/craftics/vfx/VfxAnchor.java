package com.crackedgames.craftics.vfx;

import net.minecraft.util.math.Vec3d;

/**
 * Where a primitive fires. Resolved at phase-fire time, not descriptor-build time,
 * so anchors like AtEntity/AtTarget follow moving entities between phases.
 */
public sealed interface VfxAnchor {
    /** Fixed world position. */
    record AtPos(Vec3d pos) implements VfxAnchor {}

    /** Follows an entity by id, applying an offset in world space. */
    record AtEntity(int entityId, Vec3d offset) implements VfxAnchor {}

    /** Grid tile (x, z) lifted by {@code yOffset} above the arena floor. Resolved via VfxContext.arena(). */
    record AtGridTile(int gx, int gz, double yOffset) implements VfxAnchor {}

    /** The context's origin entity (or origin block if originEntityId == -1). */
    record AtOrigin() implements VfxAnchor {}

    /** The context's target entity (or target block if targetEntityId == -1). */
    record AtTarget() implements VfxAnchor {}

    AtOrigin ORIGIN = new AtOrigin();
    AtTarget TARGET = new AtTarget();
}
