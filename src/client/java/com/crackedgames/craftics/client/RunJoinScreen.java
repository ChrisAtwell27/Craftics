package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.network.RunInviteResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Yes/No popup shown when a party member starts a biome run and invites this player.
 * Accepting joins the run (the player is teleported in when it begins); declining - or
 * letting the countdown run out, or pressing ESC - keeps them on the island. A reply is
 * sent exactly once; the server also times the invite out as a backstop.
 */
public class RunJoinScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 76;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 8; // panel-to-button vertical gap

    private final String biomeName;
    private final String starterName;
    private final int timeoutSeconds;
    private long startMs = -1;
    private boolean replied = false;

    public RunJoinScreen(String biomeName, String starterName, int timeoutSeconds) {
        super(Text.literal("Join Run?"));
        this.biomeName = biomeName == null ? "" : biomeName;
        this.starterName = starterName == null ? "" : starterName;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    /** Top of the parchment panel; the whole panel+buttons block is vertically centered. */
    private int panelTop() {
        int blockH = PANEL_H + BTN_GAP + BTN_H;
        return (this.height - blockH) / 2;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        // Buttons sit BELOW the panel - drawPanel() paints an opaque fill, so anything
        // inside the panel rect would be covered (this is the convention the other
        // parchment screens use too).
        int btnY = panelTop() + PANEL_H + BTN_GAP;
        int btnW = 112, gap = 10;
        this.addDrawableChild(GuideButton.of(cx - btnW - gap / 2, btnY, btnW, BTN_H,
            Text.literal("§a✔ Join"), b -> reply(true)));
        this.addDrawableChild(GuideButton.of(cx + gap / 2, btnY, btnW, BTN_H,
            Text.literal("§c✖ Stay"), b -> reply(false)));
    }

    private void reply(boolean accept) {
        if (replied) return;
        replied = true;
        ClientPlayNetworking.send(new RunInviteResponsePayload(accept ? 1 : 0));
        this.close();
    }

    private int secondsLeft() {
        if (startMs < 0) return timeoutSeconds;
        long elapsed = (System.currentTimeMillis() - startMs) / 1000L;
        return (int) Math.max(0, timeoutSeconds - elapsed);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (startMs < 0) startMs = System.currentTimeMillis();
        // Auto-decline just before the server's timeout so an unanswered popup can't
        // hold the rest of the party back.
        if (!replied && secondsLeft() <= 0) { reply(false); return; }

        // super.render() draws the widgets (the Join/Stay buttons) first; the panel is
        // painted afterwards but sits ABOVE them, so it never covers the buttons.
        super.render(ctx, mouseX, mouseY, delta);

        int x = (this.width - PANEL_W) / 2;
        int y = panelTop();
        GuideTheme.drawPanel(ctx, x, y, PANEL_W, PANEL_H);
        int cx = this.width / 2;

        GuideTheme.drawCentered(ctx, this.textRenderer, "§lJoin the Run?", cx, y + 12, GuideTheme.GOLD);
        GuideTheme.drawCentered(ctx, this.textRenderer, starterName + " is heading into", cx, y + 30, GuideTheme.INK);
        GuideTheme.drawCentered(ctx, this.textRenderer, biomeName, cx, y + 44, GuideTheme.GOLD);
        GuideTheme.drawCentered(ctx, this.textRenderer, "(" + secondsLeft() + "s)", cx, y + 60, GuideTheme.INK_FAINT);
    }

    @Override
    public void removed() {
        super.removed();
        // Safety net: closed without choosing (ESC fallback / forced close) counts as "stay".
        if (!replied) {
            replied = true;
            ClientPlayNetworking.send(new RunInviteResponsePayload(0));
        }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; } // ESC = stay on the island
}
