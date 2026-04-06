package com.crackedgames.craftics.client;

import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.network.RespecPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Respec screen: lets the player refund allocated stat points and reallocate them.
 * Each refunded point costs 1 XP level.
 */
public class RespecScreen extends Screen {

    private final int[] originalStatValues;
    private final int[] currentStatValues;
    private int originalUnspent;
    private int currentUnspent;
    private int playerXpLevels;

    private static final int CARD_WIDTH = 260;
    private static final int CARD_HEIGHT = 20;
    private static final int CARD_GAP = 2;
    private static final int BTN_SIZE = 20;

    private ButtonWidget confirmButton;

    public RespecScreen() {
        super(Text.literal("Respec Stats"));

        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        this.originalStatValues = new int[stats.length];
        this.currentStatValues = new int[stats.length];
        for (int i = 0; i < stats.length; i++) {
            int pts = CombatState.getStatPoints(i);
            originalStatValues[i] = pts;
            currentStatValues[i] = pts;
        }
        this.originalUnspent = CombatState.getUnspentPoints();
        this.currentUnspent = originalUnspent;
        this.playerXpLevels = -1; // set in init() from client player
    }

    @Override
    protected void init() {
        this.clearChildren();

        if (this.client != null && this.client.player != null) {
            this.playerXpLevels = this.client.player.experienceLevel;
        }

        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int centerX = this.width / 2;
        int totalHeight = stats.length * (CARD_HEIGHT + CARD_GAP);
        // Center the full content: header(32) + stats + gap(8) + buttons(20)
        int fullHeight = 32 + totalHeight + 8 + 20;
        int contentTop = (this.height - fullHeight) / 2;
        int startY = contentTop + 32;

        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);
            final int statIndex = i;

            // [-] refund button
            boolean canRefund = currentStatValues[i] > 0 && getTotalRefunded() < playerXpLevels;
            ButtonWidget minusBtn = ButtonWidget.builder(
                Text.literal("\u00a7c[-]"),
                button -> {
                    if (currentStatValues[statIndex] > 0 && getTotalRefunded() < playerXpLevels) {
                        currentStatValues[statIndex]--;
                        currentUnspent++;
                        init();
                    }
                }
            ).dimensions(centerX - CARD_WIDTH / 2, y, BTN_SIZE, CARD_HEIGHT).build();
            minusBtn.active = canRefund;
            this.addDrawableChild(minusBtn);

            // [+] allocate button
            boolean canAllocate = currentUnspent > 0;
            ButtonWidget plusBtn = ButtonWidget.builder(
                Text.literal("\u00a7a[+]"),
                button -> {
                    if (currentUnspent > 0) {
                        currentStatValues[statIndex]++;
                        currentUnspent--;
                        init();
                    }
                }
            ).dimensions(centerX + CARD_WIDTH / 2 - BTN_SIZE, y, BTN_SIZE, CARD_HEIGHT).build();
            plusBtn.active = canAllocate;
            this.addDrawableChild(plusBtn);
        }

        // Bottom row: Confirm, Reset, Cancel — all in one row
        int bottomY = startY + totalHeight + 8;
        int btnWidth = 80;
        int gap = 4;
        int totalBtnWidth = btnWidth * 2 + 50 + gap * 2; // confirm + cancel + reset + gaps
        int btnStartX = centerX - totalBtnWidth / 2;

        boolean canConfirm = hasAnyChanges();
        confirmButton = ButtonWidget.builder(
            Text.literal(canConfirm ? "\u00a7aConfirm" : "\u00a78Confirm"),
            button -> {
                if (hasAnyChanges()) {
                    StringBuilder sb = new StringBuilder();
                    PlayerProgression.Stat[] s = PlayerProgression.Stat.values();
                    for (int i = 0; i < s.length; i++) {
                        if (i > 0) sb.append(':');
                        sb.append(currentStatValues[i] - originalStatValues[i]);
                    }
                    ClientPlayNetworking.send(new RespecPayload(sb.toString()));
                    this.close();
                }
            }
        ).dimensions(btnStartX, bottomY, btnWidth, 20).build();
        confirmButton.active = canConfirm;
        this.addDrawableChild(confirmButton);

        ButtonWidget resetBtn = ButtonWidget.builder(
            Text.literal("\u00a7eReset"),
            button -> {
                PlayerProgression.Stat[] s = PlayerProgression.Stat.values();
                for (int i = 0; i < s.length; i++) {
                    currentStatValues[i] = originalStatValues[i];
                }
                currentUnspent = originalUnspent;
                init();
            }
        ).dimensions(btnStartX + btnWidth + gap, bottomY, 50, 20).build();
        this.addDrawableChild(resetBtn);

        ButtonWidget cancelBtn = ButtonWidget.builder(
            Text.literal("Cancel"),
            button -> this.close()
        ).dimensions(btnStartX + btnWidth + 50 + gap * 2, bottomY, btnWidth, 20).build();
        this.addDrawableChild(cancelBtn);
    }

    private int getTotalRefunded() {
        int total = 0;
        for (int i = 0; i < originalStatValues.length; i++) {
            int delta = originalStatValues[i] - currentStatValues[i];
            if (delta > 0) total += delta;
        }
        return total;
    }

    private boolean hasAnyChanges() {
        for (int i = 0; i < originalStatValues.length; i++) {
            if (currentStatValues[i] != originalStatValues[i]) return true;
        }
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xE0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int centerX = this.width / 2;
        int totalHeight = stats.length * (CARD_HEIGHT + CARD_GAP);
        int fullHeight = 32 + totalHeight + 8 + 20;
        int contentTop = (this.height - fullHeight) / 2;
        int startY = contentTop + 32;

        // Header
        int headerY = contentTop;
        context.drawCenteredTextWithShadow(this.textRenderer,
            "\u00a76\u00a7l\u2728 RESPEC STATS \u2728", centerX, headerY, 0xFFAA00);

        int totalRefunded = getTotalRefunded();
        String costText = totalRefunded > 0
            ? "\u00a7cCost: " + totalRefunded + " XP Level" + (totalRefunded != 1 ? "s" : "")
              + " \u00a77(You have \u00a7a" + playerXpLevels + "\u00a77)"
            : "\u00a77Refund points to reallocate them";
        context.drawCenteredTextWithShadow(this.textRenderer, costText, centerX, headerY + 11, 0xAAAAAA);

        String unspentText = currentUnspent > 0
            ? "\u00a7a" + currentUnspent + " unspent point" + (currentUnspent != 1 ? "s" : "")
            : "\u00a77No unspent points";
        context.drawCenteredTextWithShadow(this.textRenderer, unspentText, centerX, headerY + 22, 0xAAAAAA);

        // Stat rows: draw labels between the [-] and [+] buttons
        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int effective = stat.baseValue + currentStatValues[i];
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);
            int labelX = centerX - CARD_WIDTH / 2 + BTN_SIZE + 8;
            int labelY = y + (CARD_HEIGHT - 8) / 2;

            // Stat name + icon
            String label = stat.icon + " " + stat.displayName;
            context.drawTextWithShadow(this.textRenderer, label, labelX, labelY, 0xFFFFFF);

            // Effective value + delta indicator
            int statDelta = currentStatValues[i] - originalStatValues[i];
            String valueStr = "\u00a7f" + effective;
            if (statDelta > 0) {
                valueStr += " \u00a7a(+" + statDelta + ")";
            } else if (statDelta < 0) {
                valueStr += " \u00a7c(" + statDelta + ")";
            }
            int valueX = centerX + CARD_WIDTH / 2 - BTN_SIZE - 8 - this.textRenderer.getWidth(
                effective + (statDelta != 0 ? " (" + (statDelta > 0 ? "+" : "") + statDelta + ")" : ""));
            context.drawTextWithShadow(this.textRenderer, valueStr, valueX, labelY, 0xFFFFFF);

            // Hover description
            if (mouseX >= centerX - CARD_WIDTH / 2 && mouseX <= centerX + CARD_WIDTH / 2
                && mouseY >= y && mouseY <= y + CARD_HEIGHT) {
                context.drawCenteredTextWithShadow(this.textRenderer,
                    "\u00a77" + stat.description,
                    centerX, startY + stats.length * (CARD_HEIGHT + CARD_GAP) + 30, 0x888888);
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
