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
            || gridZ < 0 || gridZ >= CombatState.getArenaHeight()) {
            return null;
        }

        return new GridPos(gridX, gridZ);
    }
}
