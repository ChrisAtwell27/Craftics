package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Shared reveal-animation primitives for Craftics' "outcome reveal" screens.
 *
 * <p>First built for the {@link GameOverScreen} coin-flip item-loss sequence, the
 * staggered, sound-backed reveal feel is reused by the victory / barter / vault
 * reward reveals. This class holds only the reusable pieces - easing curves, a
 * filled-ellipse helper, the landing "pop" ring, a polished tumbling coin, panel
 * entrance easing, a lightweight screen-space particle layer (gold sparks for
 * triumphs, drifting ash for defeats), scaled title text, and a master-channel
 * UI sound - so each screen composes them into its own layout and pacing rather
 * than copying the math. (One source of truth for the reveal motion, the way
 * {@code GuideTheme} is for the parchment palette.)
 */
public final class RewardReveal {
    private RewardReveal() {}

    private static final Random RNG = new Random();

    /** Play a UI sound on the master channel (screens have no world position). */
    public static void playMaster(SoundEvent sound, float volume, float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(
            PositionedSoundInstance.master(sound, pitch, volume));
    }

    /** Ease-out-back: accelerates, overshoots just past 1.0 near the end, then
     *  settles back to 1.0 - the "snap into place" curve. */
    public static float easeOutBack(float x) {
        final float c1 = 1.70158f;
        final float c3 = c1 + 1f;
        float xm = x - 1f;
        return 1f + c3 * xm * xm * xm + c1 * xm * xm;
    }

    /** Smoothstep eased 0..1 (clamped). */
    public static float smoothstep(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return x * x * (3 - 2 * x);
    }

    /** Entrance progress 0..1 for a panel/title that started at {@code startMs}:
     *  smoothstepped over {@code durMs}, clamped. 1.0 once settled. */
    public static float entrance(long sinceMs, long durMs) {
        if (durMs <= 0) return 1f;
        return smoothstep(sinceMs / (float) durMs);
    }

    /** Slow sine pulse 0..1 for "click to continue" style affordances. */
    public static float pulse(long periodMs) {
        double phase = (System.currentTimeMillis() % periodMs) / (double) periodMs;
        return 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2);
    }

    /** Begin a centered scale transform around (cx, cy); pair with {@code pop()}. */
    public static void pushScaledAround(DrawContext ctx, float scale, float cx, float cy) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.getMatrices().translate(-cx, -cy, 0);
    }

    /** Centered text at an arbitrary scale (titles). Shadowless ink style. */
    public static void drawCenteredScaled(DrawContext ctx, TextRenderer tr, String text,
                                          int cx, int cy, float scale, int color, boolean shadow) {
        Text t = Text.literal(text);
        pushScaledAround(ctx, scale, cx, cy);
        int w = tr.getWidth(t);
        ctx.drawText(tr, t, cx - w / 2, cy - 4, color, shadow);
        ctx.getMatrices().pop();
    }

    /** Filled ellipse (radii rx,ry) centered at (cx,cy), via horizontal scanlines. */
    public static void drawDisc(DrawContext ctx, int cx, int cy, int rx, int ry, int color) {
        if (rx <= 0 || ry <= 0) return;
        for (int dyp = -ry; dyp <= ry; dyp++) {
            // half-width of the ellipse at this row
            double frac = 1.0 - (double) (dyp * dyp) / (double) (ry * ry);
            if (frac < 0) continue;
            int half = (int) Math.round(rx * Math.sqrt(frac));
            int yy = cy + dyp;
            ctx.fill(cx - half, yy, cx + half + 1, yy + 1, color);
        }
    }

    /**
     * A polished tumbling coin: soft drop shadow beneath, dark rim, bright
     * top-edge rim light, subtly shaded face, and an optional face glyph
     * (hidden while edge-on). Consolidates the coin drawing that GameOver and
     * the gamble reveal previously each hand-rolled.
     *
     * @param edgeSquash 1.0 = fully face-on, ~0.2 = thin edge slice mid-flip
     */
    public static void drawCoin(DrawContext ctx, TextRenderer tr, int cx, int cy, int radius,
                                float edgeSquash, int faceColor, String glyph, int glyphColor) {
        int r = Math.max(2, radius);
        int rx = Math.max(2, Math.round(r * Math.max(0.12f, Math.min(1f, edgeSquash))));
        boolean edge = edgeSquash < 0.45f;

        // Soft shadow puddle under the coin grounds it on the panel.
        drawDisc(ctx, cx + 1, cy + r + 2, Math.max(2, rx - 1), Math.max(1, r / 4), 0x44000000);
        // Dark rim, then body, then a bright crescent along the top edge.
        drawDisc(ctx, cx, cy, rx + 1, r + 1, 0xFF241505);
        drawDisc(ctx, cx, cy, rx, r, faceColor);
        drawDisc(ctx, cx, cy - 2, Math.max(1, rx - 2), Math.max(1, r - 2),
            GuideTheme.brighten(faceColor, 46));
        drawDisc(ctx, cx, cy - 1, Math.max(1, rx - 3), Math.max(1, r - 3), faceColor);
        if (!edge && glyph != null && !glyph.isEmpty()) {
            Text f = Text.literal(glyph);
            ctx.drawTextWithShadow(tr, f, cx - tr.getWidth(f) / 2, cy - 4, glyphColor);
        }
    }

    /** Expanding + fading rectangular ring around a cell {@code [x0,y0,x1,y1)}, used
     *  as a "landing pop" the instant an item resolves. {@code progress} runs 0..1;
     *  the ring grows outward and fades as it goes. No-op outside that range. */
    public static void drawPopRing(DrawContext ctx, int x0, int y0, int x1, int y1,
                                   float progress, int rgb) {
        if (progress < 0f || progress >= 1f) return;
        int grow = Math.round(6 * progress);          // ring expands outward
        int alpha = Math.round(0xCC * (1f - progress)); // fades out
        int ring = (alpha << 24) | (rgb & 0xFFFFFF);
        int rx0 = x0 - grow, ry0 = y0 - grow, rx1 = x1 + grow, ry1 = y1 + grow;
        ctx.fill(rx0, ry0, rx1, ry0 + 1, ring);
        ctx.fill(rx0, ry1 - 1, rx1, ry1, ring);
        ctx.fill(rx0, ry0, rx0 + 1, ry1, ring);
        ctx.fill(rx1 - 1, ry0, rx1, ry1, ring);
    }

    // ── Screen-space particles ────────────────────────────────────────────────
    // A tiny wall-clock particle layer for reveal screens: gold sparks bursting
    // from a landed reward, grey ash drifting down a defeat screen. Purely
    // decorative 2px quads - cleared whenever the owning screen closes.

    private static final class Particle {
        float x, y, vx, vy, gravity;
        int rgb, size;
        long bornMs, lifeMs;
    }

    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static long lastTickMs = -1;

    /** Radial burst of sparks from (cx, cy) - triumphant moments. */
    public static void burst(float cx, float cy, int count, int rgb, float speed) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double ang = RNG.nextDouble() * Math.PI * 2;
            double v = speed * (0.35 + RNG.nextDouble() * 0.65);
            p.x = cx; p.y = cy;
            p.vx = (float) (Math.cos(ang) * v);
            p.vy = (float) (Math.sin(ang) * v) - speed * 0.35f; // slight upward bias
            p.gravity = speed * 2.2f;
            p.rgb = rgb;
            p.size = 1 + RNG.nextInt(2);
            p.bornMs = now;
            p.lifeMs = 450 + RNG.nextInt(450);
            PARTICLES.add(p);
        }
    }

    /** One slow ash/ember mote spawned near the top of the screen - defeat mood.
     *  Call once per frame with a small probability for a steady drift. */
    public static void ash(float x, float y, int rgb) {
        long now = System.currentTimeMillis();
        Particle p = new Particle();
        p.x = x; p.y = y;
        p.vx = (RNG.nextFloat() - 0.5f) * 14f;
        p.vy = 18f + RNG.nextFloat() * 22f;
        p.gravity = 0f;
        p.rgb = rgb;
        p.size = 1 + (RNG.nextInt(4) == 0 ? 1 : 0);
        p.bornMs = now;
        p.lifeMs = 2600 + RNG.nextInt(2200);
        PARTICLES.add(p);
    }

    /** Advance and draw all live particles. Call once per frame from the owning
     *  screen's render. Frame-rate independent (wall clock). */
    public static void tickAndDrawParticles(DrawContext ctx) {
        long now = System.currentTimeMillis();
        float dt = lastTickMs < 0 ? 0.016f : Math.min(0.1f, (now - lastTickMs) / 1000f);
        lastTickMs = now;
        Iterator<Particle> it = PARTICLES.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            long age = now - p.bornMs;
            if (age >= p.lifeMs) { it.remove(); continue; }
            p.vy += p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float lifeFrac = 1f - age / (float) p.lifeMs;
            int alpha = Math.round(0xE0 * Math.min(1f, lifeFrac * 2f)); // hold, then fade tail
            int color = (alpha << 24) | (p.rgb & 0xFFFFFF);
            int ix = Math.round(p.x), iy = Math.round(p.y);
            ctx.fill(ix, iy, ix + p.size, iy + p.size, color);
        }
    }

    /** Drop all live particles (screen closed / superseded). */
    public static void clearParticles() {
        PARTICLES.clear();
        lastTickMs = -1;
    }
}
