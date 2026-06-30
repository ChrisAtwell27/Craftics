package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideTheme;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Minimal HUD overlay shown only while the local player is in a merchant scene
 * (see {@link CombatState#isInScene()}). Draws a single bottom-right "Leave"
 * button. Because Fabric's HUD callback never receives clicks, the click is
 * detected in {@link CombatInputHandler}'s mouse path via
 * {@link #tryClickLeaveButton}, hit-testing against the same rect this renderer
 * publishes - so the button geometry can't drift between draw and hit-test.
 *
 * <p>A dedicated overlay (rather than reusing {@link CombatHudOverlay}) is needed
 * because that overlay early-returns when not in combat, and a scene is not combat.
 * Drawing the button directly in the HUD (instead of a {@code Screen}) keeps the
 * cursor free for click-to-walk; a mouse-capturing Screen would block it.
 */
public class SceneHudOverlay implements HudRenderCallback {

    // Match the combat HUD chrome (leather/gold book palette) so the button reads
    // as part of the same UI family.
    private static final int PANEL_BG = 0xCC000000 | (GuideTheme.COVER_EDGE & 0x00FFFFFF);
    private static final int PANEL_BORDER = GuideTheme.COVER_LIGHT;

    private static final int BTN_W = 70;
    private static final int BTN_H = 18;
    private static final int MARGIN = 8;

    /** Leave button rect {x1,y1,x2,y2} in scaled-GUI coordinates, published each
     *  render and hit-tested by {@link #tryClickLeaveButton}; null while hidden. */
    private static int[] leaveBtnRect = null;

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || !CombatState.isInScene() || client.player == null) {
            leaveBtnRect = null;
            return;
        }

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int x = screenW - BTN_W - MARGIN;
        int y = screenH - BTN_H - MARGIN;
        leaveBtnRect = new int[]{x, y, x + BTN_W, y + BTN_H};

        boolean hover = isMouseOver(client, x, y, BTN_W, BTN_H);
        ctx.fill(x - 1, y - 1, x + BTN_W + 1, y + BTN_H + 1, PANEL_BORDER);
        ctx.fill(x, y, x + BTN_W, y + BTN_H, hover ? 0xDD3A1414 : PANEL_BG);
        ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("← Leave"),
            x + BTN_W / 2, y + (BTN_H - 8) / 2, hover ? 0xFFFFE08A : 0xFFE8D8B0);
    }

    /**
     * Cursor position in scaled-GUI coordinates, or null without a window. Mirrors
     * {@link CombatHudOverlay}'s screen->framebuffer mapping.
     */
    private static double[] mouseGuiPos(MinecraftClient client) {
        var window = client.getWindow();
        if (window == null || window.getWidth() <= 0 || window.getHeight() <= 0) return null;
        double fx = (double) window.getScaledWidth() / window.getWidth();
        double fy = (double) window.getScaledHeight() / window.getHeight();
        return new double[]{client.mouse.getX() * fx, client.mouse.getY() * fy};
    }

    private static boolean isMouseOver(MinecraftClient client, int x, int y, int w, int h) {
        double[] m = mouseGuiPos(client);
        if (m == null) return false;
        return m[0] >= x && m[0] < x + w && m[1] >= y && m[1] < y + h;
    }

    /**
     * Hit-test a left-click against the Leave button. Called by
     * {@link CombatInputHandler} BEFORE the scene tile raycast; when it consumes
     * the click it sends {@link com.crackedgames.craftics.network.LeaveScenePayload}
     * and returns true so the click does not also fall through to a walk-to.
     */
    public static boolean tryClickLeaveButton(MinecraftClient client) {
        int[] r = leaveBtnRect;
        if (r == null || !CombatState.isInScene()) return false;
        double[] m = mouseGuiPos(client);
        if (m == null) return false;
        if (m[0] >= r[0] && m[0] < r[2] && m[1] >= r[1] && m[1] < r[3]) {
            ClientPlayNetworking.send(new com.crackedgames.craftics.network.LeaveScenePayload());
            return true;
        }
        return false;
    }
}
