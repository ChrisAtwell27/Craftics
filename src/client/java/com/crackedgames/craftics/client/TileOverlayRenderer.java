package com.crackedgames.craftics.client;

import com.crackedgames.craftics.core.GridPos;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;

/**
 * Renders colored translucent quads on arena tiles for highlights.
 * Replaces the old TileHighlightManager carpet-block system.
 * All rendering is client-side — zero server round-trips.
 */
public class TileOverlayRenderer {

    private static long frameCounter = 0;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!CombatState.isInCombat()) return;
            frameCounter++;
            render(context.matrixStack(), context.camera());
        });
    }

    private static void render(MatrixStack matrices, Camera camera) {
        if (matrices == null || camera == null) return;

        Vec3d camPos = camera.getPos();
        int originX = CombatState.getArenaOriginX();
        int originY = CombatState.getArenaOriginY();
        int originZ = CombatState.getArenaOriginZ();
        float renderY = originY + 1.01f;

        boolean colorblind = false;
        try {
            colorblind = com.crackedgames.craftics.CrafticsMod.CONFIG.colorblindMode();
        } catch (Exception ignored) {}

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        // Set up render state for translucent quads
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();

        // Draw move tiles (light blue / lime)
        for (GridPos tile : CombatState.getMoveTiles()) {
            float r = colorblind ? 0.2f : 0.2f;
            float g = colorblind ? 0.9f : 0.6f;
            float b = colorblind ? 0.2f : 1.0f;
            drawTileQuad(tessellator, matrix, originX + tile.x(), renderY, originZ + tile.z(), r, g, b, 0.35f);
        }

        // Draw attack tiles (red / yellow)
        for (GridPos tile : CombatState.getAttackTiles()) {
            float r = colorblind ? 0.9f : 1.0f;
            float g = colorblind ? 0.9f : 0.2f;
            float b = colorblind ? 0.2f : 0.2f;
            drawTileQuad(tessellator, matrix, originX + tile.x(), renderY, originZ + tile.z(), r, g, b, 0.35f);
        }

        // Draw danger tiles (orange)
        for (GridPos tile : CombatState.getDangerTiles()) {
            drawTileQuad(tessellator, matrix, originX + tile.x(), renderY, originZ + tile.z(), 1.0f, 0.6f, 0.1f, 0.25f);
        }

        // Draw teammate hovers (dim with fade)
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, GridPos> entry : CombatState.getTeammateHovers().entrySet()) {
            Long timestamp = CombatState.getTeammateHoverTimestamps().get(entry.getKey());
            if (timestamp == null) continue;
            long age = now - timestamp;
            if (age > 1000) continue;
            float fadeAlpha = age > 500 ? 0.2f * (1.0f - (age - 500) / 500.0f) : 0.2f;
            if (fadeAlpha <= 0) continue;
            GridPos tile = entry.getValue();
            drawTileQuad(tessellator, matrix, originX + tile.x(), renderY, originZ + tile.z(), 0.9f, 0.9f, 0.9f, fadeAlpha);
        }

        // Draw hover tile (bright, pulsing) — LAST so it renders on top
        GridPos hover = CombatState.getHoveredTile();
        if (hover != null) {
            float pulse = (float) (0.45 + 0.1 * Math.sin(frameCounter * 0.08));
            boolean isAttackTile = CombatState.getAttackTiles().contains(hover);
            if (isAttackTile) {
                float r = colorblind ? 1.0f : 1.0f;
                float g = colorblind ? 1.0f : 0.3f;
                float b = colorblind ? 0.3f : 0.3f;
                drawTileQuad(tessellator, matrix, originX + hover.x(), renderY + 0.005f, originZ + hover.z(), r, g, b, pulse);
            } else {
                float r = colorblind ? 0.3f : 0.3f;
                float g = colorblind ? 1.0f : 0.9f;
                float b = colorblind ? 0.3f : 1.0f;
                drawTileQuad(tessellator, matrix, originX + hover.x(), renderY + 0.005f, originZ + hover.z(), r, g, b, pulse);
            }
        }

        // Restore render state
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static void drawTileQuad(Tessellator tessellator, Matrix4f matrix,
                                      float x, float y, float z,
                                      float r, float g, float b, float a) {
        float margin = 0.05f;
        float x0 = x + margin;
        float x1 = x + 1.0f - margin;
        float z0 = z + margin;
        float z1 = z + 1.0f - margin;

        int color = ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);

        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x0, y, z0).color(color);
        buffer.vertex(matrix, x0, y, z1).color(color);
        buffer.vertex(matrix, x1, y, z1).color(color);
        buffer.vertex(matrix, x1, y, z0).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
