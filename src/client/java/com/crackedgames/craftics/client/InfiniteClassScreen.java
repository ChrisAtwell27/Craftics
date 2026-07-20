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

    private static final int ROW_H = 24;
    private static final int ROW_GAP = 2;

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
    private int rowsViewportH;
    private int rowsContentH;
    private int maxScroll;
    private int scrollY;

    @Override
    protected void init() {
        super.init();
        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int rowCount = affinities.length + 1; // +1 for "No Class"
        rowsContentH = rowCount * (ROW_H + ROW_GAP) - ROW_GAP;

        // Wider panel prevents long class descriptions from clipping/cramping.
        panelW = MathHelper.clamp(width - 60, 360, 560);
        panelH = Math.min(58 + rowsContentH + 10, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        rowsY = panelY + 42;

        rowsViewportH = panelH - (rowsY - panelY) - 8;
        maxScroll = Math.max(0, rowsContentH - rowsViewportH);
        scrollY = MathHelper.clamp(scrollY, 0, maxScroll);
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

        // Clip the list so rows never draw outside the parchment box on small screens.
        ctx.enableScissor(panelX + 8, rowsY, panelX + panelW - 8, rowsY + rowsViewportH);

        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int y = rowsY - scrollY;
        for (int i = 0; i < affinities.length; i++) {
            PlayerProgression.Affinity a = affinities[i];
            boolean hover = rowHovered(mouseX, mouseY, y);
            drawRow(ctx, y, hover, a.icon + " " + a.displayName, "§8" + a.description);
            y += ROW_H + ROW_GAP;
        }
        boolean skipHover = rowHovered(mouseX, mouseY, y);
        drawRow(ctx, y, skipHover, "§7No Class", "§8Skip - just you and the logs.");

        ctx.disableScissor();

        // Scroll affordances
        if (maxScroll > 0) {
            int hintColor = 0xAA6E5C40;
            if (scrollY > 0) {
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("▲"),
                    panelX + panelW / 2, rowsY - 10, hintColor);
            }
            if (scrollY < maxScroll) {
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("▼"),
                    panelX + panelW / 2, rowsY + rowsViewportH + 1, hintColor);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // No vanilla blur/darkening: render() draws its own dim fill + parchment panel, and
        // the scissor calls flush those draws into the framebuffer BEFORE super.render()
        // would run applyBlur - so vanilla's gaussian pass blurred this screen's own UI.
        // Every other in-world screen (LevelUpScreen, GameOverScreen, ...) overrides this
        // the same way.
    }

    private boolean rowHovered(int mouseX, int mouseY, int rowY) {
        if (rowY + ROW_H <= rowsY || rowY >= rowsY + rowsViewportH) return false;
        return mouseX >= panelX + 8 && mouseX < panelX + panelW - 8
            && mouseY >= rowY && mouseY < rowY + ROW_H;
    }

    private void drawRow(DrawContext ctx, int y, boolean hover, String name, String sub) {
        int x0 = panelX + 8, x1 = panelX + panelW - 8;
        ctx.fill(x0, y, x1, y + ROW_H, hover ? 0xFFDCC68F : GuideTheme.PARCH_EDGE);
        ctx.fill(x0, y, x1, y + 1, GuideTheme.RULE);
        ctx.fill(x0, y + ROW_H - 1, x1, y + ROW_H, GuideTheme.RULE);
        ctx.fill(x0, y, x0 + 2, y + ROW_H, hover ? GuideTheme.GOLD : GuideTheme.GOLD_DIM);
        // Plain text (no shadow) stays crisp on parchment and avoids fuzzy double-edges.
        ctx.drawText(textRenderer, Text.literal(name), x0 + 7, y + 3,
            hover ? 0xFF5F3E00 : 0xFF3A2A14, false);
        ctx.drawText(textRenderer, Text.literal(sub), x0 + 7, y + 13, GuideTheme.INK_SOFT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
            int y = rowsY - scrollY;
            for (int i = 0; i < affinities.length; i++) {
                if (rowHovered((int) mouseX, (int) mouseY, y)) {
                    pick(i);
                    return true;
                }
                y += ROW_H + ROW_GAP;
            }
            if (rowHovered((int) mouseX, (int) mouseY, y)) {
                pick(InfiniteClassPickPayload.SKIP);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        if (mouseX < panelX + 8 || mouseX >= panelX + panelW - 8
                || mouseY < rowsY || mouseY >= rowsY + rowsViewportH) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int step = ROW_H + ROW_GAP;
        int delta = (int) Math.signum(-verticalAmount) * step;
        scrollY = MathHelper.clamp(scrollY + delta, 0, maxScroll);
        return true;
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
