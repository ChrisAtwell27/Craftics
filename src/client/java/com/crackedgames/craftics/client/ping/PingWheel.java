package com.crackedgames.craftics.client.ping;

import com.crackedgames.craftics.client.CombatLog;
import com.crackedgames.craftics.client.CombatState;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.PingType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * The radial menu for choosing what a ping means.
 *
 * <h2>How it behaves</h2>
 *
 * <p>Hold the ping key: the wheel opens where the cursor is. Flick toward an option and let go.
 * Let go without moving and it sends the plain "look here" ping, so the common case costs one
 * tap and never makes anyone read a menu.
 *
 * <p>The target tile is captured when the key goes <em>down</em>, not when it comes up. This is
 * the difference between a ping tool that works and one that is infuriating: choosing an option
 * means moving the mouse, moving the mouse moves the cursor off the tile, and a wheel that read
 * the tile on release would mark whatever happened to be under the cursor after the flick -
 * never the thing being pointed at.
 *
 * <p>Options are laid out clockwise from straight up. Fixed positions, always the same six, so
 * the gesture becomes muscle memory and the labels stop being read at all.
 */
public class PingWheel implements HudRenderCallback {

    /** Distance from the wheel's centre to each option chip. */
    private static final int RADIUS = 54;

    /** Chip size. */
    private static final int CHIP = 30;

    /** Cursor travel needed to count as choosing rather than tapping. Generous, because the
     *  alternative failure - a flick that registers as a tap and sends the wrong ping - is worse
     *  than one that needs a slightly longer flick. */
    private static final int DEAD_ZONE = 18;

    private static final int BG = 0xCC10131A;
    private static final int BORDER_DIM = 0xFF3A4152;
    private static final int LABEL = 0xFFE8E8E8;

    private static boolean open = false;

    /** Wheel centre, in scaled GUI pixels: where the cursor was when the key went down. */
    private static int centerX, centerY;

    /** The tile captured at key-down. Null means there was nothing under the cursor, in which
     *  case the wheel never opens - a ping with no tile has nothing to say. */
    private static GridPos target;

    /** Option under the cursor right now, or null while inside the dead zone. */
    private static PingType hovered;

    /** Where a ping means anything: somewhere with a grid and a party looking at it. */
    private static boolean onAGrid() {
        return CombatState.isInCombat() || CombatState.isInScene();
    }

    /**
     * Try to open the wheel. Refuses off the grid and when the cursor is not over a tile,
     * which is why the caller does not need to check either.
     *
     * @return true if the wheel opened
     */
    public static boolean tryOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (open) return true;
        if (client.currentScreen != null) return false;
        if (!onAGrid()) return false;
        GridPos tile = CombatState.getHoveredTile();
        if (tile == null) return false;

        target = tile;
        hovered = null;
        int[] cursor = cursorPos(client);
        centerX = cursor[0];
        centerY = cursor[1];
        open = true;
        return true;
    }

    public static boolean isOpen() { return open; }

    /**
     * Close the wheel and send whatever was chosen. A release inside the dead zone sends
     * {@link PingType#LOOK}, so a tap is always a valid ping rather than a cancelled one.
     */
    public static void closeAndSend() {
        if (!open) return;
        GridPos tile = target;
        PingType chosen = hovered != null ? hovered : PingType.LOOK;
        close();
        if (tile == null) return;
        ClientPlayNetworking.send(
            new com.crackedgames.craftics.network.PingPayload(tile.x(), tile.z(), chosen.ordinal()));
    }

    /** Close without sending. Used when combat ends or a screen opens under the held key. */
    public static void close() {
        open = false;
        target = null;
        hovered = null;
    }

    /**
     * Per-tick upkeep: shut the wheel if the situation it was opened in has gone away.
     *
     * <p>Without this a wheel opened just before a fight ended would hang on screen until the
     * key was released, over a HUD that no longer has an arena behind it.
     */
    public static void tick() {
        if (!open) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!onAGrid() || client.currentScreen != null) close();
    }

    /** Cursor position in scaled GUI pixels. */
    private static int[] cursorPos(MinecraftClient client) {
        double sw = client.getWindow().getScaledWidth();
        double sh = client.getWindow().getScaledHeight();
        double ww = Math.max(1, client.getWindow().getWidth());
        double wh = Math.max(1, client.getWindow().getHeight());
        return new int[] {
            (int) (client.mouse.getX() * sw / ww),
            (int) (client.mouse.getY() * sh / wh)
        };
    }

    /**
     * Which option a cursor offset selects, or null inside the dead zone.
     *
     * <p>The layout itself lives on {@link PingType#fromOffset}, next to the options it orders
     * and where a test can reach it; {@link #chipPos} below has to agree with it, and the pair
     * is the reason both are written in terms of "clockwise from straight up".
     */
    public static PingType select(int dx, int dy) {
        return PingType.fromOffset(dx, dy, DEAD_ZONE);
    }

    /** Centre of option {@code index}'s chip, relative to the wheel centre. */
    private static int[] chipPos(int index, int count) {
        double angle = index * (Math.PI * 2 / count);
        return new int[] {
            (int) Math.round(Math.sin(angle) * RADIUS),
            (int) Math.round(-Math.cos(angle) * RADIUS)
        };
    }

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!open) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        int[] cursor = cursorPos(client);
        hovered = select(cursor[0] - centerX, cursor[1] - centerY);

        PingType[] all = PingType.values();
        for (int i = 0; i < all.length; i++) {
            PingType type = all[i];
            int[] off = chipPos(i, all.length);
            int cx = centerX + off[0];
            int cy = centerY + off[1];
            boolean sel = type == hovered;

            int x0 = cx - CHIP / 2, y0 = cy - CHIP / 2;
            int x1 = cx + CHIP / 2, y1 = cy + CHIP / 2;

            ctx.fill(x0, y0, x1, y1, sel ? withAlpha(type.color, 0x66) : BG);
            int border = sel ? type.color : BORDER_DIM;
            ctx.fill(x0, y0, x1, y0 + 1, border);
            ctx.fill(x0, y1 - 1, x1, y1, border);
            ctx.fill(x0, y0, x0 + 1, y1, border);
            ctx.fill(x1 - 1, y0, x1, y1, border);

            int glyphWidth = client.textRenderer.getWidth(type.glyph);
            ctx.drawText(client.textRenderer, type.glyph,
                cx - glyphWidth / 2, cy - 4, sel ? 0xFFFFFFFF : type.color, true);
        }

        // The centre says what letting go will do right now. A wheel whose options are glyphs
        // needs exactly one line of prose somewhere, and this is the only place the eye is
        // already looking.
        String caption = hovered != null ? hovered.label : PingType.LOOK.label;
        int captionWidth = client.textRenderer.getWidth(caption);
        int cx0 = centerX - captionWidth / 2 - 4;
        int cy0 = centerY - 6;
        ctx.fill(cx0, cy0, cx0 + captionWidth + 8, cy0 + 13, BG);
        ctx.drawText(client.textRenderer, caption, centerX - captionWidth / 2, cy0 + 3,
            hovered != null ? hovered.color : LABEL, true);
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Show an incoming ping in the combat log.
     *
     * <p>The pillar says where; this says who and what. Off-screen pings would otherwise be
     * completely silent, and "who asked me to take this one" is the part a player needs in
     * words rather than in colour.
     */
    public static void logIncoming(String senderName, PingType type) {
        CombatLog.addMessage("§7" + senderName + " " + type.chatColor() + type.glyph + " " + type.label);
    }
}
