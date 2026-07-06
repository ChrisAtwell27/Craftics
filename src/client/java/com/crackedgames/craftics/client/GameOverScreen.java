package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.network.GameOverAckPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Party-death game-over screen. Shows the player's losable items in a grid, then
 * runs a fast pipelined coin-flip per item: a coin spins, then shrinks onto its
 * item and settles heads (kept) or tails (lost = crossed out + greyed). Coin N+1
 * starts before coin N lands. When the last coin resolves, a Continue button
 * sends the C2S ack so the server applies the (already-decided) loss.
 */
public class GameOverScreen extends Screen {

    private static final int CELL = 22;
    private static final int PANEL_W = 300;
    // Animation timing (ms). Each item gets its own coin; coins are staggered so a
    // few are in flight at once, but every coin spins, then visibly flies + shrinks
    // onto its item before the next set finishes.
    private static final long STAGGER_MS = 180;   // gap between successive coin launches
    private static final long SPIN_MS = 420;      // center spin before flying to the item
    private static final long LAND_MS = 360;      // fly + shrink onto the item
    private static final int COIN_BIG = 26;       // coin diameter while spinning in center

    private final List<ItemStack> items;
    private final int[] lostCounts;  // per group: how many units of it are lost (0 = fully kept)
    private final int emeraldsLost;
    private final int xpLevelsLost;

    private long startMs = -1;     // set on first render
    private boolean acked = false;
    private GuideButton continueButton;

    // Animation/sound bookkeeping (fire-once guards).
    private boolean[] landed;          // per item: landing clink played
    private long[] landedAt;           // per item: ms the coin landed (for the pop)
    private long lastSpinTickMs = -1;  // throttle the spin tick sound
    private long lastAshMs = -1;       // wall-clock ash spawn accumulator
    private boolean openStingPlayed = false;
    private boolean allDoneStingPlayed = false;

    public GameOverScreen(List<ItemStack> items, List<Integer> lostCounts,
                          int emeraldsLost, int xpLevelsLost) {
        super(Text.literal("Game Over"));
        this.items = items;
        this.lostCounts = new int[lostCounts.size()];
        for (int i = 0; i < lostCounts.size(); i++) this.lostCounts[i] = lostCounts.get(i);
        this.emeraldsLost = emeraldsLost;
        this.xpLevelsLost = xpLevelsLost;
        this.landed = new boolean[items.size()];
        this.landedAt = new long[items.size()];
    }

    /** True when this group lost no units (coin lands heads/star). */
    private boolean kept(int i) { return lostCounts[i] <= 0; }

    /** Outcome class for item {@code i}: 0 = kept (lost none), 1 = partial loss,
     *  2 = fully lost (every unit gone). */
    private int outcome(int i) {
        if (lostCounts[i] <= 0) return 0;
        return lostCounts[i] >= items.get(i).getCount() ? 2 : 1;
    }

    /** Play a UI sound at master volume (Screens have no world position). */
    private void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        RewardReveal.playMaster(sound, volume, pitch);
    }

    /** Total animation length in ms (last coin's launch + spin + land). */
    private long animTotalMs() {
        int n = Math.max(1, items.size());
        return (n - 1) * STAGGER_MS + SPIN_MS + LAND_MS;
    }

    private long elapsed() {
        if (startMs < 0) return 0;
        return System.currentTimeMillis() - startMs;
    }

    /** No-shadow centered text. */
    private void centered(DrawContext ctx, String text, int cx, int y, int color) {
        Text t = Text.literal(text);
        ctx.drawText(this.textRenderer, t, cx - this.textRenderer.getWidth(t) / 2, y, color, false);
    }

    @Override
    protected void init() {
        // Continue button hidden until the animation finishes; added in render once done.
        this.continueButton = GuideButton.of(
            this.width / 2 - 100, this.height / 2 + 90, 200, 20,
            Text.literal("§cContinue"), btn -> sendAck());
    }

    private void sendAck() {
        if (acked) return;
        acked = true;
        ClientPlayNetworking.send(new GameOverAckPayload());
        this.close();
    }

    @Override
    public void removed() {
        super.removed();
        RewardReveal.clearParticles();
        // Safety net: if this screen is closed without the Continue button (ESC fallback,
        // forced close) still send the ack so the party advances immediately instead of
        // waiting out the server-side game-over timeout. Idempotent via `acked`.
        if (!acked) {
            acked = true;
            ClientPlayNetworking.send(new GameOverAckPayload());
        }
    }

    /** Solemn backdrop: deep darkening with a blood-red edge vignette and
     *  cinematic letterbox bars sliding in, instead of the vanilla menu blur -
     *  a defeat should feel heavier than opening a chest. */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xCC000000);
        int edge = Math.max(24, this.height / 6);
        ctx.fillGradient(0, 0, this.width, edge, 0x66550000, 0x00000000);
        ctx.fillGradient(0, this.height - edge, this.width, this.height, 0x00000000, 0x66550000);
        float lb = RewardReveal.smoothstep(Math.min(1f, elapsed() / 400f));
        int barH = Math.round(lb * Math.max(16, this.height / 12));
        ctx.fill(0, 0, this.width, barH, 0xFF000000);
        ctx.fill(0, this.height - barH, this.width, this.height, 0xFF000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (startMs < 0) startMs = System.currentTimeMillis();
        long t = elapsed();

        // Defeat sting on first frame.
        if (!openStingPlayed) {
            openStingPlayed = true;
            playSound(net.minecraft.sound.SoundEvents.ENTITY_WITHER_DEATH, 0.5f, 0.6f);
        }
        // Soft, fast tick while any coin is mid-spin (throttled ~every 90ms).
        boolean anySpinning = false;
        for (int i = 0; i < items.size(); i++) {
            long s = (long) i * STAGGER_MS;
            if (t >= s && t < s + SPIN_MS) { anySpinning = true; break; }
        }
        if (anySpinning && (lastSpinTickMs < 0 || t - lastSpinTickMs >= 90)) {
            lastSpinTickMs = t;
            playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.7f);
        }
        // Per-coin landing clink (fire once as each coin settles).
        for (int i = 0; i < items.size(); i++) {
            long s = (long) i * STAGGER_MS;
            if (!landed[i] && t >= s + SPIN_MS + LAND_MS) {
                landed[i] = true;
                landedAt[i] = t;
                int oc = outcome(i);
                if (oc == 0) {
                    playSound(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.5f);
                } else if (oc == 1) {
                    playSound(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 0.9f);
                } else {
                    playSound(net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 0.9f);
                }
            }
        }
        // Completion sting once every coin has resolved.
        if (!allDoneStingPlayed && t >= animTotalMs()) {
            allDoneStingPlayed = true;
            playSound(net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, 0.6f, 0.8f);
        }

        // Slow ash drifting down the whole screen sets the funeral mood; the
        // occasional ember catches the eye without stealing it. Spawned at a
        // fixed wall-clock rate (~18/sec) so density doesn't scale with fps.
        if (this.width > 0) {
            if (lastAshMs < 0) lastAshMs = t;
            double expected = (t - lastAshMs) * 0.018;
            lastAshMs = t;
            int n = (int) expected;
            if (Math.random() < expected - n) n++;
            for (int k = 0; k < n; k++) {
                boolean ember = Math.random() < 0.18;
                RewardReveal.ash((float) (Math.random() * this.width), -4f,
                    ember ? 0xC24A2A : 0x8A8178);
            }
        }

        // Panel (content-fit-ish): header + summary + grid rows, easing in with
        // a subtle scale-settle over the first ~250ms.
        int perRow = Math.max(1, (PANEL_W - 24) / CELL);
        int rows = items.isEmpty() ? 1 : (items.size() + perRow - 1) / perRow;
        int panelH = 16 + 22 + 16 + rows * CELL + 30;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - panelH) / 2 - 10;
        int cx = this.width / 2;
        float in = RewardReveal.smoothstep(Math.min(1f, t / 250f));
        float panelScale = 0.92f + 0.08f * RewardReveal.easeOutBack(in);
        RewardReveal.pushScaledAround(ctx, panelScale, cx, y + panelH / 2f);
        GuideTheme.drawPanel(ctx, x, y, PANEL_W, panelH);
        ctx.getMatrices().pop();

        // Title: large, slow red heartbeat pulse - the moment should land hard.
        float titleIn = RewardReveal.smoothstep(Math.min(1f, t / 500f));
        float titleScale = (1.9f - 0.3f * titleIn); // eases 1.9 -> 1.6
        int titleColor = GuideTheme.brighten(0xFFB02020,
            Math.round(24 * RewardReveal.pulse(1600)));
        if (titleIn > 0.05f) {
            RewardReveal.drawCenteredScaled(ctx, this.textRenderer, "☠ GAME OVER ☠",
                cx, y + 15, titleScale * titleIn, titleColor, true);
        }
        String summary = "";
        if (emeraldsLost > 0) summary += "Lost " + emeraldsLost + " emeralds";
        if (xpLevelsLost > 0) summary += (summary.isEmpty() ? "Lost " : ", ") + xpLevelsLost + " XP levels";
        if (!summary.isEmpty()) centered(ctx, summary, cx, y + 30, GuideTheme.INK_SOFT);
        GuideTheme.drawRule(ctx, x + 14, y + 41, PANEL_W - 28);

        // Item grid + per-item resolution overlay.
        int gridTop = y + 46;
        int gridW = Math.min(items.size(), perRow) * CELL;
        int startX = x + (PANEL_W - gridW) / 2;
        for (int i = 0; i < items.size(); i++) {
            int ix = startX + (i % perRow) * CELL + 3;
            int iy = gridTop + (i / perRow) * CELL + 3;
            long itemStart = (long) i * STAGGER_MS;
            boolean resolved = t >= itemStart + SPIN_MS + LAND_MS;
            // Cell backdrop so every item reads on the parchment.
            ctx.fill(ix - 2, iy - 2, ix + 18, iy + 18, 0x33000000);
            ctx.drawItem(items.get(i), ix, iy);
            if (!resolved) {
                // Not yet decided: dim veil until this item's coin lands.
                ctx.fill(ix, iy, ix + 16, iy + 16, 0x77000000);
            } else if (kept(i)) {
                // KEPT (lost nothing): soft green glow ring so it's a visible result.
                ctx.fill(ix - 2, iy - 2, ix + 18, iy - 1, 0xCC2E7D32);
                ctx.fill(ix - 2, iy + 17, ix + 18, iy + 18, 0xCC2E7D32);
                ctx.fill(ix - 2, iy - 2, ix - 1, iy + 18, 0xCC2E7D32);
                ctx.fill(ix + 17, iy - 2, ix + 18, iy + 18, 0xCC2E7D32);
            } else {
                int total = items.get(i).getCount();
                boolean fully = lostCounts[i] >= total;
                if (fully) {
                    // FULLY LOST: grey out + red X (it's gone).
                    ctx.fill(ix, iy, ix + 16, iy + 16, 0xCC202020);
                    drawX(ctx, ix, iy, 16, 0xFFD03030);
                } else {
                    // PARTIAL LOSS: an orange hyphen struck through the stack - the item
                    // survives but a portion of it was lost.
                    ctx.fill(ix - 1, iy + 7, ix + 17, iy + 9, 0xFFFF9000);
                    ctx.fill(ix - 1, iy + 7, ix + 17, iy + 8, 0xFFFFB347); // bright top edge
                }
            }
            // Landing pop: a bright ring that expands + fades over ~220ms right after
            // the coin settles, so each resolution has a satisfying impact.
            if (resolved && landedAt[i] > 0) {
                long since = t - landedAt[i];
                if (since < 220) {
                    int oc = outcome(i);
                    int rgb = oc == 0 ? 0x6FFF6F : (oc == 1 ? 0xFFB347 : 0xFF6F6F);
                    RewardReveal.drawPopRing(ctx, ix - 2, iy - 2, ix + 18, iy + 18, since / 220f, rgb);
                }
            }
        }

        // Counts in a SEPARATE pass, pushed above the item-icon Z layer so a
        // neighbouring icon can never draw over them. Once a group is resolved the
        // bottom-right count is the SURVIVING amount (total - lost); a small red
        // "-N" above it shows how many were lost from the stack.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 250);
        for (int i = 0; i < items.size(); i++) {
            int total = items.get(i).getCount();
            long itemStart = (long) i * STAGGER_MS;
            boolean resolved = t >= itemStart + SPIN_MS + LAND_MS;
            int ix = startX + (i % perRow) * CELL + 3;
            int iy = gridTop + (i / perRow) * CELL + 3;
            int surviving = resolved ? total - lostCounts[i] : total;
            if (surviving > 1) {
                Text c = Text.literal(String.valueOf(surviving));
                int cwid = this.textRenderer.getWidth(c);
                ctx.drawTextWithShadow(this.textRenderer, c, ix + 16 - cwid, iy + 9, 0xFFFFFFFF);
            }
            if (resolved && lostCounts[i] > 0 && lostCounts[i] < total) {
                Text lo = Text.literal("-" + lostCounts[i]);
                ctx.drawTextWithShadow(this.textRenderer, lo, ix - 3, iy - 4, 0xFFFF5555);
            }
        }
        ctx.getMatrices().pop();

        // Draw every coin currently in flight (spinning above the box, then flying
        // down onto its item). Coins spin ABOVE the panel so they're never hidden
        // behind it. Pushed forward in Z so they sit over everything.
        int spinY = y - 26; // just above the panel's top edge
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 300);
        for (int i = 0; i < items.size(); i++) {
            long s = (long) i * STAGGER_MS;
            if (t >= s && t < s + SPIN_MS + LAND_MS) {
                drawCoin(ctx, i, t - s, perRow, startX, gridTop, spinY);
            }
        }
        ctx.getMatrices().pop();

        // Anchor the Continue button just below the panel and reveal it once every
        // flip has resolved. The player must click it - we never force them off.
        continueButton.setPosition(this.width / 2 - 100, y + panelH + 8);
        if (t >= animTotalMs() && !this.children().contains(continueButton)) {
            this.addDrawableChild(continueButton);
        }

        // Ash layer drifts over the whole composition (but under tooltips).
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 350);
        RewardReveal.tickAndDrawParticles(ctx);
        ctx.getMatrices().pop();

        // Hover tooltip on resolved cells.
        for (int i = 0; i < items.size(); i++) {
            int ix = startX + (i % perRow) * CELL + 3;
            int iy = gridTop + (i / perRow) * CELL + 3;
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                ctx.drawTooltip(this.textRenderer,
                    Screen.getTooltipFromItem(MinecraftClient.getInstance(), items.get(i)), mouseX, mouseY);
                break;
            }
        }
    }

    /** Draw one item's coin at animation time {@code local} (ms since its launch):
     *  spins as a gold disc in the center, then flies + shrinks onto its item cell,
     *  resolving to a green (kept) or red (lost) face on landing. */
    private void drawCoin(DrawContext ctx, int index, long local, int perRow, int startX, int gridTop, int spinY) {
        int cxScreen = this.width / 2;
        int cyScreen = spinY;                         // spin point, above the box
        int targetX = startX + (index % perRow) * CELL + 3 + 8; // item cell center
        int targetY = gridTop + (index / perRow) * CELL + 3 + 8;

        boolean spinning = local < SPIN_MS;
        int diameter, dx, dy;
        // Continuous tumble: the coin's width follows |cos| of the flip phase, so
        // it visibly rotates instead of snapping between face-on and edge-on.
        float squash;
        int faceCol;
        if (spinning) {
            float phase = (local % 200) / 200f;       // 5 flips/sec
            squash = Math.abs((float) Math.cos(phase * Math.PI * 2));
            diameter = COIN_BIG;
            dx = cxScreen; dy = cyScreen;
            faceCol = GuideTheme.GOLD;
        } else {
            float raw = Math.min(1f, (local - SPIN_MS) / (float) LAND_MS);
            // Ease-out-back: the coin accelerates, overshoots its target slightly,
            // then settles - a satisfying "snap onto the item" instead of a linear glide.
            float k = RewardReveal.easeOutBack(raw);
            // Size eases linearly-ish so the overshoot doesn't make it grow past start.
            float kSize = raw * raw * (3 - 2 * raw);   // smoothstep for the shrink
            squash = 1f;
            diameter = Math.round(COIN_BIG - (COIN_BIG - 16) * kSize); // shrink to ~item size
            dx = Math.round(cxScreen + (targetX - cxScreen) * k);
            dy = Math.round(cyScreen + (targetY - cyScreen) * k);
            // Coin color by outcome: green (kept), orange (partial), red (fully lost).
            int oc = outcome(index);
            faceCol = oc == 0 ? 0xFF2E7D32 : (oc == 1 ? 0xFFE08000 : 0xFFB02020);
        }

        // Face glyph: "?" while spinning, then a gold star (kept), an orange
        // hyphen (partial loss), or a red X (fully lost).
        int oc = outcome(index);
        String face = spinning ? "?" : (oc == 0 ? "★" : (oc == 1 ? "-" : "✖"));
        int glyphCol = spinning ? 0xFF3B2B12
            : (oc == 0 ? 0xFFF7E27A : (oc == 1 ? 0xFFFFC04D : 0xFFFF4040));
        RewardReveal.drawCoin(ctx, this.textRenderer, dx, dy, diameter / 2,
            squash, faceCol, face, glyphCol);
    }

    /** Red X across a 16px cell at (ix,iy). */
    private static void drawX(DrawContext ctx, int ix, int iy, int size, int color) {
        for (int p = 0; p < size; p++) {
            ctx.fill(ix + p, iy + p, ix + p + 2, iy + p + 2, color);
            ctx.fill(ix + (size - 1 - p), iy + p, ix + (size - 1 - p) + 2, iy + p + 2, color);
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
