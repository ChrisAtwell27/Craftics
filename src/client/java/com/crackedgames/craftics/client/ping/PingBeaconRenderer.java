package com.crackedgames.craftics.client.ping;

import com.crackedgames.craftics.client.CombatState;
import com.crackedgames.craftics.client.TileOverlayRenderer;
import com.crackedgames.craftics.core.PingType;
//? if <=1.21.4 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws each standing ping as a beacon pillar rising off its tile.
 *
 * <p>A flat marker on the ground is the obvious way to mark a tile and the wrong one here. The
 * tactical camera looks down at a shallow angle, so a ground highlight is hidden by the first
 * obstacle, mob or ally between it and the viewer - exactly the situations worth pinging about.
 * A pillar leaves the ground plane immediately and is readable from anywhere in the arena, which
 * is the whole job.
 *
 * <p>Structure follows vanilla's beacon: a bright narrow core inside a wide translucent sheath.
 * The pair is what makes a beam read as a beam rather than as a flat coloured rectangle - the
 * sheath gives it volume from the side and the core keeps it visible against a bright sky.
 *
 * <p>Kept apart from {@link TileOverlayRenderer} because that renderer's geometry is horizontal
 * by construction: its quad type carries a single Y, which is a reasonable simplification for
 * ground highlights and cannot express a vertical face at all.
 *
 * <p><b>Geometry is collected into a list before any buffer is opened.</b> That is not a style
 * choice. {@code BufferBuilder.end()} throws if nothing was written to it, and the frame a ping
 * expires is exactly such a frame: the liveness check at the top of the render callback passes,
 * then {@link PingState#active()} prunes the ping before a single vertex is emitted. Deciding
 * whether there is anything to draw <em>before</em> beginning the buffer removes the window
 * entirely, rather than narrowing it.
 */
public final class PingBeaconRenderer {
    private PingBeaconRenderer() {}

    /** How far the pillar rises. Tall enough to clear any arena obstacle, short enough to stay
     *  on screen at the tactical camera's framing rather than running off the top. */
    private static final float HEIGHT = 14.0f;

    /** Half-width of the bright inner core. */
    private static final float CORE_HALF = 0.10f;

    /** Half-width of the translucent outer sheath. */
    private static final float SHEATH_HALF = 0.26f;

    /** Fraction of the lifetime spent shooting up. Short: the snap of motion is what catches
     *  a teammate's eye, and a leisurely rise would spend the ping's whole two seconds arriving. */
    private static final float RISE_FRACTION = 0.12f;

    /** Fraction of the lifetime spent fading out at the end. */
    private static final float FADE_FRACTION = 0.28f;

    /** One quad in world space, four explicit corners. Vertical faces need all three axes to
     *  vary, so this cannot reuse the tile renderer's single-Y quad. */
    private record Face(float ax, float ay, float az, float bx, float by, float bz,
                        float cx, float cy, float cz, float dx, float dy, float dz,
                        float r, float g, float b, float a) {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!CombatState.isInCombat() && !CombatState.isInScene()) return;
            if (MinecraftClient.getInstance().options.hudHidden) return;
            //? if <=1.21.4 {
            render(context.matrixStack(), context.camera());
            //?} else
            /*renderV5(context);*/
        });
    }

    // ── Geometry (shared across versions) ─────────────────────────────────

    /**
     * Build every live ping's pillar and ground ring.
     *
     * <p>Returns an empty list when there is nothing standing, which the callers treat as "draw
     * nothing" rather than "open an empty buffer".
     */
    private static List<Face> collect(MinecraftClient mc) {
        List<Face> out = new ArrayList<>();
        net.minecraft.client.world.ClientWorld world = mc.world;
        if (world == null) return out;

        List<PingState.Ping> pings = PingState.active();
        if (pings.isEmpty()) return out;

        int ox = CombatState.getArenaOriginX();
        int oy = CombatState.getArenaOriginY();
        int oz = CombatState.getArenaOriginZ();
        long now = System.currentTimeMillis();

        for (PingState.Ping ping : pings) {
            float t = ping.progress(now);
            PingType type = ping.type();

            // Rise with an ease-out so it decelerates into place instead of stopping dead.
            float rise = t >= RISE_FRACTION ? 1f : (t / RISE_FRACTION);
            rise = 1f - (1f - rise) * (1f - rise);

            // Hold full strength, then fade over the tail.
            float fade = t <= 1f - FADE_FRACTION ? 1f : (1f - t) / FADE_FRACTION;
            if (fade <= 0f) continue;

            // A slow breathe on top of the fade, so a ping that is standing still is not a
            // static object the eye stops reporting.
            float pulse = 0.85f + 0.15f * (float) Math.sin((now % 100_000L) / 1000.0 * 5.0);

            float baseY = TileOverlayRenderer.tileRenderY(world, ox, oy, oz,
                ping.pos().x(), ping.pos().z());
            float topY = baseY + HEIGHT * rise;
            float cx = ox + ping.pos().x() + 0.5f;
            float cz = oz + ping.pos().z() + 0.5f;

            float r = type.red(), g = type.green(), b = type.blue();

            // Outer sheath: the type's own colour, translucent.
            column(out, cx, cz, SHEATH_HALF, baseY, topY, r, g, b, 0.30f * fade * pulse);

            // Inner core: the same hue pushed most of the way to white, so the beam stays
            // legible against a bright sky without losing which ping it is.
            column(out, cx, cz, CORE_HALF, baseY, topY,
                Math.min(1f, r * 0.4f + 0.6f), Math.min(1f, g * 0.4f + 0.6f),
                Math.min(1f, b * 0.4f + 0.6f), 0.85f * fade);

            // Ground ring: marks the tile itself, which the pillar alone does not - from
            // directly above, a beam is a dot.
            ring(out, cx, cz, baseY + 0.02f, r, g, b, 0.55f * fade * pulse);
        }
        return out;
    }

    /** Four outward faces of a square column. Cull is off, so winding does not matter. */
    private static void column(List<Face> out, float cx, float cz, float half,
                               float y0, float y1, float r, float g, float b, float a) {
        float x0 = cx - half, x1 = cx + half;
        float z0 = cz - half, z1 = cz + half;
        out.add(new Face(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a)); // -Z
        out.add(new Face(x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a)); // +Z
        out.add(new Face(x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, a)); // -X
        out.add(new Face(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a)); // +X
    }

    /** A flat square ring on the ground: four thin strips, drawn as an outline rather than a
     *  filled square so it frames the tile instead of hiding what is standing on it. */
    private static void ring(List<Face> out, float cx, float cz, float y,
                             float r, float g, float b, float a) {
        float outer = 0.46f, inner = 0.34f;
        float xo0 = cx - outer, xo1 = cx + outer, zo0 = cz - outer, zo1 = cz + outer;
        float xi0 = cx - inner, xi1 = cx + inner, zi0 = cz - inner, zi1 = cz + inner;
        flat(out, xo0, zo0, xo1, zi0, y, r, g, b, a); // north strip
        flat(out, xo0, zi1, xo1, zo1, y, r, g, b, a); // south strip
        flat(out, xo0, zi0, xi0, zi1, y, r, g, b, a); // west strip
        flat(out, xi1, zi0, xo1, zi1, y, r, g, b, a); // east strip
    }

    /** One horizontal quad spanning (x0,z0)-(x1,z1) at height y. */
    private static void flat(List<Face> out, float x0, float z0, float x1, float z1,
                             float y, float r, float g, float b, float a) {
        out.add(new Face(x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, r, g, b, a));
    }

    /** Push one face's four vertices into a consumer. */
    private static void emit(VertexConsumer vc, Matrix4f m, Face f) {
        vc.vertex(m, f.ax(), f.ay(), f.az()).color(f.r(), f.g(), f.b(), f.a());
        vc.vertex(m, f.bx(), f.by(), f.bz()).color(f.r(), f.g(), f.b(), f.a());
        vc.vertex(m, f.cx(), f.cy(), f.cz()).color(f.r(), f.g(), f.b(), f.a());
        vc.vertex(m, f.dx(), f.dy(), f.dz()).color(f.r(), f.g(), f.b(), f.a());
    }

    // ── Version-specific flush ────────────────────────────────────────────

    //? if <=1.21.4 {
    private static void render(MatrixStack matrices, Camera camera) {
        if (matrices == null || camera == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        // Collected before the buffer is opened: an empty buffer is a crash, not a no-op.
        List<Face> faces = collect(mc);
        if (faces.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // The beam is a light effect, not a solid: writing depth would let the sheath
        // occlude the core drawn after it, punching a hole down the middle of the pillar.
        RenderSystem.depthMask(false);
        //? if <=1.21.1 {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        //?} else {
        /*RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
        *///?}

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Face f : faces) emit(buffer, matrix, f);
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
        if (mc.world == null) return;

        List<Face> faces = collect(mc);
        if (faces.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer vc = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        for (Face f : faces) emit(vc, matrix, f);

        matrices.pop();
    }
    *///?}
}
