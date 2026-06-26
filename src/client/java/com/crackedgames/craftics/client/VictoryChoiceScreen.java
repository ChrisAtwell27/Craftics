package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.network.PostLevelChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(acceptLabel),
                btn -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        biomeName, "Entering...",
                        () -> ClientPlayNetworking.send(new PostLevelChoicePayload(false))
                    );
                }
            ).dimensions(centerX - btnW / 2, btnY, btnW, btnH).build());

            // Decline button (sends goHome=true to skip)
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(declineLabel),
                btn -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        "Onward!", "",
                        () -> ClientPlayNetworking.send(new PostLevelChoicePayload(true))
                    );
                }
            ).dimensions(centerX - btnW / 2, btnY + 25, btnW, btnH).build());

        } else if (isLeader) {
            // --- Normal victory choice (leader only) ---
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§a⌂ Go Home (Keep loot, reset biome progress)"),
                btn -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        "Returning to Hub", "Safe travels...",
                        () -> ClientPlayNetworking.send(new PostLevelChoicePayload(true))
                    );
                }
            ).dimensions(centerX - btnW / 2, btnY, btnW, btnH).build());

            int nextLevel = levelIndex + 1; // payload is already next level index (0-based)
            if (nextIsBoss) {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("§4§l☠ BOSS FIGHT: " + biomeName + " ☠ (Risk it all!)"),
                    btn -> {
                        this.close();
                        TransitionOverlay.startTransition(
                            "§4§l☠ BOSS FIGHT ☠",
                            biomeName + " — Prepare yourself...",
                            () -> ClientPlayNetworking.send(new PostLevelChoicePayload(false))
                        );
                    }
                ).dimensions(centerX - btnW / 2, btnY + 25, btnW, btnH).build());
            } else {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("§c⚔ Continue to Level " + nextLevel + " (Risk it all!)"),
                    btn -> {
                        this.close();
                        TransitionOverlay.startTransition(
                            biomeName + " — Level " + nextLevel, "Onward!",
                            () -> ClientPlayNetworking.send(new PostLevelChoicePayload(false))
                        );
                    }
                ).dimensions(centerX - btnW / 2, btnY + 25, btnW, btnH).build());
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

    /** Draw the reward grid (item icon + count) wrapping inside [x, x+w). Returns
     *  the Y just below the grid. Hovering a cell draws its tooltip. */
    private int drawRewardGrid(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY) {
        if (rewards.isEmpty()) {
            centered(ctx, "No items collected", x + w / 2, y, GuideTheme.INK_SOFT);
            return y + 12;
        }
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
            ctx.drawItem(stack, ix, iy);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                hovered = stack; hx = mouseX; hy = mouseY;
            }
        }
        // Stack counts in a SEPARATE pass pushed above the item-icon Z layer, so a
        // neighbouring icon can never draw over a count (no dark backing needed once
        // the digits sit on top). Vanilla-style white digits with a shadow.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 250);
        for (int i = 0; i < rewards.size(); i++) {
            ItemStack stack = rewards.get(i);
            if (stack.getCount() <= 1) continue;
            int ix = startX + (i % perRow) * CELL + 2;
            int iy = y + (i / perRow) * CELL + 2;
            net.minecraft.text.Text c = Text.literal(String.valueOf(stack.getCount()));
            int cwid = this.textRenderer.getWidth(c);
            ctx.drawTextWithShadow(this.textRenderer, c, ix + 16 - cwid, iy + 9, 0xFFFFFFFF);
        }
        ctx.getMatrices().pop();
        if (hovered != null) {
            java.util.List<net.minecraft.text.Text> lines =
                net.minecraft.client.gui.screen.Screen.getTooltipFromItem(
                    MinecraftClient.getInstance(), hovered);
            ctx.drawTooltip(this.textRenderer, lines, hx, hy);
        }
        return y + rows * CELL + 2;
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
        super.render(context, mouseX, mouseY, delta);
        int panelH = panelHeight();
        int x = (this.width - PANEL_W) / 2;
        int y = panelTop();
        GuideTheme.drawPanel(context, x, y, PANEL_W, panelH);
        int cx = this.width / 2;
        if (isEventPrompt) {
            renderEventPrompt(context, cx, y + 12, PANEL_W, mouseX, mouseY);
        } else {
            renderVictoryScreen(context, cx, x, y + 10, PANEL_W, mouseX, mouseY);
        }
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
        centered(context, "★ VICTORY!", cx, y, GuideTheme.GOLD);
        y += 13;
        centered(context, biomeName + " — Level " + (levelIndex + 1) + " Complete",
            cx, y, GuideTheme.INK);
        y += 12;
        GuideTheme.drawRule(context, panelX + 12, y, panelW - 24);
        y += 6;
        centered(context, "Total: " + totalEmeralds + " Emeralds", cx, y, GuideTheme.INK_SOFT);
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
