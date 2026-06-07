package com.crackedgames.craftics.client.music;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * "Now Playing" toast pinned to the very bottom-left of the screen. Slides in from the left,
 * holds, then slides back out. Shown by {@link MusicManager#request} whenever a new track
 * starts; names the song and its source. Animation is driven by {@link #tick()} from the
 * client tick loop (same pattern as {@link com.crackedgames.craftics.client.AchievementToast}).
 */
public class MusicToast implements HudRenderCallback {

    private static final int SLIDE_TICKS = 8;
    private static final int DISPLAY_TICKS = 100; // ~5 seconds at full visibility
    private static final int TOTAL_TICKS = SLIDE_TICKS + DISPLAY_TICKS + SLIDE_TICKS;

    private static final int PAD_X = 6;
    private static final int PAD_Y = 5;
    private static final int LINE_GAP = 2;
    private static final int MARGIN = 6;       // gap from the screen edges
    private static final int MAX_WIDTH = 240;  // cap before truncation

    private static final int BG_COLOR = 0xDD12121C;
    private static final int ACCENT_COLOR = 0xFF35D6B0; // teal accent bar + note
    private static final int LABEL_COLOR = 0xFF8FE3D0;  // muted teal "Now Playing"
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int SOURCE_COLOR = 0xFFB9B9C9;

    private static String songName = "";
    private static String source = "";
    private static int ticksShown = 0;
    private static boolean active = false;

    /** Begin showing a fresh toast (restarts the animation if one is already up). */
    public static void show(String name, String src) {
        songName = name == null ? "" : name;
        source = src == null ? "" : src;
        ticksShown = 0;
        active = true;
    }

    public static void clear() {
        active = false;
        ticksShown = 0;
    }

    public static void tick() {
        if (!active) return;
        ticksShown++;
        if (ticksShown >= TOTAL_TICKS) {
            active = false;
            ticksShown = 0;
        }
    }

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!active) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.player == null) return;

        TextRenderer tr = client.textRenderer;
        String label = "♪ Now Playing";
        String fromLine = source.isEmpty() ? "" : "from " + source;

        // Box width fits the widest line (capped), then truncate lines to fit.
        int contentW = Math.max(tr.getWidth(label),
            Math.max(tr.getWidth(songName), tr.getWidth(fromLine)));
        int boxW = Math.min(MAX_WIDTH, contentW + PAD_X * 2 + 2);
        int innerMax = boxW - PAD_X * 2 - 2;
        String name = trim(tr, songName, innerMax);
        String src = trim(tr, fromLine, innerMax);

        int lineH = tr.fontHeight;
        int boxH = PAD_Y * 2 + lineH * 3 + LINE_GAP * 2;

        int screenH = ctx.getScaledWindowHeight();

        // Slide horizontally: off-screen-left → MARGIN → off-screen-left.
        int restX = MARGIN;
        int x;
        if (ticksShown < SLIDE_TICKS) {
            float p = (float) ticksShown / SLIDE_TICKS;
            x = (int) (-boxW + (boxW + restX) * p);
        } else if (ticksShown >= SLIDE_TICKS + DISPLAY_TICKS) {
            float p = (float) (ticksShown - SLIDE_TICKS - DISPLAY_TICKS) / SLIDE_TICKS;
            x = (int) (restX - (boxW + restX) * p);
        } else {
            x = restX;
        }
        int y = screenH - boxH - MARGIN;

        // Background + left accent bar.
        ctx.fill(x, y, x + boxW, y + boxH, BG_COLOR);
        ctx.fill(x, y, x + 2, y + boxH, ACCENT_COLOR);

        int tx = x + PAD_X;
        int ty = y + PAD_Y;
        ctx.drawText(tr, label, tx, ty, LABEL_COLOR, true);
        ty += lineH + LINE_GAP;
        ctx.drawText(tr, name, tx, ty, NAME_COLOR, true);
        ty += lineH + LINE_GAP;
        if (!src.isEmpty()) {
            ctx.drawText(tr, src, tx, ty, SOURCE_COLOR, false);
        }
    }

    private static String trim(TextRenderer tr, String s, int maxW) {
        if (s == null || s.isEmpty() || tr.getWidth(s) <= maxW) return s == null ? "" : s;
        String ell = "...";
        int ellW = tr.getWidth(ell);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            int cw = tr.getWidth(String.valueOf(s.charAt(i)));
            if (w + cw + ellW > maxW) break;
            sb.append(s.charAt(i));
            w += cw;
        }
        return sb.append(ell).toString();
    }
}
