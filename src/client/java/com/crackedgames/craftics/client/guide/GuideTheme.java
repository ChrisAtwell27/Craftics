package com.crackedgames.craftics.client.guide;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Shared parchment/leather styling for Craftics GUI panels (the Guide Book and
 * the inventory stat/affinity panels). One source of truth for the palette and
 * the framed-panel primitives so the panels can never drift from the book.
 */
public final class GuideTheme {
    private GuideTheme() {}

    // Palette (ARGB)
    public static final int COVER_EDGE  = 0xFF1F1209;
    public static final int COVER       = 0xFF3D2817;
    public static final int COVER_LIGHT = 0xFF5A3D24;
    public static final int SIDEBAR_BG  = 0xFF31200F;
    public static final int GOLD        = 0xFFE8B637;
    public static final int GOLD_DIM    = 0xFFB78A2A;
    public static final int PARCH       = 0xFFEADCB3;
    public static final int PARCH_EDGE  = 0xFFD9C490;
    public static final int PARCH_SHADE = 0xFFC4AA72;
    public static final int INK         = 0xFF3B2B12;
    public static final int INK_SOFT    = 0xFF6E5A36;
    public static final int INK_FAINT   = 0xFF9A8455;
    public static final int RULE        = 0xFFB39A66;

    /** Leather-cover bevel with a layered parchment page inside the given rect. */
    public static void drawPanel(DrawContext ctx, int x, int y, int w, int h) {
        int x1 = x + w, y1 = y + h;
        ctx.fill(x - 4, y - 4, x1 + 4, y1 + 4, COVER_EDGE);
        ctx.fill(x - 2, y - 2, x1 + 2, y1 + 2, COVER);
        ctx.fill(x - 2, y - 2, x1 + 2, y, COVER_LIGHT);
        int s = 3;
        ctx.fill(x - 3, y - 3, x - 3 + s, y - 3 + s, GOLD_DIM);
        ctx.fill(x1 + 3 - s, y - 3, x1 + 3, y - 3 + s, GOLD_DIM);
        ctx.fill(x - 3, y1 + 3 - s, x - 3 + s, y1 + 3, GOLD_DIM);
        ctx.fill(x1 + 3 - s, y1 + 3 - s, x1 + 3, y1 + 3, GOLD_DIM);
        ctx.fill(x, y, x1, y1, PARCH_SHADE);
        ctx.fill(x + 1, y + 1, x1 - 1, y1 - 1, PARCH_EDGE);
        ctx.fill(x + 3, y + 3, x1 - 3, y1 - 3, PARCH);
    }

    /** No-shadow centered text in {@code color}, horizontally centered on {@code cx}.
     *  The shared replacement for per-screen {@code centered()} helpers - on light
     *  parchment, shadowed text reads muddy, so callers must use this, not
     *  drawCenteredTextWithShadow. */
    public static void drawCentered(DrawContext ctx, TextRenderer tr, String text, int cx, int y, int color) {
        Text t = Text.literal(text);
        ctx.drawText(tr, t, cx - tr.getWidth(t) / 2, y, color, false);
    }

    /** No-shadow left-aligned text. Companion to {@link #drawCentered}. */
    public static void drawInk(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color) {
        ctx.drawText(tr, Text.literal(text), x, y, color, false);
    }

    /** Horizontal rule with a centered gold diamond. */
    public static void drawRule(DrawContext ctx, int x, int y, int w) {
        ctx.fill(x, y, x + w, y + 1, RULE);
        int cx = x + w / 2;
        ctx.fill(cx - 1, y - 1, cx + 1, y + 2, GOLD_DIM);
    }

    /** Small colored chip; returns the next x. Drops (returns x unchanged) if it
     *  would overflow maxX. Skips null/empty text. */
    public static int drawBadge(DrawContext ctx, TextRenderer tr, int x, int y,
                                String text, int color, int maxX) {
        if (text == null || text.isEmpty()) return x;
        int w = tr.getWidth(text) + 6;
        if (x + w > maxX) return x;
        ctx.fill(x, y, x + w, y + 12, color);
        ctx.fill(x, y, x + w, y + 1, brighten(color, 45));
        ctx.drawTextWithShadow(tr, Text.literal(text), x + 3, y + 2, 0xFFF5EBD0);
        return x + w + 3;
    }

    /** Brighten an ARGB color by adding to each RGB channel (alpha forced opaque). */
    public static int brighten(int argb, int amount) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
