package com.crackedgames.craftics.client;

//? if <=1.21.4 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Ground-level combat readability: a contact shadow under every combatant, an expanding ring where
 * a blow lands, and a slow pulse over sculk-sensor tiles.
 *
 * <p>Contact shadows are the load-bearing one. The tactical camera looks down at a flat board, and
 * without a shadow anchoring them, models read as hovering somewhere above their tile - which
 * matters when the tile IS the game state. The ring and the pulse are timing cues: one says a hit
 * resolved HERE, the other says this ground is listening.
 *
 * <p>Drawn as world-space quads on the same {@code AFTER_TRANSLUCENT} pass and per-shard flush
 * that {@link TileOverlayRenderer} and {@link CloudSeaRenderer} use.
 */
public final class ArenaFxRenderer {

    private ArenaFxRenderer() {}

    /** One flat ground quad: four corners at a fixed Y, flat RGBA. */
    private record Face(float x0, float z0, float x1, float z1,
                        float x2, float z2, float x3, float z3,
                        float y, float r, float g, float b, float a) {}

    /** A hit ring expanding out from a tile. */
    private record Ring(double x, double z, double y, long startMs, int color, boolean loud) {}

    /** Shadow radius for a normal-sized mob, scaled by the entity's actual width. */
    private static final float SHADOW_BASE_RADIUS = 0.42f;
    private static final float SHADOW_ALPHA = 0.34f;
    /** Shadows lift this far off the floor so they don't z-fight the tile overlay. */
    private static final float SHADOW_LIFT = 0.015f;
    /** Impact ring lifetime and how far it travels in that time. */
    private static final long RING_LIFE_MS = 420L;
    private static final float RING_RADIUS = 1.5f;
    private static final float RING_THICKNESS = 0.22f;
    /** Sculk pulse period, so the field breathes rather than strobes. */
    private static final long SCULK_PERIOD_MS = 4400L;
    /** Sculk alpha never reaches zero and never gets loud - it marks ground, it isn't an alert. */
    private static final float SCULK_ALPHA_BASE = 0.075f;
    private static final float SCULK_ALPHA_SWING = 0.035f;

    private static final List<Ring> rings = new ArrayList<>();

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

    /**
     * Ring at a struck tile. {@code loud} marks a heavy hit - it rings wider and brighter, so the
     * damage number isn't the only thing telling you the blow mattered.
     */
    public static void spawnImpact(int tileX, int tileZ, boolean loud) {
        if (!CombatState.isInCombat()) return;
        rings.add(new Ring(
            CombatState.getArenaOriginX() + tileX + 0.5,
            CombatState.getArenaOriginZ() + tileZ + 0.5,
            CombatState.getArenaOriginY() + 1.02,
            System.currentTimeMillis(),
            loud ? 0xFFE8A0 : 0xFFFFFF,
            loud));
        if (rings.size() > 24) rings.remove(0);   // a runaway fight can't grow this without bound
    }

    /** Drop pending rings (combat end / disconnect). */
    public static void reset() {
        rings.clear();
    }

    private static void buildFaces(MinecraftClient mc, List<Face> out) {
        if (mc == null || mc.world == null) return;
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        if (w <= 0 || h <= 0) return;

        int ox = CombatState.getArenaOriginX();
        int oy = CombatState.getArenaOriginY();
        int oz = CombatState.getArenaOriginZ();
        float floorY = oy + 1.0f;

        buildShadows(mc, out, ox, oy, oz, w, h);
        buildRings(out);
        buildSculkPulses(mc, out, ox, oy, oz, w, h, floorY);
    }

    /** A soft contact shadow under each living entity standing in the arena. */
    private static void buildShadows(MinecraftClient mc, List<Face> out,
                                     int ox, int oy, int oz, int w, int h) {
        Box bounds = new Box(ox - 1, oy - 2, oz - 1, ox + w + 1, oy + 6, oz + h + 1);
        List<LivingEntity> entities = mc.world.getEntitiesByClass(LivingEntity.class, bounds, e -> true);
        for (LivingEntity entity : entities) {
            if (!entity.isAlive() || entity.isSpectator()) continue;
            // Anchor to the tile's floor, not the entity's own Y: a knocked-back or mid-hop mob
            // should cast its shadow on the ground it will land on, which is what sells the drop.
            float y = oy + 1.0f + SHADOW_LIFT;
            float radius = SHADOW_BASE_RADIUS * Math.max(0.6f, Math.min(2.2f, entity.getWidth()));
            // Fade with height so a launched entity's shadow shrinks away rather than sliding
            // around underneath it at full strength.
            float above = (float) (entity.getY() - (oy + 1));
            float alpha = SHADOW_ALPHA * Math.max(0.0f, 1.0f - Math.abs(above) / 4.0f);
            if (alpha < 0.02f) continue;
            addOctagon(out, (float) entity.getX(), (float) entity.getZ(), radius, y,
                0.0f, 0.0f, 0.0f, alpha);
        }
    }

    /** Expanding hit rings, oldest first. */
    private static void buildRings(List<Face> out) {
        long now = System.currentTimeMillis();
        Iterator<Ring> it = rings.iterator();
        while (it.hasNext()) {
            Ring ring = it.next();
            float t = (now - ring.startMs()) / (float) RING_LIFE_MS;
            if (t >= 1.0f) { it.remove(); continue; }
            float ease = 1.0f - (1.0f - t) * (1.0f - t);         // fast out, slow settle
            float radius = RING_RADIUS * (ring.loud() ? 1.5f : 1.0f) * ease;
            float alpha = (1.0f - t) * (ring.loud() ? 0.8f : 0.55f);
            float r = ((ring.color() >> 16) & 0xFF) / 255.0f;
            float g = ((ring.color() >> 8) & 0xFF) / 255.0f;
            float b = (ring.color() & 0xFF) / 255.0f;
            addRing(out, (float) ring.x(), (float) ring.z(), radius, RING_THICKNESS,
                (float) ring.y(), r, g, b, alpha);
        }
    }

    /**
     * Slow pulse marking the sculk-sensor field. What the player needs from this is the
     * EDGE - where the sensor stops hearing you - so only the boundary tiles are drawn.
     *
     * <p>This used to fill every tile in the field with a near-full-tile octagon breathing
     * between fully transparent and 0.2 alpha. Over a sensor's 5x5 that is a pulsing cyan
     * slab across a quarter of the board, and it read as a strobe rather than as terrain
     * information. Now it is an outline: dimmer, slower, and it never fades fully out, so it
     * settles into the floor instead of blinking.
     */
    private static void buildSculkPulses(MinecraftClient mc, List<Face> out,
                                         int ox, int oy, int oz, int w, int h, float floorY) {
        long now = System.currentTimeMillis();
        float phase = (now % SCULK_PERIOD_MS) / (float) SCULK_PERIOD_MS;
        float alpha = (float) (SCULK_ALPHA_BASE
            + SCULK_ALPHA_SWING * Math.sin(phase * Math.PI * 2.0));
        for (int tx = 0; tx < w; tx++) {
            for (int tz = 0; tz < h; tz++) {
                if (!isSculkAt(mc, ox, oy, oz, tx, tz)) continue;
                // Interior tiles carry the sculk block's own texture already; drawing over
                // them adds nothing but glare.
                boolean edge = !isSculkAt(mc, ox, oy, oz, tx + 1, tz)
                    || !isSculkAt(mc, ox, oy, oz, tx - 1, tz)
                    || !isSculkAt(mc, ox, oy, oz, tx, tz + 1)
                    || !isSculkAt(mc, ox, oy, oz, tx, tz - 1);
                if (!edge) continue;
                addOctagon(out, ox + tx + 0.5f, oz + tz + 0.5f, 0.30f, floorY + 0.02f,
                    0.16f, 0.72f, 0.78f, alpha);
            }
        }
    }

    private static boolean isSculkAt(MinecraftClient mc, int ox, int oy, int oz, int tx, int tz) {
        return mc.world.getBlockState(new net.minecraft.util.math.BlockPos(ox + tx, oy, oz + tz))
            .isOf(net.minecraft.block.Blocks.SCULK);
    }

    /**
     * An octagon, built from an axis-aligned square and a 45-degree one. Two quads read as a
     * round blob at this size and cost a fraction of a real fan - and the faceting suits the
     * blocky look better than a smooth circle would.
     */
    private static void addOctagon(List<Face> out, float cx, float cz, float radius, float y,
                                   float r, float g, float b, float a) {
        out.add(new Face(cx - radius, cz - radius, cx - radius, cz + radius,
            cx + radius, cz + radius, cx + radius, cz - radius, y, r, g, b, a));
        float d = radius * 1.32f;
        out.add(new Face(cx - d, cz, cx, cz + d, cx + d, cz, cx, cz - d, y, r, g, b, a));
    }

    /** A square ring of four thin quads at {@code radius} from centre. */
    private static void addRing(List<Face> out, float cx, float cz, float radius, float thickness,
                                float y, float r, float g, float b, float a) {
        float outer = radius;
        float inner = Math.max(0.02f, radius - thickness);
        // North and south bars span the full width; east and west fill the gap between them.
        out.add(quad(cx - outer, cz - outer, cx + outer, cz - inner, y, r, g, b, a));
        out.add(quad(cx - outer, cz + inner, cx + outer, cz + outer, y, r, g, b, a));
        out.add(quad(cx - outer, cz - inner, cx - inner, cz + inner, y, r, g, b, a));
        out.add(quad(cx + inner, cz - inner, cx + outer, cz + inner, y, r, g, b, a));
    }

    private static Face quad(float x0, float z0, float x1, float z1, float y,
                             float r, float g, float b, float a) {
        return new Face(x0, z0, x0, z1, x1, z1, x1, z0, y, r, g, b, a);
    }

    // Per-version GPU flush - same split as TileOverlayRenderer / CloudSeaRenderer.

    //? if <=1.21.4 {
    private static void render(MatrixStack matrices, Camera camera) {
        if (matrices == null || camera == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        List<Face> faces = new ArrayList<>();
        buildFaces(mc, faces);
        if (faces.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        //? if <=1.21.1 {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        //?} else {
        /*RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
        *///?}

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Face f : faces) {
            buffer.vertex(matrix, f.x0(), f.y(), f.z0()).color(f.r(), f.g(), f.b(), f.a());
            buffer.vertex(matrix, f.x1(), f.y(), f.z1()).color(f.r(), f.g(), f.b(), f.a());
            buffer.vertex(matrix, f.x2(), f.y(), f.z2()).color(f.r(), f.g(), f.b(), f.a());
            buffer.vertex(matrix, f.x3(), f.y(), f.z3()).color(f.r(), f.g(), f.b(), f.a());
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }
    //?} else {
    /*private static void renderV5(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        if (matrices == null || camera == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        List<Face> faces = new ArrayList<>();
        buildFaces(mc, faces);
        if (faces.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer vc = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        for (Face f : faces) {
            vc.vertex(matrix, f.x0(), f.y(), f.z0()).color(f.r(), f.g(), f.b(), f.a());
            vc.vertex(matrix, f.x1(), f.y(), f.z1()).color(f.r(), f.g(), f.b(), f.a());
            vc.vertex(matrix, f.x2(), f.y(), f.z2()).color(f.r(), f.g(), f.b(), f.a());
            vc.vertex(matrix, f.x3(), f.y(), f.z3()).color(f.r(), f.g(), f.b(), f.a());
        }

        matrices.pop();
    }
    *///?}
}
