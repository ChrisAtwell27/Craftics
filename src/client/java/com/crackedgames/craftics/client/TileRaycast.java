package com.crackedgames.craftics.client;

import com.crackedgames.craftics.core.GridPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Raycasts from the screen cursor through the combat camera
 * to the arena floor plane, returning the GridPos under the cursor.
 */
public class TileRaycast {

    private static GridPos lastDebugPos = null;

    public static GridPos getLastDebugPos() { return lastDebugPos; }

    public static GridPos getGridPosUnderCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null || client.world == null) return null;

        // Use the actual Camera object for position/rotation
        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();
        float pitch = camera.getPitch();
        float yaw = camera.getYaw();
        int originX = CombatState.getArenaOriginX();
        int originY = CombatState.getArenaOriginY();
        int originZ = CombatState.getArenaOriginZ();
        int arenaW = CombatState.getArenaWidth();
        int arenaH = CombatState.getArenaHeight();

        // Mouse coords are in screen space; window dimensions may be framebuffer space on HiDPI.
        // Use GLFW window size (screen coords) to match mouse coords.
        long handle = client.getWindow().getHandle();
        int[] screenW = new int[1], screenH = new int[1];
        org.lwjgl.glfw.GLFW.glfwGetWindowSize(handle, screenW, screenH);
        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();
        int winW = screenW[0];
        int winH = screenH[0];

        if (winW == 0 || winH == 0) return null;

        // Normalized device coordinates (-1 to 1)
        double ndcX = (2.0 * mouseX / winW) - 1.0;
        double ndcY = 1.0 - (2.0 * mouseY / winH);

        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);

        // Camera basis vectors
        double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fwdY = -Math.sin(pitchRad);
        double fwdZ = Math.cos(yawRad) * Math.cos(pitchRad);

        // Right vector in Minecraft coords (+X=East, +Z=South): negate cos/sin
        double rightX = -Math.cos(yawRad);
        double rightY = 0;
        double rightZ = -Math.sin(yawRad);

        // Up = right cross forward
        double upX = rightY * fwdZ - rightZ * fwdY;
        double upY = rightZ * fwdX - rightX * fwdZ;
        double upZ = rightX * fwdY - rightY * fwdX;

        // FOV and aspect
        double fov = Math.toRadians(client.options.getFov().getValue());
        double aspect = (double) winW / winH;
        double tanHalfFov = Math.tan(fov / 2.0);

        // Ray direction in world space
        double rayDirX = fwdX + ndcX * tanHalfFov * aspect * rightX + ndcY * tanHalfFov * upX;
        double rayDirY = fwdY + ndcX * tanHalfFov * aspect * rightY + ndcY * tanHalfFov * upY;
        double rayDirZ = fwdZ + ndcX * tanHalfFov * aspect * rightZ + ndcY * tanHalfFov * upZ;

        // World raycast first: catches elevated blocks (Creaking Heart and
        // any other block-based enemy whose collider sits at floor+1) that the
        // flat plane intersection below would pass through. If the cursor ray
        // hits any block ABOVE the arena floor within the arena footprint, use
        // its column for the grid coord. Otherwise fall through to the plane
        // intersection so empty/low tiles still target correctly.
        if (arenaW > 0 && arenaH > 0) {
            double rayLen = 64.0;
            Vec3d start = camPos;
            Vec3d end = camPos.add(rayDirX * rayLen, rayDirY * rayLen, rayDirZ * rayLen);
            net.minecraft.util.hit.BlockHitResult worldHit = client.world.raycast(
                new net.minecraft.world.RaycastContext(start, end,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    client.player));
            boolean blockHitValid = worldHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK;
            double blockHitDistSq = blockHitValid
                ? camPos.squaredDistanceTo(worldHit.getPos()) : Double.MAX_VALUE;

            // Entity raycast: pointing at a mob's visible body should target the
            // tile it stands on, even when the cursor ray would otherwise land on
            // the tile behind it (common at low camera angles on tall mobs).
            // Invisible (stealthed) mobs are skipped so their hidden position
            // can't be discovered by sweeping the cursor around. A block hit
            // closer than the entity still wins so mobs can't be picked through
            // walls.
            GridPos entityTile = pickEntityTile(client, start, end, blockHitDistSq,
                originX, originY, originZ, arenaW, arenaH);
            if (entityTile != null) {
                lastDebugPos = entityTile;
                return entityTile;
            }

            if (blockHitValid) {
                net.minecraft.util.math.BlockPos bp = worldHit.getBlockPos();
                int gx = bp.getX() - originX;
                int gz = bp.getZ() - originZ;
                // Only honor block hits inside the arena footprint and at or
                // above the floor (skip ceiling clips from above-arena debris).
                if (gx >= 0 && gx < arenaW && gz >= 0 && gz < arenaH
                        && bp.getY() >= originY + 1 && bp.getY() <= originY + 4
                        && CombatState.isInPolygon(gx, gz)) {
                    lastDebugPos = new GridPos(gx, gz);
                    return new GridPos(gx, gz);
                }
            }
        }

        // Intersect ray with the floor plane at Y = arenaOriginY (the block surface)
        double floorY = CombatState.getArenaOriginY() + 1.0; // top of floor blocks

        if (Math.abs(rayDirY) < 1e-10) return null;

        double t = (floorY - camPos.y) / rayDirY;
        if (t < 0) return null;

        double hitX = camPos.x + rayDirX * t;
        double hitZ = camPos.z + rayDirZ * t;

        // Bias away from exact block edges to prevent jank at tile boundaries
        double fracX = hitX - Math.floor(hitX);
        double fracZ = hitZ - Math.floor(hitZ);
        if (fracX < 0.05) hitX += 0.05;
        else if (fracX > 0.95) hitX -= 0.05;
        if (fracZ < 0.05) hitZ += 0.05;
        else if (fracZ > 0.95) hitZ -= 0.05;

        // Convert to grid position
        int gridX = (int) Math.floor(hitX) - CombatState.getArenaOriginX();
        int gridZ = (int) Math.floor(hitZ) - CombatState.getArenaOriginZ();

        lastDebugPos = new GridPos(gridX, gridZ);

        if (gridX < 0 || gridX >= CombatState.getArenaWidth()
            || gridZ < 0 || gridZ >= CombatState.getArenaHeight()
            || !CombatState.isInPolygon(gridX, gridZ)) {
            lastPlanePos = null;
            return null;
        }

        GridPos candidate = applyEdgeHysteresis(new GridPos(gridX, gridZ), hitX, hitZ);
        lastPlanePos = candidate;
        return candidate;
    }

    /** Previous plane-pick result, kept for edge hysteresis. */
    private static GridPos lastPlanePos = null;

    /** How far (in tile fractions) the cursor must travel INTO a neighboring
     *  tile before the hover switches to it. Kills the per-pixel flicker when
     *  the cursor rides a tile boundary. */
    private static final double EDGE_HYSTERESIS = 0.08;

    /**
     * Keep the previously hovered tile while the hit point is still within the
     * hysteresis band just inside an adjacent candidate tile. Applies only to
     * floor-plane picks; entity/block picks snap hard (they're unambiguous).
     */
    private static GridPos applyEdgeHysteresis(GridPos candidate, double hitX, double hitZ) {
        GridPos prev = lastPlanePos;
        if (prev == null || candidate.equals(prev)) return candidate;
        int dx = candidate.x() - prev.x();
        int dz = candidate.z() - prev.z();
        if (Math.abs(dx) + Math.abs(dz) != 1) return candidate; // only cardinal neighbors wobble
        double fx = hitX - Math.floor(hitX);
        double fz = hitZ - Math.floor(hitZ);
        // Distance traveled into the candidate tile from the shared edge.
        double into;
        if (dx > 0) into = fx;
        else if (dx < 0) into = 1.0 - fx;
        else if (dz > 0) into = fz;
        else into = 1.0 - fz;
        return into < EDGE_HYSTERESIS ? prev : candidate;
    }

    /**
     * How far beyond the arena footprint the entity pick still looks, in blocks. Wide enough
     * to cover a boss parked off the stage (the Wailing Revenant hovers ten blocks past the
     * front edge). Entities out here are only ever pickable if they are combat enemies with
     * registered arena tiles, so widening the net cannot make scenery clickable.
     */
    private static final int OFF_STAGE_PICK_MARGIN = 16;

    /**
     * Ray-test the bounding boxes of mobs and players in and around the arena and
     * return the grid tile of the nearest one hit, or {@code null}. Hits
     * farther away than {@code maxDistSq} (the nearest block hit) are ignored
     * so entities can't be picked through walls.
     *
     * <p>An enemy does not have to be standing on the grid to be picked. A boss parked off
     * the stage is registered onto a block of arena tiles that acts as its targeting surface,
     * and clicking its body resolves to the registered tile NEAREST THE PLAYER - the one that
     * gives the shot its best chance of being in range. Without this the only way to shoot
     * the Wailing Revenant was to click a bare patch of floor on the front row and know that
     * the ghast hovering ten blocks past the edge was somehow "there".
     */
    private static GridPos pickEntityTile(MinecraftClient client, Vec3d start, Vec3d end,
                                          double maxDistSq, int originX, int originY, int originZ,
                                          int arenaW, int arenaH) {
        net.minecraft.util.math.Box arenaBox = new net.minecraft.util.math.Box(
            originX - OFF_STAGE_PICK_MARGIN, originY - OFF_STAGE_PICK_MARGIN,
            originZ - OFF_STAGE_PICK_MARGIN,
            originX + arenaW + OFF_STAGE_PICK_MARGIN, originY + OFF_STAGE_PICK_MARGIN,
            originZ + arenaH + OFF_STAGE_PICK_MARGIN);

        net.minecraft.entity.Entity best = null;
        double bestDistSq = maxDistSq;
        for (net.minecraft.entity.Entity e : client.world.getOtherEntities(client.player, arenaBox)) {
            boolean pickable = e instanceof net.minecraft.entity.mob.MobEntity
                || e instanceof net.minecraft.entity.player.PlayerEntity;
            if (!pickable || !e.isAlive() || e.isInvisible()) continue;
            // Darkness fog-of-war: an enemy hidden from this client shouldn't be
            // hover-pickable via its (unrendered) body - that would leak its
            // position through the target highlight.
            if (CombatState.isEnemyHiddenByDarkness(e.getId())) continue;
            // Outside the footprint, only a registered combat enemy may be picked. The
            // margin above sweeps in whatever else happens to be standing near the arena,
            // and a stray cow closer to the camera than the boss must not win the ray and
            // then resolve to nothing.
            if (!onArenaFootprint(e, originX, originZ, arenaW, arenaH)
                    && registeredTileNearestPlayer(client, e.getId()) == null) {
                continue;
            }
            var hit = e.getBoundingBox().expand(0.08).raycast(start, end);
            if (hit.isEmpty()) continue;
            double distSq = start.squaredDistanceTo(hit.get());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = e;
            }
        }
        if (best == null) return null;

        int gx = (int) Math.floor(best.getX()) - originX;
        int gz = (int) Math.floor(best.getZ()) - originZ;
        if (gx < 0 || gx >= arenaW || gz < 0 || gz >= arenaH
            || !CombatState.isInPolygon(gx, gz)) {
            // Body is off the grid: fall back to its registered targeting tiles.
            return registeredTileNearestPlayer(client, best.getId());
        }
        return new GridPos(gx, gz);
    }

    /** True if the entity's own position lands on a real tile of the arena. */
    private static boolean onArenaFootprint(net.minecraft.entity.Entity e,
                                            int originX, int originZ, int arenaW, int arenaH) {
        int gx = (int) Math.floor(e.getX()) - originX;
        int gz = (int) Math.floor(e.getZ()) - originZ;
        return gx >= 0 && gx < arenaW && gz >= 0 && gz < arenaH && CombatState.isInPolygon(gx, gz);
    }

    /**
     * The arena tile registered to {@code entityId} that is closest to the player, or null if
     * this entity has no registered tiles. Manhattan distance, matching the metric the server
     * uses for ranged reach, so the tile this returns is the one most likely to be in range.
     */
    private static GridPos registeredTileNearestPlayer(MinecraftClient client, int entityId) {
        GridPos playerPos = ClientGridHelper.getPlayerGridPos(client);
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (var entry : CombatState.getEnemyGridMap().entrySet()) {
            if (entry.getValue() == null || entry.getValue() != entityId) continue;
            GridPos tile = entry.getKey();
            int dist = playerPos == null ? 0
                : Math.abs(playerPos.x() - tile.x()) + Math.abs(playerPos.z() - tile.z());
            if (best == null || dist < bestDist) {
                best = tile;
                bestDist = dist;
            }
        }
        return best;
    }
}
