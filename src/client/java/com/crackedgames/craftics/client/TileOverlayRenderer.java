package com.crackedgames.craftics.client;

import com.crackedgames.craftics.core.GridPos;
//? if <=1.21.4 {
/*import com.mojang.blaze3d.systems.RenderSystem;
*///?}
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
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
            if (net.minecraft.client.MinecraftClient.getInstance().options.hudHidden) return;
            frameCounter++;
            //? if <=1.21.4 {
            /*render(context.matrixStack(), context.camera());
            *///?} else
            renderV5(context);
        });
    }

    /**
     * Compute the highlight Y for a single arena tile. Mirrors ArenaBuilder's
     * tile classification at floor+1: a stair gives a Y+0.5 ramp, a full
     * (solid) block gives a Y+1 platform (ELEVATED), anything else is the
     * flat floor. Without this, highlights drawn at a flat originY+1.01 sink
     * inside ELEVATED blocks and slip under STAIR slopes.
     */
    private static float tileRenderY(net.minecraft.client.world.ClientWorld world,
                                      int originX, int originY, int originZ,
                                      int tileX, int tileZ) {
        if (world == null) return originY + 1.01f;
        net.minecraft.util.math.BlockPos abovePos =
            new net.minecraft.util.math.BlockPos(originX + tileX, originY + 1, originZ + tileZ);
        net.minecraft.block.BlockState above = world.getBlockState(abovePos);
        if (above.getBlock() instanceof net.minecraft.block.StairsBlock) {
            return originY + 1.51f;
        }
        if (above.getBlock() instanceof net.minecraft.block.SlabBlock) {
            net.minecraft.block.enums.SlabType slabType =
                above.get(net.minecraft.block.SlabBlock.TYPE);
            if (slabType == net.minecraft.block.enums.SlabType.BOTTOM) {
                return originY + 1.51f;
            }
            // TOP / DOUBLE slab → walk on top at full +1 step
            return originY + 2.01f;
        }
        if (!above.isAir() && above.isSolidBlock(world, abovePos)) {
            return originY + 2.01f;
        }
        return originY + 1.01f;
    }

    //? if <=1.21.4 {
    /*private static void render(MatrixStack matrices, Camera camera) {
        if (matrices == null || camera == null) return;

        Vec3d camPos = camera.getPos();
        int originX = CombatState.getArenaOriginX();
        int originY = CombatState.getArenaOriginY();
        int originZ = CombatState.getArenaOriginZ();
        net.minecraft.client.world.ClientWorld world = net.minecraft.client.MinecraftClient.getInstance().world;

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
        //? if <=1.21.1 {
        /^RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        ^///?} else {
        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
        //?}

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();

        // Draw move tiles (light blue / lime)
        for (GridPos tile : CombatState.getMoveTiles()) {
            float r = colorblind ? 0.2f : 0.2f;
            float g = colorblind ? 0.9f : 0.6f;
            float b = colorblind ? 0.2f : 1.0f;
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
            drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(), r, g, b, 0.35f);
        }

        // Draw attack tiles (red / yellow)
        for (GridPos tile : CombatState.getAttackTiles()) {
            float r = colorblind ? 0.9f : 1.0f;
            float g = colorblind ? 0.9f : 0.2f;
            float b = colorblind ? 0.2f : 0.2f;
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
            drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(), r, g, b, 0.35f);
        }

        // Draw danger tiles (orange)
        for (GridPos tile : CombatState.getDangerTiles()) {
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
            drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(), 1.0f, 0.6f, 0.1f, 0.25f);
        }

        // Draw the netherite mount's 1×3 footprint side tiles (steely blue-grey — reads
        // as "the golem's body"). Persistent so the player can see the 3-tile footprint.
        for (GridPos tile : CombatState.getMountTiles()) {
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.004f;
            drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(), 0.5f, 0.55f, 0.78f, 0.55f);
        }

        // Blindness completely hides boss telegraphs and enemy movement patterns —
        // the player literally can't read the battlefield while blinded.
        boolean blind = CombatState.hasBlindness();

        // Draw boss attack warning tiles (pulsing bright red)
        if (!blind && !CombatState.getWarningTiles().isEmpty()) {
            float warningPulse = (float) (0.4 + 0.2 * Math.sin(frameCounter * 0.15));
            for (GridPos tile : CombatState.getWarningTiles()) {
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.01f;
                drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(),
                    1.0f, 0.1f, 0.1f, warningPulse);
            }
        }

        // Draw hovered enemy's movement range (purple)
        if (!blind) {
            for (GridPos tile : CombatState.getHoveredEnemyMoveTiles()) {
                float r = colorblind ? 0.9f : 0.7f;
                float g = colorblind ? 0.5f : 0.3f;
                float b = colorblind ? 0.9f : 0.9f;
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
                drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(), r, g, b, 0.3f);
            }
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
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
            drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(), 0.9f, 0.9f, 0.9f, fadeAlpha);
        }

        // Draw attack AoE preview for the hovered tile (amber = damage,
        // cyan = effect-only like Density pull / Wind Burst). Reuses the
        // server's AoeShapes geometry so the preview matches the real hit.
        GridPos aoeHover = CombatState.getHoveredTile();
        if (!blind && aoeHover != null) {
            AttackAoePreview.Preview ap =
                AttackAoePreview.compute(net.minecraft.client.MinecraftClient.getInstance(), aoeHover);
            for (GridPos tile : ap.effectTiles()) {
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.006f;
                drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(),
                    0.2f, 0.7f, 1.0f, 0.25f);
            }
            for (GridPos tile : ap.damageTiles()) {
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.007f;
                drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(),
                    1.0f, 0.75f, 0.1f, 0.35f);
            }
        }

        // Draw AoE tile flashes (server-sent on attack resolve): hold bright, then fade.
        long flashNow = System.currentTimeMillis();
        java.util.List<CombatState.TileFlash> flashes = CombatState.getTileFlashes();
        flashes.removeIf(f -> flashNow - f.startMs() > f.durationMs());
        for (CombatState.TileFlash f : flashes) {
            long age = flashNow - f.startMs();
            long dur = f.durationMs();
            float fadeTail = 400f; // ms of fade-out at the end
            float baseAlpha = 0.5f;
            float alpha;
            if (age >= dur) continue;
            if (age > dur - fadeTail) {
                alpha = baseAlpha * (1.0f - (age - (dur - fadeTail)) / fadeTail);
            } else {
                alpha = baseAlpha;
            }
            if (alpha <= 0.01f) continue;
            int c = f.color();
            float r = ((c >> 16) & 0xFF) / 255.0f;
            float g = ((c >> 8) & 0xFF) / 255.0f;
            float b = (c & 0xFF) / 255.0f;
            for (com.crackedgames.craftics.core.GridPos tile : f.tiles()) {
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.008f;
                drawTileQuad(tessellator, matrix, originX + tile.x(), y, originZ + tile.z(), r, g, b, alpha);
            }
        }

        // Draw hover tile (bright, pulsing) — LAST so it renders on top
        GridPos hover = CombatState.getHoveredTile();
        if (hover != null) {
            float pulse = (float) (0.45 + 0.1 * Math.sin(frameCounter * 0.08));
            boolean isAttackTile = CombatState.getAttackTiles().contains(hover);
            float y = tileRenderY(world, originX, originY, originZ, hover.x(), hover.z()) + 0.005f;
            if (isAttackTile) {
                float r = colorblind ? 1.0f : 1.0f;
                float g = colorblind ? 1.0f : 0.3f;
                float b = colorblind ? 0.3f : 0.3f;
                drawTileQuad(tessellator, matrix, originX + hover.x(), y, originZ + hover.z(), r, g, b, pulse);
            } else {
                float r = colorblind ? 0.3f : 0.3f;
                float g = colorblind ? 1.0f : 0.9f;
                float b = colorblind ? 0.3f : 1.0f;
                drawTileQuad(tessellator, matrix, originX + hover.x(), y, originZ + hover.z(), r, g, b, pulse);
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
    *///?} else {
    private static void renderV5(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        if (matrices == null || camera == null) return;

        Vec3d camPos = camera.getPos();
        int originX = CombatState.getArenaOriginX();
        int originY = CombatState.getArenaOriginY();
        int originZ = CombatState.getArenaOriginZ();
        net.minecraft.client.world.ClientWorld world = net.minecraft.client.MinecraftClient.getInstance().world;

        boolean colorblind = false;
        try {
            colorblind = com.crackedgames.craftics.CrafticsMod.CONFIG.colorblindMode();
        } catch (Exception ignored) {}

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer vc = context.consumers().getBuffer(RenderLayer.getDebugQuads());

        // Draw move tiles (light blue / lime)
        for (GridPos tile : CombatState.getMoveTiles()) {
            float r = colorblind ? 0.2f : 0.2f;
            float g = colorblind ? 0.9f : 0.6f;
            float b = colorblind ? 0.2f : 1.0f;
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
            drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(), r, g, b, 0.35f);
        }

        // Draw attack tiles (red / yellow)
        for (GridPos tile : CombatState.getAttackTiles()) {
            float r = colorblind ? 0.9f : 1.0f;
            float g = colorblind ? 0.9f : 0.2f;
            float b = colorblind ? 0.2f : 0.2f;
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
            drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(), r, g, b, 0.35f);
        }

        // Draw danger tiles (orange)
        for (GridPos tile : CombatState.getDangerTiles()) {
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
            drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(), 1.0f, 0.6f, 0.1f, 0.25f);
        }

        // Draw the netherite mount's 1×3 footprint side tiles (steely blue-grey — reads
        // as "the golem's body," distinct from move/attack/danger highlights). Persistent
        // so the player can see the mount occupies 3 tiles; enemies can't enter these.
        for (GridPos tile : CombatState.getMountTiles()) {
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.004f;
            drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(), 0.5f, 0.55f, 0.78f, 0.55f);
        }

        boolean blind = CombatState.hasBlindness();

        // Draw boss attack warning tiles (pulsing bright red)
        if (!blind && !CombatState.getWarningTiles().isEmpty()) {
            float warningPulse = (float) (0.4 + 0.2 * Math.sin(frameCounter * 0.15));
            for (GridPos tile : CombatState.getWarningTiles()) {
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.01f;
                drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(),
                    1.0f, 0.1f, 0.1f, warningPulse);
            }
        }

        // Draw hovered enemy's movement range (purple)
        if (!blind) {
            for (GridPos tile : CombatState.getHoveredEnemyMoveTiles()) {
                float r = colorblind ? 0.9f : 0.7f;
                float g = colorblind ? 0.5f : 0.3f;
                float b = colorblind ? 0.9f : 0.9f;
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
                drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(), r, g, b, 0.3f);
            }
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
            float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z());
            drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(), 0.9f, 0.9f, 0.9f, fadeAlpha);
        }

        // Draw attack AoE preview (amber = damage, cyan = effect-only).
        GridPos aoeHover = CombatState.getHoveredTile();
        if (!blind && aoeHover != null) {
            AttackAoePreview.Preview ap =
                AttackAoePreview.compute(net.minecraft.client.MinecraftClient.getInstance(), aoeHover);
            for (GridPos tile : ap.effectTiles()) {
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.006f;
                drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(),
                    0.2f, 0.7f, 1.0f, 0.25f);
            }
            for (GridPos tile : ap.damageTiles()) {
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.007f;
                drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(),
                    1.0f, 0.75f, 0.1f, 0.35f);
            }
        }

        // Draw AoE tile flashes (server-sent on attack resolve): hold bright, then fade.
        long flashNow = System.currentTimeMillis();
        java.util.List<CombatState.TileFlash> flashes = CombatState.getTileFlashes();
        flashes.removeIf(f -> flashNow - f.startMs() > f.durationMs());
        for (CombatState.TileFlash f : flashes) {
            long age = flashNow - f.startMs();
            long dur = f.durationMs();
            float fadeTail = 400f; // ms of fade-out at the end
            float baseAlpha = 0.5f;
            float alpha;
            if (age >= dur) continue;
            if (age > dur - fadeTail) {
                alpha = baseAlpha * (1.0f - (age - (dur - fadeTail)) / fadeTail);
            } else {
                alpha = baseAlpha;
            }
            if (alpha <= 0.01f) continue;
            int c = f.color();
            float r = ((c >> 16) & 0xFF) / 255.0f;
            float g = ((c >> 8) & 0xFF) / 255.0f;
            float b = (c & 0xFF) / 255.0f;
            for (com.crackedgames.craftics.core.GridPos tile : f.tiles()) {
                float y = tileRenderY(world, originX, originY, originZ, tile.x(), tile.z()) + 0.008f;
                drawTileQuadV5(vc, matrix, originX + tile.x(), y, originZ + tile.z(), r, g, b, alpha);
            }
        }

        // Draw hover tile (bright, pulsing) — LAST so it renders on top
        GridPos hover = CombatState.getHoveredTile();
        if (hover != null) {
            float pulse = (float) (0.45 + 0.1 * Math.sin(frameCounter * 0.08));
            boolean isAttackTile = CombatState.getAttackTiles().contains(hover);
            float y = tileRenderY(world, originX, originY, originZ, hover.x(), hover.z()) + 0.005f;
            if (isAttackTile) {
                float r = colorblind ? 1.0f : 1.0f;
                float g = colorblind ? 1.0f : 0.3f;
                float b = colorblind ? 0.3f : 0.3f;
                drawTileQuadV5(vc, matrix, originX + hover.x(), y, originZ + hover.z(), r, g, b, pulse);
            } else {
                float r = colorblind ? 0.3f : 0.3f;
                float g = colorblind ? 1.0f : 0.9f;
                float b = colorblind ? 0.3f : 1.0f;
                drawTileQuadV5(vc, matrix, originX + hover.x(), y, originZ + hover.z(), r, g, b, pulse);
            }
        }

        matrices.pop();
    }

    private static void drawTileQuadV5(VertexConsumer vc, Matrix4f matrix,
                                        float x, float y, float z,
                                        float r, float g, float b, float a) {
        float margin = 0.05f;
        float x0 = x + margin;
        float x1 = x + 1.0f - margin;
        float z0 = z + margin;
        float z1 = z + 1.0f - margin;

        vc.vertex(matrix, x0, y, z0).color(r, g, b, a);
        vc.vertex(matrix, x0, y, z1).color(r, g, b, a);
        vc.vertex(matrix, x1, y, z1).color(r, g, b, a);
        vc.vertex(matrix, x1, y, z0).color(r, g, b, a);
    }
    //?}
}
