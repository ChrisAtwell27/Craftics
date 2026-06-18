package com.crackedgames.craftics.client;

import com.crackedgames.craftics.core.GridPos;
//? if <=1.21.4 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Renders colored translucent quads on arena tiles for highlights.
 * Replaces the old TileHighlightManager carpet-block system.
 * All rendering is client-side - zero server round-trips.
 *
 * <p>All highlight logic lives in {@link #buildQuads} (version-independent);
 * the per-version branches only flush the resulting quad list to the GPU.
 * Pulse animations are wall-clock based so they run at the same speed at any
 * frame rate.
 */
public class TileOverlayRenderer {

    /** One colored ground quad in world space: corner span, height, RGBA. */
    private record Quad(float x0, float z0, float x1, float z1, float y,
                        float r, float g, float b, float a) {}

    /** Inset of a tile fill from the block edges. */
    private static final float TILE_MARGIN = 0.05f;
    /** Width of region/hover outline strips. */
    private static final float EDGE_THICKNESS = 0.07f;

    private static final GridPos[] CARDINALS = {
        new GridPos(0, -1), new GridPos(0, 1), new GridPos(-1, 0), new GridPos(1, 0)
    };

    // Move-path preview cache: BFS from the player tile to the hovered move
    // tile, recomputed only when either endpoint changes.
    private static GridPos cachedPathFrom = null;
    private static GridPos cachedPathTo = null;
    private static List<GridPos> cachedPath = null;

    /**
     * The movement path currently previewed (player tile -> hovered move tile,
     * excluding the start tile), or {@code null} when no preview is active.
     * Read by {@code CombatHudOverlay} for the move-cost cursor label.
     */
    public static List<GridPos> getActivePath() { return cachedPath; }

    /** Destination of {@link #getActivePath()}, or {@code null}. */
    public static GridPos getActivePathTarget() { return cachedPathTo; }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!CombatState.isInCombat()) return;
            if (MinecraftClient.getInstance().options.hudHidden) return;
            //? if <=1.21.4 {
            render(context.matrixStack(), context.camera());
            //?} else
            /*renderV5(context);*/
        });
    }

    /** Wall-clock seconds for frame-rate-independent pulse animations. */
    private static float timeSeconds() {
        return (System.currentTimeMillis() % 600_000L) / 1000.0f;
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
        // Snow layers (cold-biome arena decoration, plus snow golem trails) sit
        // on the floor at 0.125 per layer; without this the highlight draws at
        // floor level and the snow buries it: telegraphs and the move grid
        // disappeared on snowed-over tiles.
        if (above.getBlock() instanceof net.minecraft.block.SnowBlock) {
            int layers = above.get(net.minecraft.block.SnowBlock.LAYERS);
            return originY + 1.01f + layers * 0.125f;
        }
        if (above.getBlock() instanceof net.minecraft.block.StairsBlock) {
            return originY + 1.51f;
        }
        if (above.getBlock() instanceof net.minecraft.block.SlabBlock) {
            net.minecraft.block.enums.SlabType slabType =
                above.get(net.minecraft.block.SlabBlock.TYPE);
            if (slabType == net.minecraft.block.enums.SlabType.BOTTOM) {
                return originY + 1.51f;
            }
            // TOP / DOUBLE slab -> walk on top at full +1 step
            return originY + 2.01f;
        }
        if (!above.isAir() && above.isSolidBlock(world, abovePos)) {
            return originY + 2.01f;
        }
        return originY + 1.01f;
    }

    // Quad assembly (shared across versions)

    /** Full-tile fill quad (inset by {@link #TILE_MARGIN}). */
    private static void fillTile(List<Quad> out, net.minecraft.client.world.ClientWorld world,
                                 int ox, int oy, int oz, GridPos tile, float yOff,
                                 float r, float g, float b, float a) {
        float y = tileRenderY(world, ox, oy, oz, tile.x(), tile.z()) + yOff;
        float wx = ox + tile.x();
        float wz = oz + tile.z();
        out.add(new Quad(wx + TILE_MARGIN, wz + TILE_MARGIN,
            wx + 1 - TILE_MARGIN, wz + 1 - TILE_MARGIN, y, r, g, b, a));
    }

    /** Inner marker quad (path breadcrumbs): a small centered square. */
    private static void markTile(List<Quad> out, net.minecraft.client.world.ClientWorld world,
                                 int ox, int oy, int oz, GridPos tile, float yOff, float margin,
                                 float r, float g, float b, float a) {
        float y = tileRenderY(world, ox, oy, oz, tile.x(), tile.z()) + yOff;
        float wx = ox + tile.x();
        float wz = oz + tile.z();
        out.add(new Quad(wx + margin, wz + margin, wx + 1 - margin, wz + 1 - margin, y, r, g, b, a));
    }

    /**
     * Outline the border of a tile region: each tile contributes an edge strip
     * on every side whose neighbor is NOT in the region. A single tile gets a
     * full ring; a connected blob gets one crisp perimeter, which reads far
     * better than the soft fill alone.
     */
    private static void outlineRegion(List<Quad> out, net.minecraft.client.world.ClientWorld world,
                                      int ox, int oy, int oz, Set<GridPos> region, float yOff,
                                      float r, float g, float b, float a) {
        for (GridPos tile : region) {
            float y = tileRenderY(world, ox, oy, oz, tile.x(), tile.z()) + yOff;
            for (GridPos dir : CARDINALS) {
                GridPos neighbor = new GridPos(tile.x() + dir.x(), tile.z() + dir.z());
                if (region.contains(neighbor)) continue;
                edgeQuad(out, ox, oz, tile, dir, y, r, g, b, a);
            }
        }
    }

    /** Full four-edge outline ring around a single tile. */
    private static void outlineTile(List<Quad> out, net.minecraft.client.world.ClientWorld world,
                                    int ox, int oy, int oz, GridPos tile, float yOff,
                                    float r, float g, float b, float a) {
        float y = tileRenderY(world, ox, oy, oz, tile.x(), tile.z()) + yOff;
        for (GridPos dir : CARDINALS) {
            edgeQuad(out, ox, oz, tile, dir, y, r, g, b, a);
        }
    }

    /**
     * One edge strip of a tile in direction {@code dir}. North/south strips
     * span the full (inset) width; east/west strips are shortened by the strip
     * thickness so corners aren't double-drawn (translucent overlap would
     * render as bright dots).
     */
    private static void edgeQuad(List<Quad> out, int ox, int oz, GridPos tile, GridPos dir,
                                 float y, float r, float g, float b, float a) {
        float wx = ox + tile.x();
        float wz = oz + tile.z();
        float m = TILE_MARGIN;
        float t = EDGE_THICKNESS;
        float x0 = wx + m, x1 = wx + 1 - m;
        float z0 = wz + m, z1 = wz + 1 - m;
        if (dir.z() < 0) {        // north edge
            out.add(new Quad(x0, z0, x1, z0 + t, y, r, g, b, a));
        } else if (dir.z() > 0) { // south edge
            out.add(new Quad(x0, z1 - t, x1, z1, y, r, g, b, a));
        } else if (dir.x() < 0) { // west edge
            out.add(new Quad(x0, z0 + t, x0 + t, z1 - t, y, r, g, b, a));
        } else {                  // east edge
            out.add(new Quad(x1 - t, z0 + t, x1, z1 - t, y, r, g, b, a));
        }
    }

    /** Brighten an RGB triple toward white for outline accents. */
    private static float lighten(float channel) {
        return Math.min(1.0f, channel + 0.25f);
    }

    /**
     * Build every highlight quad for this frame, in paint order (the debug-quads
     * layer doesn't write depth, so later quads draw on top of earlier ones).
     *
     * <p>{@code xray} receives a faint copy of the navigation-critical quads
     * (move/attack fills, path, hover). It's drawn with a GREATER depth test,
     * so it only shows where terrain occludes the normal pass, keeping the
     * move region readable behind obstacles at low camera angles.
     */
    private static void buildQuads(MinecraftClient mc, List<Quad> out, List<Quad> xray) {
        net.minecraft.client.world.ClientWorld world = mc.world;
        int ox = CombatState.getArenaOriginX();
        int oy = CombatState.getArenaOriginY();
        int oz = CombatState.getArenaOriginZ();

        boolean colorblind = false;
        try {
            colorblind = com.crackedgames.craftics.CrafticsMod.CONFIG.colorblindMode();
        } catch (Exception ignored) {}

        float time = timeSeconds();

        // Blindness completely hides boss telegraphs and enemy movement patterns:
        // the player literally can't read the battlefield while blinded.
        boolean blind = CombatState.hasBlindness();

        // Threat overlay (hotkey toggle): every tile an enemy could reach and
        // attack this turn. Drawn FIRST so the player's own move/attack
        // highlights stay dominant on overlapping tiles.
        if (!blind) {
            Set<GridPos> threat = CombatState.getThreatTiles();
            if (!threat.isEmpty()) {
                float r = colorblind ? 0.95f : 0.85f;
                float g = colorblind ? 0.45f : 0.15f;
                float b = colorblind ? 0.10f : 0.25f;
                for (GridPos tile : threat) {
                    fillTile(out, world, ox, oy, oz, tile, 0.002f, r, g, b, 0.16f);
                }
                outlineRegion(out, world, ox, oy, oz, threat, 0.0108f, r, g, b, 0.45f);
            }
        }

        // Move tiles (light blue / lime) with a crisp perimeter outline.
        Set<GridPos> moveTiles = CombatState.getMoveTiles();
        {
            float r = colorblind ? 0.2f : 0.2f;
            float g = colorblind ? 0.9f : 0.6f;
            float b = colorblind ? 0.2f : 1.0f;
            for (GridPos tile : moveTiles) {
                fillTile(out, world, ox, oy, oz, tile, 0f, r, g, b, 0.35f);
                fillTile(xray, world, ox, oy, oz, tile, 0f, r, g, b, 0.13f);
            }
            outlineRegion(out, world, ox, oy, oz, moveTiles, 0.0112f,
                lighten(r), lighten(g), lighten(b), 0.8f);
        }

        // Attack tiles (red / yellow) with per-tile outlines (these are usually
        // scattered enemy tiles, so the region border outlines each one).
        Set<GridPos> attackTiles = CombatState.getAttackTiles();
        {
            float r = colorblind ? 0.9f : 1.0f;
            float g = colorblind ? 0.9f : 0.2f;
            float b = colorblind ? 0.2f : 0.2f;
            for (GridPos tile : attackTiles) {
                fillTile(out, world, ox, oy, oz, tile, 0f, r, g, b, 0.35f);
                fillTile(xray, world, ox, oy, oz, tile, 0f, r, g, b, 0.13f);
            }
            outlineRegion(out, world, ox, oy, oz, attackTiles, 0.0114f,
                lighten(r), lighten(g), lighten(b), 0.85f);
        }

        // Danger tiles (orange).
        for (GridPos tile : CombatState.getDangerTiles()) {
            fillTile(out, world, ox, oy, oz, tile, 0f, 1.0f, 0.6f, 0.1f, 0.25f);
        }

        // The netherite mount's 1x3 footprint side tiles (steely blue-grey, reads
        // as "the golem's body," distinct from move/attack/danger highlights). Persistent
        // so the player can see the mount occupies 3 tiles; enemies can't enter these.
        for (GridPos tile : CombatState.getMountTiles()) {
            fillTile(out, world, ox, oy, oz, tile, 0.004f, 0.5f, 0.55f, 0.78f, 0.55f);
        }

        // Boss attack warning tiles (pulsing bright red). The crisp pulsing
        // perimeter separates "the boss will strike HERE" from the softer
        // danger/threat washes, and the xray ghost keeps the telegraph
        // readable when terrain occludes it at low camera angles. A
        // telegraph you can't see is a hit you can't dodge.
        if (!blind && !CombatState.getWarningTiles().isEmpty()) {
            Set<GridPos> warningTiles = CombatState.getWarningTiles();
            float warningPulse = (float) (0.4 + 0.2 * Math.sin(time * 9.0));
            for (GridPos tile : warningTiles) {
                fillTile(out, world, ox, oy, oz, tile, 0.01f, 1.0f, 0.1f, 0.1f, warningPulse);
                fillTile(xray, world, ox, oy, oz, tile, 0.01f, 1.0f, 0.1f, 0.1f, 0.15f);
            }
            outlineRegion(out, world, ox, oy, oz, warningTiles, 0.0118f,
                1.0f, 0.45f, 0.2f, Math.min(1.0f, warningPulse + 0.35f));
        }

        // Hovered enemy's movement range (purple).
        if (!blind) {
            float r = colorblind ? 0.9f : 0.7f;
            float g = colorblind ? 0.5f : 0.3f;
            float b = colorblind ? 0.9f : 0.9f;
            for (GridPos tile : CombatState.getHoveredEnemyMoveTiles()) {
                fillTile(out, world, ox, oy, oz, tile, 0f, r, g, b, 0.3f);
            }
        }

        // Teammate hovers (dim with fade).
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, GridPos> entry : CombatState.getTeammateHovers().entrySet()) {
            Long timestamp = CombatState.getTeammateHoverTimestamps().get(entry.getKey());
            if (timestamp == null) continue;
            long age = now - timestamp;
            if (age > 1000) continue;
            float fadeAlpha = age > 500 ? 0.2f * (1.0f - (age - 500) / 500.0f) : 0.2f;
            if (fadeAlpha <= 0) continue;
            fillTile(out, world, ox, oy, oz, entry.getValue(), 0f, 0.9f, 0.9f, 0.9f, fadeAlpha);
        }

        // Attack AoE preview for the hovered tile (amber = damage, cyan =
        // effect-only like Density pull / Wind Burst). Reuses the server's
        // AoeShapes geometry so the preview matches the real hit. Damage tiles
        // get an outline - they're also valid click targets (empty-tile AoE).
        GridPos hover = CombatState.getHoveredTile();
        if (!blind && hover != null) {
            AttackAoePreview.Preview ap = AttackAoePreview.compute(mc, hover);
            for (GridPos tile : ap.effectTiles()) {
                fillTile(out, world, ox, oy, oz, tile, 0.006f, 0.2f, 0.7f, 1.0f, 0.25f);
            }
            for (GridPos tile : ap.damageTiles()) {
                fillTile(out, world, ox, oy, oz, tile, 0.007f, 1.0f, 0.75f, 0.1f, 0.35f);
            }
            outlineRegion(out, world, ox, oy, oz, ap.damageTiles(), 0.0116f,
                1.0f, 0.85f, 0.3f, 0.7f);
        }

        // AoE tile flashes (server-sent on attack resolve): hold bright, then fade.
        List<CombatState.TileFlash> flashes = CombatState.getTileFlashes();
        flashes.removeIf(f -> now - f.startMs() > f.durationMs());
        for (CombatState.TileFlash f : flashes) {
            long age = now - f.startMs();
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
            for (GridPos tile : f.tiles()) {
                fillTile(out, world, ox, oy, oz, tile, 0.008f, r, g, b, alpha);
            }
        }

        // Move-path preview: breadcrumb squares from the player to the hovered
        // move tile, so the route (and anything it skirts) is visible before
        // committing. Only on the local player's turn in MOVE mode.
        updatePathPreview(mc, hover, moveTiles);
        if (cachedPath != null && cachedPath.size() > 1) {
            for (int i = 0; i < cachedPath.size() - 1; i++) { // skip the hover tile itself
                markTile(out, world, ox, oy, oz, cachedPath.get(i), 0.012f, 0.38f,
                    0.55f, 1.0f, 0.75f, 0.55f);
                markTile(xray, world, ox, oy, oz, cachedPath.get(i), 0.012f, 0.38f,
                    0.55f, 1.0f, 0.75f, 0.25f);
            }
        }

        // Hover tile (bright pulsing fill + outline ring) - LAST so it renders on top.
        if (hover != null) {
            float pulse = (float) (0.45 + 0.1 * Math.sin(time * 4.8));
            boolean isAttackTile = attackTiles.contains(hover);
            float r, g, b;
            if (isAttackTile) {
                r = 1.0f;
                g = colorblind ? 1.0f : 0.3f;
                b = 0.3f;
            } else {
                r = 0.3f;
                g = colorblind ? 1.0f : 0.9f;
                b = colorblind ? 0.3f : 1.0f;
            }
            fillTile(out, world, ox, oy, oz, hover, 0.005f, r, g, b, pulse);
            outlineTile(out, world, ox, oy, oz, hover, 0.013f,
                lighten(r), lighten(g), lighten(b), 0.95f);
            fillTile(xray, world, ox, oy, oz, hover, 0.005f, r, g, b, pulse * 0.45f);
            outlineTile(xray, world, ox, oy, oz, hover, 0.013f,
                lighten(r), lighten(g), lighten(b), 0.4f);
        }
    }

    /** Refresh the cached BFS path when the player tile or hover target changed. */
    private static void updatePathPreview(MinecraftClient mc, GridPos hover, Set<GridPos> moveTiles) {
        boolean active = hover != null
            && CombatState.isLocalPlayersTurn()
            && moveTiles.contains(hover)
            && CombatInputHandler.getActionMode(mc) == CombatInputHandler.ActionMode.MOVE;
        if (!active) {
            cachedPathFrom = null;
            cachedPathTo = null;
            cachedPath = null;
            return;
        }
        GridPos playerPos = ClientGridHelper.getPlayerGridPos(mc);
        if (playerPos == null) {
            cachedPath = null;
            return;
        }
        if (playerPos.equals(cachedPathFrom) && hover.equals(cachedPathTo)) return;
        cachedPathFrom = playerPos;
        cachedPathTo = hover;
        // The server already bounded the move set by remaining SPD; the cap here
        // only guards the BFS against degenerate arenas.
        cachedPath = ClientGridHelper.getPathTo(mc, playerPos, hover, 64);
    }

    // Per-version GPU flush.
    // The x-ray (ghost) pass draws the same navigation quads with a GREATER
    // depth test: fragments render ONLY where world geometry occludes them,
    // so the faint copy appears exclusively behind obstacles and never doubles
    // up with the normal pass.

    //? if <=1.21.4 {
    private static void render(MatrixStack matrices, Camera camera) {
        if (matrices == null || camera == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        List<Quad> quads = new ArrayList<>();
        List<Quad> xray = new ArrayList<>();
        buildQuads(mc, quads, xray);
        if (quads.isEmpty() && xray.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        // Set up render state for translucent quads
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        //? if <=1.21.1 {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        //?} else {
        /*RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
        *///?}

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Occluded-only ghost pass first (GL_GREATER: draw where hidden).
        if (!xray.isEmpty()) {
            RenderSystem.depthMask(false);
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_GREATER);
            BufferBuilder ghost = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Quad q : xray) {
                ghost.vertex(matrix, q.x0(), q.y(), q.z0()).color(q.r(), q.g(), q.b(), q.a());
                ghost.vertex(matrix, q.x0(), q.y(), q.z1()).color(q.r(), q.g(), q.b(), q.a());
                ghost.vertex(matrix, q.x1(), q.y(), q.z1()).color(q.r(), q.g(), q.b(), q.a());
                ghost.vertex(matrix, q.x1(), q.y(), q.z0()).color(q.r(), q.g(), q.b(), q.a());
            }
            BufferRenderer.drawWithGlobalProgram(ghost.end());
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
        }

        if (!quads.isEmpty()) {
            BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Quad q : quads) {
                buffer.vertex(matrix, q.x0(), q.y(), q.z0()).color(q.r(), q.g(), q.b(), q.a());
                buffer.vertex(matrix, q.x0(), q.y(), q.z1()).color(q.r(), q.g(), q.b(), q.a());
                buffer.vertex(matrix, q.x1(), q.y(), q.z1()).color(q.r(), q.g(), q.b(), q.a());
                buffer.vertex(matrix, q.x1(), q.y(), q.z0()).color(q.r(), q.g(), q.b(), q.a());
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        // Restore render state
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }
    //?} else {
    /*/^*
     * Lazily-built see-through layer: identical to the debug-quads layer but
     * with depth test GREATER and no depth write. 1.21.5 made both
     * {@code RenderLayer.of} and the POSITION_COLOR snippet inaccessible, so
     * the pipeline replicates the snippet's shaders/uniforms explicitly and
     * the layer is constructed through the {@code RenderLayerInvoker} mixin.
     ^/
    private static RenderLayer xrayLayer = null;

    private static RenderLayer getXrayLayer() {
        if (xrayLayer == null) {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline =
                com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                    .withLocation("pipeline/craftics_tile_xray")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withUniform("ModelViewMat", net.minecraft.client.gl.UniformType.MATRIX4X4)
                    .withUniform("ProjMat", net.minecraft.client.gl.UniformType.MATRIX4X4)
                    .withUniform("ColorModulator", net.minecraft.client.gl.UniformType.VEC4)
                    .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
                    .withVertexFormat(VertexFormats.POSITION_COLOR,
                        com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthWrite(false)
                    .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.GREATER_DEPTH_TEST)
                    .build();
            RenderLayer.MultiPhaseParameters params =
                ((com.crackedgames.craftics.mixin.client.RenderPhaseBuilderInvoker) (Object)
                    RenderLayer.MultiPhaseParameters.builder()).craftics$build(false);
            xrayLayer = com.crackedgames.craftics.mixin.client.RenderLayerInvoker
                .craftics$of("craftics_tile_xray", 1536, pipeline, params);
        }
        return xrayLayer;
    }

    private static void renderV5(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        if (matrices == null || camera == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        List<Quad> quads = new ArrayList<>();
        List<Quad> xray = new ArrayList<>();
        buildQuads(mc, quads, xray);
        if (quads.isEmpty() && xray.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Occluded-only ghost pass first; switching layers flushes the batch.
        if (!xray.isEmpty()) {
            VertexConsumer gvc = context.consumers().getBuffer(getXrayLayer());
            for (Quad q : xray) {
                gvc.vertex(matrix, q.x0(), q.y(), q.z0()).color(q.r(), q.g(), q.b(), q.a());
                gvc.vertex(matrix, q.x0(), q.y(), q.z1()).color(q.r(), q.g(), q.b(), q.a());
                gvc.vertex(matrix, q.x1(), q.y(), q.z1()).color(q.r(), q.g(), q.b(), q.a());
                gvc.vertex(matrix, q.x1(), q.y(), q.z0()).color(q.r(), q.g(), q.b(), q.a());
            }
        }

        VertexConsumer vc = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        for (Quad q : quads) {
            vc.vertex(matrix, q.x0(), q.y(), q.z0()).color(q.r(), q.g(), q.b(), q.a());
            vc.vertex(matrix, q.x0(), q.y(), q.z1()).color(q.r(), q.g(), q.b(), q.a());
            vc.vertex(matrix, q.x1(), q.y(), q.z1()).color(q.r(), q.g(), q.b(), q.a());
            vc.vertex(matrix, q.x1(), q.y(), q.z0()).color(q.r(), q.g(), q.b(), q.a());
        }

        matrices.pop();
    }
    *///?}
}
