package com.crackedgames.craftics.client.hints;

import com.crackedgames.craftics.client.guide.GuideTheme;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

public final class HintHudRenderer implements HudRenderCallback {

    // Mirror the GuideTheme leather/gold book palette: translucent dark-leather
    // backing with a dim-gold frame, white text for legibility over the world.
    private static final int PANEL_BG = 0xCC000000 | (GuideTheme.COVER_EDGE & 0x00FFFFFF);
    private static final int PANEL_BORDER = GuideTheme.GOLD_DIM;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int PADDING = 8;
    /** Vertical pixel offset from the top of the screen - sits just below the turn banner
     *  (turn banner ends at y≈18; this leaves a 4px gap). */
    private static final int TOP_OFFSET = 22;

    private String currentId = null;
    private int ticksRemaining = 0;

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        if (net.minecraft.client.MinecraftClient.getInstance().options.hudHidden) return;
        var maybeHint = HintManager.get().getActiveHudHint();
        if (maybeHint.isEmpty()) {
            currentId = null;
            ticksRemaining = 0;
            return;
        }

        Hint h = maybeHint.get();
        if (!(h.presenter() instanceof HintPresenter.HudPopup popup)) return;

        if (!h.id().equals(currentId)) {
            currentId = h.id();
            ticksRemaining = popup.durationTicks();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int screenW = client.getWindow().getScaledWidth();

        Text text = popup.text();
        int textW = tr.getWidth(text);
        int boxW = textW + PADDING * 2;
        int boxH = tr.fontHeight + PADDING * 2;
        int x = (screenW - boxW) / 2;
        // Sit below the WHOLE top-center combat stack (turn banner + party turn-order panel
        // + enemy act-order strip), not just the banner. The fixed offset drew this popup
        // straight over the party panel whenever a fight had party members listed.
        int y = Math.max(TOP_OFFSET,
            com.crackedgames.craftics.client.CombatHudOverlay.getTopCenterReservedPx() + 4);

        ctx.fill(x, y, x + boxW, y + boxH, PANEL_BG);
        ctx.fill(x,             y,             x + boxW, y + 1,        PANEL_BORDER);
        ctx.fill(x,             y + boxH - 1,  x + boxW, y + boxH,     PANEL_BORDER);
        ctx.fill(x,             y,             x + 1,    y + boxH,     PANEL_BORDER);
        ctx.fill(x + boxW - 1,  y,             x + boxW, y + boxH,     PANEL_BORDER);

        ctx.drawTextWithShadow(tr, text, x + PADDING, y + PADDING, TEXT_COLOR);

        if (--ticksRemaining <= 0) {
            HintManager.get().dismiss(h.id());
            currentId = null;
        }
    }
}
