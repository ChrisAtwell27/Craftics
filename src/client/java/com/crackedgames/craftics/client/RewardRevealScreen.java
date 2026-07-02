package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.network.DialogueChoicePayload;
import com.crackedgames.craftics.network.RewardRevealPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Standalone reward reveal for the piglin barter gamble and treasure-vault loot.
 * Reuses {@link RewardReveal}'s primitives for the staggered, sound-backed item
 * drop-in that gives the Game Over sequence its feel; the {@link RewardRevealPayload#STYLE_GAMBLE}
 * style adds a coin-flip intro that lands heads (paid off) or tails (a dud) before
 * the loot drops.
 *
 * <p>The items are already in the player's inventory by the time this opens
 * (server-side {@code LootDelivery}) - this screen is purely the reveal. On
 * completion + click it sends a {@link DialogueChoicePayload#ACTION_DISMISS}, which
 * resumes the active event's finish path on the server, exactly like the result
 * dialogue it replaces.
 */
public class RewardRevealScreen extends Screen {

    private static final int CELL = 22;
    private static final int PANEL_W = 280;
    private static final long STAGGER_MS = 120;    // gap between successive item drops
    private static final long DROP_MS = 300;       // per-item fall + settle
    private static final long COIN_SPIN_MS = 700;  // gamble coin spin before payout
    private static final int COIN_BIG = 26;

    private final int style;
    private final boolean success;
    private final String title;
    private final String subtitle;
    private final List<ItemStack> items;

    private long startMs = -1;
    private final boolean[] popPlayed;
    private boolean openStingPlayed = false;
    private boolean coinLandPlayed = false;
    private boolean doneStingPlayed = false;
    private boolean dismissed = false;
    private long lastSpinTickMs = -1;

    public RewardRevealScreen(int style, int success, String title, String subtitle, List<ItemStack> items) {
        super(Text.literal("Reward"));
        this.style = style;
        this.success = success != 0;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.items = items;
        this.popPlayed = new boolean[items.size()];
    }

    private long elapsed() { return startMs < 0 ? 0L : System.currentTimeMillis() - startMs; }
    /** Gamble holds the item drop-in until the coin has landed. */
    private long coinDelay() { return style == RewardRevealPayload.STYLE_GAMBLE ? COIN_SPIN_MS : 0L; }
    private long itemElapsed() { return Math.max(0L, elapsed() - coinDelay()); }
    private long itemRevealEnd() {
        if (items.isEmpty()) return 0L;
        return (long) (items.size() - 1) * STAGGER_MS + DROP_MS + 200L;
    }
    private boolean revealComplete() { return itemElapsed() >= itemRevealEnd(); }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Opens mid-cinematic - dim the event scene rather than blur to black.
        ctx.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (startMs < 0) startMs = System.currentTimeMillis();
        super.render(ctx, mouseX, mouseY, delta);

        if (!openStingPlayed) {
            openStingPlayed = true;
            RewardReveal.playMaster(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 0.7f);
        }

        int perRow = Math.max(1, (PANEL_W - 24) / CELL);
        int rows = items.isEmpty() ? 1 : (items.size() + perRow - 1) / perRow;
        int panelH = 14 + 14 + 10 + rows * CELL + 16;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - panelH) / 2 - 6;
        int cx = this.width / 2;

        // Panel eases in with a subtle scale-settle so the reveal has a curtain-up.
        float in = RewardReveal.smoothstep(Math.min(1f, elapsed() / 220f));
        RewardReveal.pushScaledAround(ctx, 0.93f + 0.07f * RewardReveal.easeOutBack(in),
            cx, y + panelH / 2f);
        GuideTheme.drawPanel(ctx, x, y, PANEL_W, panelH);
        ctx.getMatrices().pop();

        GuideTheme.drawCentered(ctx, this.textRenderer, title, cx, y + 8, GuideTheme.GOLD);
        if (!subtitle.isEmpty()) {
            GuideTheme.drawCentered(ctx, this.textRenderer, subtitle, cx, y + 20, GuideTheme.INK);
        }
        GuideTheme.drawRule(ctx, x + 14, y + 30, PANEL_W - 28);

        // Gamble coin intro spins above the panel, lands heads/tails, then loot drops.
        if (style == RewardRevealPayload.STYLE_GAMBLE && elapsed() < coinDelay() + 150) {
            drawGambleCoin(ctx, cx, y - 16, elapsed());
        }

        drawItemGrid(ctx, x, y + 36, perRow, mouseX, mouseY);

        if (!items.isEmpty() && !doneStingPlayed && revealComplete()) {
            doneStingPlayed = true;
            RewardReveal.playMaster(net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, 0.5f, 1.2f);
            // A triumphant reveal earns a burst of gold sparks off the panel top.
            if (style != RewardRevealPayload.STYLE_GAMBLE || success) {
                RewardReveal.burst(cx, y + 4, 26, 0xF7C84A, 90f);
            }
        }
        // Spark layer above the panel, below tooltips.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 350);
        RewardReveal.tickAndDrawParticles(ctx);
        ctx.getMatrices().pop();

        if (revealComplete()) {
            float p = 0.45f + 0.55f * RewardReveal.pulse(1400);
            int a = Math.round(0xFF * p) << 24;
            GuideTheme.drawCentered(ctx, this.textRenderer, "(click to continue)",
                cx, y + panelH - 12, a | (GuideTheme.INK_FAINT & 0xFFFFFF));
        }
    }

    private void drawItemGrid(DrawContext ctx, int panelX, int gridTop, int perRow, int mouseX, int mouseY) {
        if (items.isEmpty()) return;
        long t = itemElapsed();
        int gridW = Math.min(items.size(), perRow) * CELL;
        int startX = panelX + (PANEL_W - gridW) / 2;
        ItemStack hovered = null;
        int hx = 0, hy = 0;

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            int ix = startX + (i % perRow) * CELL + 3;
            int iy = gridTop + (i / perRow) * CELL + 3;
            // Cell backdrop so each item reads on the parchment, even before it drops.
            ctx.fill(ix - 2, iy - 2, ix + 18, iy + 18, 0x33000000);
            long launch = (long) i * STAGGER_MS;
            if (t < launch) continue;

            float p = Math.min(1f, (t - launch) / (float) DROP_MS);
            float eased = RewardReveal.easeOutBack(p);
            float scale = 1.35f - 0.35f * RewardReveal.smoothstep(p);
            int yShift = Math.round(-(1f - eased) * 14f); // fall in from ~14px above
            int cxItem = ix + 8, cyItem = iy + 8;

            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, yShift, 0);
            ctx.getMatrices().translate(cxItem, cyItem, 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.getMatrices().translate(-cxItem, -cyItem, 0);
            ctx.drawItem(stack, ix, iy);
            ctx.getMatrices().pop();

            if (p >= 1f && !popPlayed[i]) {
                popPlayed[i] = true;
                RewardReveal.playMaster(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    0.5f, rarityPitch(stack));
            }
            RewardReveal.drawPopRing(ctx, ix - 2, iy - 2, ix + 18, iy + 18,
                (t - (launch + DROP_MS)) / 200f, rarityColor(stack));

            if (p >= 1f && mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                hovered = stack; hx = mouseX; hy = mouseY;
            }
        }
        // Counts in a separate pass above the item-icon Z layer; settled items only.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 250);
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.getCount() <= 1) continue;
            if (t < (long) i * STAGGER_MS + DROP_MS) continue;
            int ix = startX + (i % perRow) * CELL + 3;
            int iy = gridTop + (i / perRow) * CELL + 3;
            Text c = Text.literal(String.valueOf(stack.getCount()));
            int cwid = this.textRenderer.getWidth(c);
            ctx.drawTextWithShadow(this.textRenderer, c, ix + 16 - cwid, iy + 9, 0xFFFFFFFF);
        }
        ctx.getMatrices().pop();

        if (hovered != null) {
            ctx.drawTooltip(this.textRenderer,
                Screen.getTooltipFromItem(MinecraftClient.getInstance(), hovered), hx, hy);
        }
    }

    /** Spinning gamble coin that lands a gold star (paid off) or a red X (a dud). */
    private void drawGambleCoin(DrawContext ctx, int cx, int cy, long t) {
        boolean spinning = t < COIN_SPIN_MS;
        float squash;
        int faceCol;
        if (spinning) {
            float phase = (t % 200) / 200f;        // ~5 flips/sec, continuous tumble
            squash = Math.abs((float) Math.cos(phase * Math.PI * 2));
            faceCol = GuideTheme.GOLD;
            if (lastSpinTickMs < 0 || t - lastSpinTickMs >= 90) {
                lastSpinTickMs = t;
                RewardReveal.playMaster(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 0.25f, 1.6f);
            }
        } else {
            squash = 1f;
            faceCol = success ? 0xFF2E7D32 : 0xFFB02020;
            if (!coinLandPlayed) {
                coinLandPlayed = true;
                RewardReveal.playMaster(
                    success ? net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME
                            : net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND,
                    0.6f, success ? 1.5f : 0.9f);
                // The landing pays off (or stings) in sparks: gold shower for a
                // win, a dull grey puff for a dud.
                RewardReveal.burst(cx, cy, success ? 22 : 10,
                    success ? 0xF7C84A : 0x777069, success ? 80f : 40f);
            }
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 300);
        String face = spinning ? "?" : (success ? "★" : "✖");
        int gcol = spinning ? 0xFF3B2B12 : (success ? 0xFFF7E27A : 0xFFFF4040);
        RewardReveal.drawCoin(ctx, this.textRenderer, cx, cy, COIN_BIG / 2, squash, faceCol, face, gcol);
        ctx.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (!revealComplete()) { skip(); return true; }
            dismiss();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Snap the reveal to its end, muting the remaining per-item chimes. */
    private void skip() {
        startMs = System.currentTimeMillis() - coinDelay() - itemRevealEnd() - 1L;
        for (int i = 0; i < popPlayed.length; i++) popPlayed[i] = true;
        coinLandPlayed = true;
        if (!doneStingPlayed) {
            doneStingPlayed = true;
            RewardReveal.playMaster(net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, 0.5f, 1.2f);
        }
    }

    private void dismiss() {
        if (dismissed) return;
        dismissed = true;
        ClientPlayNetworking.send(new DialogueChoicePayload(DialogueChoicePayload.ACTION_DISMISS));
        this.close();
    }

    /** Called by the client receiver right before it replaces this reveal with the next
     *  server-driven screen, so the close() safety net below doesn't fire a stray DISMISS
     *  on normal flow progression. */
    public void markSuperseded() {
        dismissed = true;
    }

    @Override
    public void removed() {
        super.removed();
        RewardReveal.clearParticles();
        // Safety net: if this reveal is closed without dismissing (ESC fallback, forced
        // close) still send DISMISS so the event gate releases and the party isn't left
        // softlocked waiting on this player. Idempotent via `dismissed`.
        if (!dismissed) {
            dismissed = true;
            ClientPlayNetworking.send(new DialogueChoicePayload(DialogueChoicePayload.ACTION_DISMISS));
        }
    }

    private static int rarityColor(ItemStack stack) {
        return switch (stack.getRarity()) {
            case UNCOMMON -> 0xFFFFFF55;
            case RARE     -> 0xFF55FFFF;
            case EPIC     -> 0xFFFF55FF;
            default       -> 0xFFFFFFFF;
        };
    }

    private static float rarityPitch(ItemStack stack) {
        return switch (stack.getRarity()) {
            case UNCOMMON -> 1.2f;
            case RARE     -> 1.45f;
            case EPIC     -> 1.7f;
            default       -> 1.0f;
        };
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; } // must dismiss to resume the event
}
