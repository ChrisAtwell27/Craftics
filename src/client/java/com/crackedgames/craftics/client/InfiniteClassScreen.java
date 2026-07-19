package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.network.InfiniteClassPickPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Infinite-mode class selection, shown once at run start: one class per affinity (+1
 * point and a modest starter weapon), or Skip to walk in with nothing but the log
 * bootstrap. Options come straight from {@link PlayerProgression.Affinity}, so a new
 * affinity appears here without this screen changing.
 *
 * <p>Opened DEFERRED: the run start teleports the party between dimensions, which wipes
 * whatever screen is open at that moment. The offer payload just raises {@link #pending};
 * a client tick opens the screen once the world is stable and no other screen is up.
 *
 * <p>Closing without choosing (ESC) counts as Skip - the server's one-shot offer is
 * always answered, so it can never be banked and cashed in later.
 */
public class InfiniteClassScreen extends Screen {

    /** An offer arrived and the screen hasn't been shown yet. */
    private static volatile boolean pending = false;

    private boolean answered = false;

    private static final int ROW_H = 22;

    public InfiniteClassScreen() {
        super(Text.literal("Choose Your Class"));
    }

    /** Client init hook: payload receiver + the deferred opener. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.InfiniteClassOfferPayload.ID,
            (payload, context) -> pending = true);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!pending || client.player == null || client.world == null) return;
            if (client.currentScreen != null) return; // wait out loading/other screens
            pending = false;
            client.mouse.unlockCursor();
            client.setScreen(new InfiniteClassScreen());
        });
    }

    private int panelX, panelY, panelW, panelH, rowsY;

    @Override
    protected void init() {
        super.init();
        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        panelW = MathHelper.clamp(width - 80, 300, 400);
        panelH = 46 + (affinities.length + 1) * (ROW_H + 2) + 10;
        panelH = Math.min(panelH, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        rowsY = panelY + 42;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);

        // Parchment panel with a leather border, matching the guide book's language.
        ctx.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, GuideTheme.COVER_EDGE);
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, GuideTheme.COVER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, GuideTheme.PARCH);

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§5§l∞ CHOOSE YOUR CLASS ∞"),
            panelX + panelW / 2, panelY + 8, GuideTheme.GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§7+1 affinity and a starter weapon - or go in with nothing."),
            panelX + panelW / 2, panelY + 22, GuideTheme.INK_SOFT);

        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int y = rowsY;
        for (int i = 0; i < affinities.length; i++) {
            PlayerProgression.Affinity a = affinities[i];
            boolean hover = rowHovered(mouseX, mouseY, y);
            drawRow(ctx, y, hover, a.icon + " §l" + a.displayName, "§8" + a.description);
            y += ROW_H + 2;
        }
        boolean skipHover = rowHovered(mouseX, mouseY, y);
        drawRow(ctx, y, skipHover, "§7§lNo Class", "§8Skip - just you and the logs.");

        super.render(ctx, mouseX, mouseY, delta);
    }

    private boolean rowHovered(int mouseX, int mouseY, int rowY) {
        return mouseX >= panelX + 8 && mouseX < panelX + panelW - 8
            && mouseY >= rowY && mouseY < rowY + ROW_H;
    }

    private void drawRow(DrawContext ctx, int y, boolean hover, String name, String sub) {
        int x0 = panelX + 8, x1 = panelX + panelW - 8;
        ctx.fill(x0, y, x1, y + ROW_H, hover ? 0xFFDCC68F : GuideTheme.PARCH_EDGE);
        ctx.fill(x0, y, x1, y + 1, GuideTheme.RULE);
        ctx.fill(x0, y + ROW_H - 1, x1, y + ROW_H, GuideTheme.RULE);
        ctx.fill(x0, y, x0 + 2, y + ROW_H, hover ? GuideTheme.GOLD : GuideTheme.GOLD_DIM);
        ctx.drawTextWithShadow(textRenderer, Text.literal(name), x0 + 7, y + 3,
            hover ? GuideTheme.GOLD : 0xFF3A2A14);
        ctx.drawText(textRenderer, Text.literal(sub), x0 + 7, y + 12, GuideTheme.INK_SOFT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
            int y = rowsY;
            for (int i = 0; i < affinities.length; i++) {
                if (rowHovered((int) mouseX, (int) mouseY, y)) {
                    pick(i);
                    return true;
                }
                y += ROW_H + 2;
            }
            if (rowHovered((int) mouseX, (int) mouseY, y)) {
                pick(InfiniteClassPickPayload.SKIP);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void pick(int ordinal) {
        if (answered) return;
        answered = true;
        ClientPlayNetworking.send(new InfiniteClassPickPayload(ordinal));
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void close() {
        // ESC (or anything else that closes us) is an explicit Skip, so the server's
        // outstanding offer is always resolved.
        if (!answered) {
            answered = true;
            ClientPlayNetworking.send(new InfiniteClassPickPayload(InfiniteClassPickPayload.SKIP));
        }
        super.close();
    }

    @Override
    public boolean shouldPause() { return false; }
}
