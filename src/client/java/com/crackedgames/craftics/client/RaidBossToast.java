package com.crackedgames.craftics.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Renders daily raid boss toast notifications at the top of the screen. Queues
 * multiple broadcasts and shows them one at a time with slide-in/out animation.
 * Same queueing/animation pattern as {@link AchievementToast}; styled red instead
 * of gold/purple so it reads as a raid alert rather than an achievement.
 */
public class RaidBossToast implements HudRenderCallback {

    private static final int TOAST_WIDTH = 240;
    private static final int TOAST_HEIGHT = 36;
    private static final int SLIDE_TICKS = 10;
    private static final int DISPLAY_TICKS = 80; // how long to show (4 seconds)
    private static final int TOTAL_TICKS = SLIDE_TICKS + DISPLAY_TICKS + SLIDE_TICKS;

    private static final int BG_COLOR = 0xDD2e1a1a;
    private static final int BORDER_COLOR = 0xFFaa3333;
    private static final int TITLE_COLOR = 0xFFFF5555; // red
    private static final int SUBTITLE_COLOR = 0xFFCCCCCC;

    private static final Queue<ToastEntry> queue = new ArrayDeque<>();
    private static ToastEntry current = null;
    private static int ticksShown = 0;

    public record ToastEntry(String title, String subtitle) {}

    public static void enqueue(String title, String subtitle) {
        queue.add(new ToastEntry(title, subtitle));
    }

    public static void tick() {
        if (current != null) {
            ticksShown++;
            if (ticksShown >= TOTAL_TICKS) {
                current = null;
                ticksShown = 0;
            }
        }
        if (current == null && !queue.isEmpty()) {
            current = queue.poll();
            ticksShown = 0;
        }
    }

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        if (MinecraftClient.getInstance().options.hudHidden) return;
        if (current == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int screenWidth = ctx.getScaledWindowWidth();

        // Calculate slide offset
        int slideOffset;
        if (ticksShown < SLIDE_TICKS) {
            // Sliding in from top
            float progress = (float) ticksShown / SLIDE_TICKS;
            slideOffset = (int) (-TOAST_HEIGHT * (1.0f - progress));
        } else if (ticksShown >= SLIDE_TICKS + DISPLAY_TICKS) {
            // Sliding out
            float progress = (float) (ticksShown - SLIDE_TICKS - DISPLAY_TICKS) / SLIDE_TICKS;
            slideOffset = (int) (-TOAST_HEIGHT * progress);
        } else {
            slideOffset = 0;
        }

        int x = (screenWidth - TOAST_WIDTH) / 2;
        int y = 4 + slideOffset;

        // Background
        ctx.fill(x, y, x + TOAST_WIDTH, y + TOAST_HEIGHT, BG_COLOR);
        // Border
        ctx.fill(x, y, x + TOAST_WIDTH, y + 1, BORDER_COLOR);
        ctx.fill(x, y + TOAST_HEIGHT - 1, x + TOAST_WIDTH, y + TOAST_HEIGHT, BORDER_COLOR);
        ctx.fill(x, y, x + 1, y + TOAST_HEIGHT, BORDER_COLOR);
        ctx.fill(x + TOAST_WIDTH - 1, y, x + TOAST_WIDTH, y + TOAST_HEIGHT, BORDER_COLOR);

        // Skull icon
        ctx.drawText(client.textRenderer, "☠", x + 6, y + 5, TITLE_COLOR, true);

        String title = current.title();
        if (client.textRenderer.getWidth(title) > TOAST_WIDTH - 24) {
            title = trim(client, title, TOAST_WIDTH - 24);
        }
        ctx.drawText(client.textRenderer, title, x + 18, y + 5, TITLE_COLOR, true);

        String subtitle = current.subtitle();
        if (subtitle != null && !subtitle.isEmpty()) {
            if (client.textRenderer.getWidth(subtitle) > TOAST_WIDTH - 12) {
                subtitle = trim(client, subtitle, TOAST_WIDTH - 12);
            }
            ctx.drawText(client.textRenderer, subtitle, x + 6, y + 20, SUBTITLE_COLOR, false);
        }
    }

    private static String trim(MinecraftClient client, String s, int maxWidth) {
        String ell = "...";
        int ellWidth = client.textRenderer.getWidth(ell);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            int cw = client.textRenderer.getWidth(String.valueOf(s.charAt(i)));
            if (w + cw + ellWidth > maxWidth) break;
            sb.append(s.charAt(i));
            w += cw;
        }
        return sb.append(ell).toString();
    }
}
