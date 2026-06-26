package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.combat.PlayerProgression;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Right-side inventory panel: level, unspent points, the eight progression
 *  stats, and emeralds, drawn in the shared parchment GuideTheme style. */
public final class StatsPanel {
    private StatsPanel() {}

    private static final int WIDTH = 124;       // content width (pre-scale)
    private static final int LINE = 12;
    private static final int BTN = 11; // minimize button size
    private static final int STRIP_W = 26;
    /** Minecraft inventory background height; the collapsed strip matches it. */
    private static final int INV_H = 166;

    /** 1.0 if the panel fits between the inventory's right edge and the screen
     *  edge; otherwise a shrink factor so it fits. */
    public static float fitScale(int screenWidth, int screenHeight) {
        int[] L = computeLayout(screenWidth, screenHeight);
        int rightEdge = L[0] + L[2] + 6;             // panel right + margin
        if (rightEdge <= screenWidth) return 1.0f;
        int avail = screenWidth - L[0] - 6;
        return Math.max(0.5f, avail / (float) L[2]); // never below 0.5
    }

    /** Minimize-button rect {x, y, w, h} for the Stats panel. */
    public static int[] buttonRect(int screenWidth, int screenHeight) {
        float s = fitScale(screenWidth, screenHeight);
        int ox, oy; int[] base;
        if (CombatState.isStatsPanelCollapsed()) {
            int[] c = collapsedLayout(screenWidth, screenHeight);
            ox = c[0]; oy = c[1];
            base = new int[]{c[0] + c[2] - BTN, c[1], BTN, BTN};
        } else {
            int[] L = computeLayout(screenWidth, screenHeight);
            ox = L[0]; oy = L[1];
            base = new int[]{L[0] + L[2] - BTN, L[1] + 2, BTN, BTN};
        }
        if (s == 1.0f) return base;
        int x = ox + Math.round((base[0] - ox) * s);
        int y = oy + Math.round((base[1] - oy) * s);
        return new int[]{x, y, Math.round(BTN * s), Math.round(BTN * s)};
    }

    /** Collapsed icon-strip rect {x, y, w, h}. Matches the inventory's height so
     *  the thin sidebar reads as the same size as the inventory, anchored to the
     *  same right edge and vertically centered. */
    private static int[] collapsedLayout(int screenWidth, int screenHeight) {
        int x = (screenWidth / 2) + 90;
        int y = (screenHeight / 2) - INV_H / 2;
        return new int[]{x, y, STRIP_W, INV_H};
    }

    private static void drawButton(DrawContext ctx, TextRenderer tr, int bx, int by, boolean collapsed) {
        ctx.fill(bx, by, bx + BTN, by + BTN, GuideTheme.PARCH_EDGE);
        ctx.fill(bx, by, bx + BTN, by + 1, GuideTheme.GOLD_DIM);
        String sym = collapsed ? "+" : "–"; // plus / en-dash
        int sw = tr.getWidth(sym);
        ctx.drawText(tr, Text.literal(sym), bx + (BTN - sw) / 2, by + 2, GuideTheme.INK, false);
    }

    private static void renderCollapsed(DrawContext ctx, int screenWidth, int screenHeight,
                                        int mouseX, int mouseY, TextRenderer tr) {
        int[] L = collapsedLayout(screenWidth, screenHeight);
        int x = L[0], y0 = L[1], w = L[2], h = L[3];
        float scale = fitScale(screenWidth, screenHeight);
        boolean scaled = scale != 1.0f;
        if (scaled) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate((float) x, (float) y0, 0);
            ctx.getMatrices().scale(scale, scale, 1.0f);
            ctx.getMatrices().translate((float) -x, (float) -y0, 0);
        }
        GuideTheme.drawPanel(ctx, x, y0, w, h);

        int[] br = buttonRect(screenWidth, screenHeight);
        drawButton(ctx, tr, br[0], br[1], true);

        // Space the icon rows evenly through the strip below the button, each
        // glyph horizontally centered in the strip.
        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int top = y0 + BTN + 4;
        int bottom = y0 + h - 4;
        int n = stats.length;
        int slot = (bottom - top) / n;
        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int gx = x + (w - tr.getWidth(stat.icon)) / 2;
            int rowY = top + i * slot + (slot - 8) / 2;
            ctx.drawText(tr, Text.literal(stat.icon), gx, rowY, 0xFFFFFFFF, false);
            if (mouseX >= x && mouseX < x + w && mouseY >= rowY - 3 && mouseY < rowY + 11) {
                int points = CombatState.getStatPoints(i);
                int effective = stat.baseValue + points;
                String tip = stat.displayName + ": " + effective + (points > 0 ? " (+" + points + ")" : "");
                ctx.drawTooltip(tr, Text.literal(tip), mouseX, mouseY);
            }
        }
        if (scaled) ctx.getMatrices().pop();
    }

    /** Content rect {x, y, w, h} anchored to the right of the inventory GUI and
     *  vertically centered on it (the inventory background is 166px tall, centered
     *  on screenHeight/2). Height is computed to fit the content exactly. */
    public static int[] computeLayout(int screenWidth, int screenHeight) {
        int rows = PlayerProgression.Stat.values().length;
        int h = 5 + LINE + LINE + LINE + 6 + rows * LINE + 6 + LINE + 5;
        int x = (screenWidth / 2) + 90;
        int y = (screenHeight / 2) - h / 2;
        return new int[]{x, y, WIDTH, h};
    }

    public static void render(DrawContext ctx, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        TextRenderer tr = mc.textRenderer;

        if (CombatState.isStatsPanelCollapsed()) {
            renderCollapsed(ctx, screenWidth, screenHeight, mouseX, mouseY, tr);
            return;
        }

        int[] L = computeLayout(screenWidth, screenHeight);
        int x = L[0], y0 = L[1], w = L[2], h = L[3];
        float scale = fitScale(screenWidth, screenHeight);
        boolean scaled = scale != 1.0f;
        if (scaled) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate((float) x, (float) y0, 0);
            ctx.getMatrices().scale(scale, scale, 1.0f);
            ctx.getMatrices().translate((float) -x, (float) -y0, 0);
        }
        GuideTheme.drawPanel(ctx, x, y0, w, h);

        int px = x + 5;
        int y = y0 + 5;
        int maxX = x + w - 5;

        ctx.drawText(tr, Text.literal("§l★ Stats"), px, y, GuideTheme.GOLD, false);
        y += LINE;

        ctx.drawText(tr, Text.literal("Level " + CombatState.getPlayerLevel()), px, y, GuideTheme.INK, false);
        y += LINE;

        int unspent = CombatState.getUnspentPoints();
        if (unspent > 0) {
            ctx.drawText(tr,
                Text.literal(unspent + " point" + (unspent != 1 ? "s" : "") + " available!"),
                px, y, 0xFF2E7D32, false);
        }
        y += LINE;

        GuideTheme.drawRule(ctx, px, y, w - 10);
        y += 6;

        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int points = CombatState.getStatPoints(i);
            int effective = stat.baseValue + points;
            String line = stat.icon + " §r§o" + stat.displayName;
            ctx.drawText(tr, Text.literal(line), px, y, GuideTheme.INK, false);
            String val = String.valueOf(effective) + (points > 0 ? " §8(+" + points + ")" : "");
            int vw = tr.getWidth(val);
            ctx.drawText(tr, Text.literal("§r" + val), maxX - vw, y, GuideTheme.INK_SOFT, false);
            y += LINE;
        }

        y += 2;
        GuideTheme.drawRule(ctx, px, y, w - 10);
        y += 6;

        ctx.drawText(tr,
            Text.literal("⬢ " + CombatState.getEmeralds() + " Emeralds"), px, y, 0xFF2E7D32, false);

        int[] br = buttonRect(screenWidth, screenHeight);
        drawButton(ctx, tr, br[0], br[1], false);
        if (scaled) ctx.getMatrices().pop();
    }
}
