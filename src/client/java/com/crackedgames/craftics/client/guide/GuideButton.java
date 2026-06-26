package com.crackedgames.craftics.client.guide;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * A {@link ButtonWidget} skinned to the parchment {@link GuideTheme} so every
 * restyled Craftics screen gets the same button look instead of the gray vanilla
 * button. Parchment fill, a gold top accent, an ink border, and ink label text;
 * brighter on hover, dimmer when disabled.
 *
 * <p>Build with {@link #builder(Text, PressAction)} exactly like a vanilla button.
 */
public class GuideButton extends ButtonWidget {

    public GuideButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
    }

    /** Factory mirroring how screens position vanilla buttons. */
    public static GuideButton of(int x, int y, int width, int height, Text message, PressAction onPress) {
        return new GuideButton(x, y, width, height, message, onPress);
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean enabled = this.active;
        boolean hover = enabled && isHovered();

        int fill = !enabled ? GuideTheme.PARCH_SHADE
                 : hover ? GuideTheme.PARCH
                 : GuideTheme.PARCH_EDGE;
        // Body + 1px border, gold top accent.
        ctx.fill(x, y, x + w, y + h, GuideTheme.COVER_EDGE);          // border
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);          // face
        ctx.fill(x + 1, y + 1, x + w - 1, y + 2,
            hover ? GuideTheme.GOLD : GuideTheme.GOLD_DIM);          // top accent

        int textCol = !enabled ? GuideTheme.INK_FAINT : GuideTheme.INK;
        // Centered, no shadow (parchment). Strip any legacy color codes so they
        // don't fight the ink color on the light face.
        String label = stripCodes(getMessage().getString());
        MinecraftClient mc = MinecraftClient.getInstance();
        int tw = mc.textRenderer.getWidth(label);
        ctx.drawText(mc.textRenderer, Text.literal(label),
            x + (w - tw) / 2, y + (h - 8) / 2, textCol, false);
    }

    /** Remove legacy section-sign formatting codes from a label string. */
    private static String stripCodes(String s) {
        if (s.indexOf('§') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }
}
