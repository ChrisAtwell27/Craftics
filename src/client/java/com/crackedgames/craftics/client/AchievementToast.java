package com.crackedgames.craftics.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Renders achievement unlock toast notifications at the top of the screen.
 * Queues multiple unlocks and shows them one at a time with slide-in/out animation.
 */
public class AchievementToast implements HudRenderCallback {

    private static final int TOAST_WIDTH = 220;
    private static final int TOAST_HEIGHT = 36;
    private static final int SLIDE_TICKS = 10;
    private static final int DISPLAY_TICKS = 80; // how long to show (4 seconds)
    private static final int TOTAL_TICKS = SLIDE_TICKS + DISPLAY_TICKS + SLIDE_TICKS;

    private static final int BG_COLOR = 0xDD1a1a2e;
    private static final int BORDER_COLOR = 0xFF6633aa;
    private static final int TITLE_COLOR = 0xFFFFD700; // gold
    private static final int DESC_COLOR = 0xFFCCCCCC;

    private static final Queue<ToastEntry> queue = new ArrayDeque<>();
    private static ToastEntry current = null;
    private static int ticksShown = 0;

    public record ToastEntry(String displayName, String description, String categoryColor) {}

    public static void enqueue(String displayName, String description, String categoryColor) {
        queue.add(new ToastEntry(displayName, description, categoryColor));
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

        // Star icon
        ctx.drawText(client.textRenderer, "\u2726", x + 6, y + 5, TITLE_COLOR, true);

        // Title: "Achievement Unlocked!"
        ctx.drawText(client.textRenderer, "Achievement Unlocked!", x + 18, y + 5, TITLE_COLOR, true);

        // Achievement name + description
        String name = current.displayName();
        String desc = current.description();
        // Truncate if too long
        if (client.textRenderer.getWidth(name + " — " + desc) > TOAST_WIDTH - 16) {
            desc = desc.substring(0, Math.min(desc.length(), 28)) + "...";
        }
        ctx.drawText(client.textRenderer, name, x + 6, y + 20, 0xFFFFFFFF, true);
        int nameWidth = client.textRenderer.getWidth(name);
        ctx.drawText(client.textRenderer, " — " + desc, x + 6 + nameWidth, y + 20, DESC_COLOR, false);
    }
}
