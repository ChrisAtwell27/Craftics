package com.crackedgames.craftics.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side combat log overlay. Shows recent combat messages in the
 * bottom-left corner with auto-fade. Replaces chat messages during combat.
 */
public class CombatLog {

    private static final int MAX_VISIBLE_DEFAULT = 4;
    private static int getMaxMessages() {
        try { return com.crackedgames.craftics.CrafticsMod.CONFIG.combatLogMaxLines(); }
        catch (Exception e) { return MAX_VISIBLE_DEFAULT; }
    }
    private static final int FADE_START_TICKS = 60;  // start fading after 3 seconds
    private static final int FADE_DURATION_TICKS = 40; // fully gone after 2 more seconds
    private static final int LINE_HEIGHT = 11;

    private static final List<LogEntry> entries = new ArrayList<>();

    private record LogEntry(String message, long timestamp) {}

    /**
     * Add a message to the combat log.
     */
    public static void addMessage(String message) {
        entries.add(0, new LogEntry(message, System.currentTimeMillis()));
        while (entries.size() > getMaxMessages()) {
            entries.remove(entries.size() - 1);
        }
    }

    /**
     * Clear all log entries.
     */
    public static void clear() {
        entries.clear();
    }

    /**
     * Render the combat log overlay. Call from HUD render callback.
     */
    public static void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        if (entries.isEmpty() || !CombatState.isInCombat()) return;

        int x = 6;
        int baseY = screenHeight - 56; // above hotbar, clear of vanilla HUD
        long now = System.currentTimeMillis();

        // Draw from bottom up (newest at bottom)
        int drawn = 0;
        for (int i = 0; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);
            long age = now - entry.timestamp();
            int ageTicks = (int)(age / 50); // ~20 ticks per second

            if (ageTicks > FADE_START_TICKS + FADE_DURATION_TICKS) continue;

            // Calculate alpha
            int alpha = 255;
            if (ageTicks > FADE_START_TICKS) {
                float fadeProgress = (float)(ageTicks - FADE_START_TICKS) / FADE_DURATION_TICKS;
                alpha = (int)(255 * (1.0f - fadeProgress));
            }
            alpha = Math.max(4, Math.min(255, alpha));

            int y = baseY - (drawn * LINE_HEIGHT);

            // Left-edge accent line
            int accentAlpha = (alpha * 3) / 4;
            context.fill(x - 3, y - 1, x - 2, y + LINE_HEIGHT - 2, (accentAlpha << 24) | 0x555577);

            // Semi-transparent background behind text
            int bgAlpha = (alpha * 3) / 4;
            int bgColor = (bgAlpha << 24) | 0x000000;
            int textWidth = textRenderer.getWidth(entry.message());
            context.fill(x - 2, y - 1, x + textWidth + 2, y + LINE_HEIGHT - 2, bgColor);

            // Draw text with alpha
            int textColor = 0xFFFFFF | (alpha << 24);
            context.drawTextWithShadow(textRenderer, entry.message(), x, y, textColor);

            drawn++;
        }
    }
}
