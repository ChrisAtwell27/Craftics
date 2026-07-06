package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.network.PostLevelChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Shown after winning a non-boss level.
 * Player chooses: Go Home (safe, reset biome) or Continue (risky, next level).
 * Also used for trial chamber / random event prompts (levelIndex == -1).
 */
public class VictoryChoiceScreen extends Screen {

    private static final int CELL = 20;   // reward grid cell size (16px icon + 4px gap)
    private static final int PANEL_W = 260;

    private final int emeraldsEarned;
    private final int totalEmeralds;
    private final String biomeName;
    private final int levelIndex;
    private final boolean nextIsBoss;
    private final boolean isLeader;
    private final java.util.List<net.minecraft.item.ItemStack> rewards;

    /** True when this screen is prompting for a trial/event, not a regular level victory. */
    private final boolean isEventPrompt;

    // ── Reward reveal animation (shares the Game Over sequence's feel) ──
    private static final long REVEAL_STAGGER_MS = 110; // gap between successive item drops
    private static final long REVEAL_DROP_MS    = 300; // per-item fall + settle
    private long startMs = -1;                          // reveal clock; starts once the fade clears
    private final boolean[] revealPopPlayed;            // per reward: landing chime fired
    private boolean openStingPlayed = false;
    private boolean doneStingPlayed = false;

    public VictoryChoiceScreen(int emeraldsEarned, int totalEmeralds,
                                String biomeName, int levelIndex, boolean nextIsBoss,
                                boolean isLeader, java.util.List<net.minecraft.item.ItemStack> rewards) {
        super(Text.literal("Victory!"));
        this.emeraldsEarned = emeraldsEarned;
        this.totalEmeralds = totalEmeralds;
        this.biomeName = biomeName;
        this.levelIndex = levelIndex;
        this.nextIsBoss = nextIsBoss;
        this.isEventPrompt = (levelIndex == -1);
        this.isLeader = isLeader;
        this.rewards = rewards != null ? rewards : new java.util.ArrayList<>();
        this.revealPopPlayed = new boolean[this.rewards.size()];
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int btnW = 300;
        int btnH = 20;
        // Buttons sit just below the content-fit parchment panel.
        int btnY = panelTop() + panelHeight() + 6;

        if (isEventPrompt) {
            // --- Event prompt (Trial Chamber, Treasure Vault, Ominous Trial, addon events) ---
            boolean isTreasure = biomeName.contains("Treasure");
            boolean isOminous = biomeName.contains("Ominous");
            boolean isTrial = biomeName.contains("Trial");

            String acceptLabel;
            String declineLabel;

            if (isTreasure) {
                acceptLabel = "§a✦ Enter the Vault (Free loot!)";
                declineLabel = "§7✗ Skip and continue";
            } else if (isOminous) {
                acceptLabel = "§c⚔ Accept the Ominous Trial (Legendary loot!)";
                declineLabel = "§7✗ Not worth the risk...";
            } else if (isTrial) {
                acceptLabel = "§6⚔ Enter the Trial Chamber (Rare loot!)";
                declineLabel = "§7✗ Pass and continue";
            } else {
                acceptLabel = "§6⚔ Explore " + biomeName;
                declineLabel = "§7✗ Pass and continue";
            }

            // Accept button (sends goHome=false to enter trial)
            this.addDrawableChild(GuideButton.of(
                centerX - btnW / 2, btnY, btnW, btnH,
                Text.literal(acceptLabel),
                btn -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        biomeName, "Entering...",
                        () -> ClientPlayNetworking.send(new PostLevelChoicePayload(false))
                    );
                }
            ));

            // Decline button (sends goHome=true to skip)
            this.addDrawableChild(GuideButton.of(
                centerX - btnW / 2, btnY + 25, btnW, btnH,
                Text.literal(declineLabel),
                btn -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        "Onward!", "",
                        () -> ClientPlayNetworking.send(new PostLevelChoicePayload(true))
                    );
                }
            ));

        } else if (isLeader) {
            // --- Normal victory choice (leader only) ---
            this.addDrawableChild(GuideButton.of(
                centerX - btnW / 2, btnY, btnW, btnH,
                Text.literal("§a⌂ Go Home (Keep loot, reset biome progress)"),
                btn -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        "Returning to Hub", "Safe travels...",
                        () -> ClientPlayNetworking.send(new PostLevelChoicePayload(true))
                    );
                }
            ));

            int nextLevel = levelIndex + 1; // payload is already next level index (0-based)
            if (nextIsBoss) {
                this.addDrawableChild(GuideButton.of(
                    centerX - btnW / 2, btnY + 25, btnW, btnH,
                    Text.literal("§4§l☠ BOSS FIGHT: " + biomeName + " ☠ (Risk it all!)"),
                    btn -> {
                        this.close();
                        TransitionOverlay.startTransition(
                            "§4§l☠ BOSS FIGHT ☠",
                            biomeName + " — Prepare yourself...",
                            () -> ClientPlayNetworking.send(new PostLevelChoicePayload(false))
                        );
                    }
                ));
            } else {
                this.addDrawableChild(GuideButton.of(
                    centerX - btnW / 2, btnY + 25, btnW, btnH,
                    Text.literal("§c⚔ Continue to Level " + nextLevel + " (Risk it all!)"),
                    btn -> {
                        this.close();
                        TransitionOverlay.startTransition(
                            biomeName + " — Level " + nextLevel, "Onward!",
                            () -> ClientPlayNetworking.send(new PostLevelChoicePayload(false))
                        );
                    }
                ));
            }
        }
    }

    /** No-shadow centered text helper (the codebase has no drawCenteredText; this
     *  measures width and draws with shadow disabled). */
    private void centered(DrawContext ctx, String text, int cx, int y, int color) {
        net.minecraft.text.Text t = Text.literal(text);
        int w = this.textRenderer.getWidth(t);
        ctx.drawText(this.textRenderer, t, cx - w / 2, y, color, false);
    }

    /** Draw the reward grid with a staggered drop-in reveal (mirrors the Game Over
     *  sequence's feel): each reward falls into its cell with an ease-out-back settle,
     *  a rarity-tinted landing pop, and a per-item chime. Counts and tooltips appear
     *  only once an item has landed. Returns the Y just below the grid. */
    private int drawRewardGrid(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY) {
        if (rewards.isEmpty()) {
            centered(ctx, "No items collected", x + w / 2, y, GuideTheme.INK_SOFT);
            return y + 12;
        }
        long t = elapsed();
        int perRow = Math.max(1, w / CELL);
        int rows = (rewards.size() + perRow - 1) / perRow;
        int gridW = Math.min(rewards.size(), perRow) * CELL;
        int startX = x + (w - gridW) / 2; // center the grid

        ItemStack hovered = null;
        int hx = 0, hy = 0;
        for (int i = 0; i < rewards.size(); i++) {
            ItemStack stack = rewards.get(i);
            // 16px icon centered in the 20px cell -> 2px gap on every side.
            int ix = startX + (i % perRow) * CELL + 2;
            int iy = y + (i / perRow) * CELL + 2;

            long launch = (long) i * REVEAL_STAGGER_MS;
            if (t < launch) {
                // Not dropped yet: a faint empty cell keeps the grid footprint stable.
                ctx.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0x22000000);
                continue;
            }
            float p = Math.min(1f, (t - launch) / (float) REVEAL_DROP_MS);
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

            // Landing chime (once) + rarity-tinted pop ring as the item settles.
            if (p >= 1f && !revealPopPlayed[i]) {
                revealPopPlayed[i] = true;
                RewardReveal.playMaster(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    0.5f, rarityPitch(stack));
            }
            float popP = (t - (launch + REVEAL_DROP_MS)) / 200f;
            RewardReveal.drawPopRing(ctx, ix - 2, iy - 2, ix + 18, iy + 18, popP, rarityColor(stack));

            if (p >= 1f && mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                hovered = stack; hx = mouseX; hy = mouseY;
            }
        }
        // Stack counts in a SEPARATE pass pushed above the item-icon Z layer, so a
        // neighbouring icon can never draw over a count. Only for settled items.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 250);
        for (int i = 0; i < rewards.size(); i++) {
            ItemStack stack = rewards.get(i);
            if (stack.getCount() <= 1) continue;
            if (t < (long) i * REVEAL_STAGGER_MS + REVEAL_DROP_MS) continue; // not landed yet
            int ix = startX + (i % perRow) * CELL + 2;
            int iy = y + (i / perRow) * CELL + 2;
            Text c = Text.literal(String.valueOf(stack.getCount()));
            int cwid = this.textRenderer.getWidth(c);
            ctx.drawTextWithShadow(this.textRenderer, c, ix + 16 - cwid, iy + 9, 0xFFFFFFFF);
        }
        ctx.getMatrices().pop();
        if (hovered != null) {
            java.util.List<Text> lines =
                Screen.getTooltipFromItem(MinecraftClient.getInstance(), hovered);
            ctx.drawTooltip(this.textRenderer, lines, hx, hy);
        }
        return y + rows * CELL + 2;
    }

    // ── Reward-reveal timing / helpers ──────────────────────────────────────

    private long elapsed() {
        return startMs < 0 ? 0L : System.currentTimeMillis() - startMs;
    }

    /** Total reveal length: last item's launch + drop + a short pop tail. */
    private long revealDurationMs() {
        if (rewards.isEmpty()) return 0L;
        return (long) (rewards.size() - 1) * REVEAL_STAGGER_MS + REVEAL_DROP_MS + 200L;
    }

    private boolean revealComplete() {
        return isEventPrompt || elapsed() >= revealDurationMs();
    }

    /** Emerald total eased from the pre-victory amount up to the new total over the reveal. */
    private int shownEmeraldTotal() {
        if (rewards.isEmpty() || revealDurationMs() <= 0) return totalEmeralds;
        float op = Math.min(1f, elapsed() / (float) revealDurationMs());
        int start = Math.max(0, totalEmeralds - emeraldsEarned);
        return start + Math.round((totalEmeralds - start) * op);
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

    /** Snap the reward reveal to its end, muting the remaining per-item chimes.
     *  The completion bell + spark shower fire from render()'s completion block
     *  next frame (doneStingPlayed stays false), so skippers get the finale too. */
    private void skipReveal() {
        startMs = System.currentTimeMillis() - revealDurationMs() - 1L;
        for (int i = 0; i < revealPopPlayed.length; i++) revealPopPlayed[i] = true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Let the choice buttons handle the click first; a click on empty space during
        // the reveal fast-forwards it (like the dialogue typewriter).
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!isEventPrompt && button == 0 && startMs >= 0 && !revealComplete()) {
            skipReveal();
            return true;
        }
        return false;
    }

    /** Number of grid rows the rewards occupy at the current panel width. */
    private int rewardRows() {
        if (rewards.isEmpty()) return 1;
        int perRow = Math.max(1, (PANEL_W - 24) / CELL);
        return (rewards.size() + perRow - 1) / perRow;
    }

    /** Content-fit panel height so there's no large empty area below the content. */
    private int panelHeight() {
        if (isEventPrompt) return 92;
        // inset(10) + header(13) + biome line(12) + rule(8) + total(14)
        // + grid rows + gap(6) + warning lines + bottom inset(10).
        int warnings = nextIsBoss ? 22 : 11;
        if (!isLeader) warnings += 14;
        return 10 + 13 + 12 + 8 + 14 + rewardRows() * CELL + 6 + warnings + 10;
    }

    /** Top Y of the panel, centered over the inventory area, a little above the buttons. */
    private int panelTop() {
        return (this.height - panelHeight()) / 2 - 10;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Hold the reward reveal until the level-transition fade has fully cleared -
        // the screen opens behind a fade-to-black, and starting the staggered drop-in
        // immediately would play the whole thing under the black overlay, unseen.
        if (startMs < 0 && !TransitionOverlay.isActive()) {
            startMs = System.currentTimeMillis();
            if (!isEventPrompt && !openStingPlayed) {
                openStingPlayed = true;
                RewardReveal.playMaster(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 0.8f);
            }
        }

        super.render(context, mouseX, mouseY, delta);
        int panelH = panelHeight();
        int x = (this.width - PANEL_W) / 2;
        int y = panelTop();
        int cx = this.width / 2;
        // Panel curtain-up: a subtle scale-settle once the transition fade clears.
        float in = startMs < 0 ? 0f
            : RewardReveal.smoothstep(Math.min(1f, elapsed() / 220f));
        RewardReveal.pushScaledAround(context, 0.93f + 0.07f * RewardReveal.easeOutBack(in),
            cx, y + panelH / 2f);
        GuideTheme.drawPanel(context, x, y, PANEL_W, panelH);
        context.getMatrices().pop();
        if (isEventPrompt) {
            renderEventPrompt(context, cx, y + 12, PANEL_W, mouseX, mouseY);
        } else {
            renderVictoryScreen(context, cx, x, y + 10, PANEL_W, mouseX, mouseY);
        }

        // Completion sting + a shower of gold sparks once every reward settled.
        if (!isEventPrompt && !rewards.isEmpty() && !doneStingPlayed
                && startMs >= 0 && elapsed() >= revealDurationMs()) {
            doneStingPlayed = true;
            RewardReveal.playMaster(net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, 0.5f, 1.2f);
            RewardReveal.burst(cx, y + 6, 30, 0xF7C84A, 95f);
        }
        // Spark layer above the panel content.
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 350);
        RewardReveal.tickAndDrawParticles(context);
        context.getMatrices().pop();
    }

    @Override
    public void removed() {
        super.removed();
        RewardReveal.clearParticles();
    }

    private void renderEventPrompt(DrawContext context, int cx, int topY,
                                   int panelW, int mouseX, int mouseY) {
        boolean isTreasure = biomeName.contains("Treasure");
        boolean isOminous = biomeName.contains("Ominous");
        boolean isTrial = biomeName.contains("Trial");

        if (isTreasure) {
            // Treasure Vault prompt
            centered(context, "✦ TREASURE VAULT ✦", cx, topY, GuideTheme.GOLD);
            centered(context, "A hidden vault filled with riches!", cx, topY + 16, GuideTheme.INK);
            centered(context, "No enemies inside — just free loot.", cx, topY + 32, GuideTheme.INK_SOFT);
        } else if (isOminous) {
            // Ominous Trial prompt
            centered(context, "⚔ OMINOUS TRIAL CHAMBER ⚔", cx, topY, GuideTheme.GOLD);
            centered(context, "A dark and powerful trial awaits...", cx, topY + 16, GuideTheme.INK);
            centered(context, "⚠ WARNING: Contains a Warden!", cx, topY + 32, 0xFFB02020);
            centered(context, "Rewards: Legendary-tier loot", cx, topY + 48, GuideTheme.INK);
        } else if (isTrial) {
            // Standard Trial Chamber prompt
            centered(context, "⚔ TRIAL CHAMBER DISCOVERED ⚔", cx, topY, GuideTheme.GOLD);
            centered(context, "A mysterious trial awaits...", cx, topY + 16, GuideTheme.INK);
            centered(context, "Defeat trial mobs for rare loot!", cx, topY + 32, GuideTheme.INK_SOFT);
            centered(context, "Enemies are tougher than normal.", cx, topY + 48, 0xFFB02020);
        } else {
            // Generic addon event prompt - use the event's display name
            centered(context, "✦ " + biomeName.toUpperCase() + " ✦", cx, topY, GuideTheme.GOLD);
            centered(context, "A rare discovery!", cx, topY + 16, GuideTheme.INK);
            centered(context, "Will you explore it?", cx, topY + 32, GuideTheme.INK_SOFT);
        }
    }

    private void renderVictoryScreen(DrawContext context, int cx, int panelX, int topY,
                                     int panelW, int mouseX, int mouseY) {
        int y = topY;
        // Title carries the win: larger, with a slow gold shimmer.
        int shimmer = GuideTheme.brighten(GuideTheme.GOLD,
            Math.round(28 * RewardReveal.pulse(1800)));
        RewardReveal.drawCenteredScaled(context, this.textRenderer, "★ VICTORY! ★",
            cx, y + 4, 1.35f, shimmer, false);
        y += 15;
        centered(context, biomeName + " — Level " + (levelIndex + 1) + " Complete",
            cx, y, GuideTheme.INK);
        y += 12;
        GuideTheme.drawRule(context, panelX + 12, y, panelW - 24);
        y += 6;
        centered(context, "Total: " + shownEmeraldTotal() + " Emeralds", cx, y, GuideTheme.INK_SOFT);
        // While the total is still counting up, a green "+N" rides beside it so
        // the gain itself is legible, not just the final number.
        if (emeraldsEarned > 0 && startMs >= 0 && shownEmeraldTotal() < totalEmeralds) {
            String gain = "+" + emeraldsEarned;
            int totW = this.textRenderer.getWidth("Total: " + shownEmeraldTotal() + " Emeralds");
            context.drawText(this.textRenderer, Text.literal(gain),
                cx + totW / 2 + 6, y, 0xFF2E7D32, false);
        }
        y += 14;
        y = drawRewardGrid(context, panelX + 12, y, panelW - 24, mouseX, mouseY);
        y += 4;
        if (nextIsBoss) {
            centered(context, "☠ NEXT LEVEL IS A BOSS FIGHT", cx, y, 0xFFB02020);
            y += 11;
        }
        centered(context, "If you die, you risk losing items!", cx, y, 0xFFB02020);
        if (!isLeader) {
            centered(context, "Waiting for the leader to choose...",
                cx, y + 14, GuideTheme.INK_SOFT);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Must choose
    }
}
