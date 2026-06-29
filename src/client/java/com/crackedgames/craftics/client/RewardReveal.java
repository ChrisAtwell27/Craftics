package com.crackedgames.craftics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;

/**
 * Shared reveal-animation primitives for Craftics' "outcome reveal" screens.
 *
 * <p>First built for the {@link GameOverScreen} coin-flip item-loss sequence, the
 * staggered, sound-backed reveal feel is reused by the victory / barter / vault
 * reward reveals. This class holds only the reusable pieces - easing curves, a
 * filled-ellipse helper, the landing "pop" ring, and a master-channel UI sound -
 * so each screen composes them into its own layout and pacing rather than copying
 * the math. (One source of truth for the reveal motion, the way {@code GuideTheme}
 * is for the parchment palette.)
 */
public final class RewardReveal {
    private RewardReveal() {}

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
}
