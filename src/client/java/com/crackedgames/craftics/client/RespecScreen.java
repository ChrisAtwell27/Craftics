package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
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

    // Panel layout constants
    private static final int PANEL_INSET = 8;   // padding inside the panel
    private static final int HEADER_H    = 36;  // 3 lines of header text
    private static final int FOOTER_H    = 20;  // bottom buttons row

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

    // -------------------------------------------------------------------------
    // Panel geometry helpers (mirroring VictoryChoiceScreen pattern)
    // -------------------------------------------------------------------------

    private int panelWidth() {
        return CARD_WIDTH + 20; // 10px padding each side for stepper buttons
    }

    private int panelHeight() {
        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int totalHeight = stats.length * (CARD_HEIGHT + CARD_GAP);
        // inset + header + stat rows + gap + buttons + inset
        return PANEL_INSET + HEADER_H + totalHeight + 8 + FOOTER_H + PANEL_INSET;
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (this.height - panelHeight()) / 2;
    }

    @Override
    protected void init() {
        this.clearChildren();

        if (this.client != null && this.client.player != null) {
            this.playerXpLevels = this.client.player.experienceLevel;
        }

        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int centerX = this.width / 2;
        int panelT   = panelTop();
        int startY   = panelT + PANEL_INSET + HEADER_H;

        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);
            final int statIndex = i;

            // [-] refund button
            boolean canRefund = currentStatValues[i] > 0 && getTotalRefunded() < playerXpLevels;
            GuideButton minusBtn = GuideButton.of(
                centerX - CARD_WIDTH / 2, y, BTN_SIZE, CARD_HEIGHT,
                Text.literal("[-]"),
                button -> {
                    if (currentStatValues[statIndex] > 0 && getTotalRefunded() < playerXpLevels) {
                        currentStatValues[statIndex]--;
                        currentUnspent++;
                        init();
                    }
                }
            );
            minusBtn.active = canRefund;
            this.addDrawableChild(minusBtn);

            // [+] allocate button
            boolean canAllocate = currentUnspent > 0;
            GuideButton plusBtn = GuideButton.of(
                centerX + CARD_WIDTH / 2 - BTN_SIZE, y, BTN_SIZE, CARD_HEIGHT,
                Text.literal("[+]"),
                button -> {
                    if (currentUnspent > 0) {
                        currentStatValues[statIndex]++;
                        currentUnspent--;
                        init();
                    }
                }
            );
            plusBtn.active = canAllocate;
            this.addDrawableChild(plusBtn);
        }

        // Bottom row: Confirm, Reset, Cancel
        int totalHeight = stats.length * (CARD_HEIGHT + CARD_GAP);
        int bottomY = panelT + PANEL_INSET + HEADER_H + totalHeight + 8;
        int btnWidth = 80;
        int gap = 4;
        int totalBtnWidth = btnWidth * 2 + 50 + gap * 2;
        int btnStartX = centerX - totalBtnWidth / 2;

        boolean canConfirm = hasAnyChanges();
        confirmButton = GuideButton.of(
            btnStartX, bottomY, btnWidth, 20,
            Text.literal("Confirm"),
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
        );
        confirmButton.active = canConfirm;
        this.addDrawableChild(confirmButton);

        GuideButton resetBtn = GuideButton.of(
            btnStartX + btnWidth + gap, bottomY, 50, 20,
            Text.literal("Reset"),
            button -> {
                PlayerProgression.Stat[] s = PlayerProgression.Stat.values();
                for (int i = 0; i < s.length; i++) {
                    currentStatValues[i] = originalStatValues[i];
                }
                currentUnspent = originalUnspent;
                init();
            }
        );
        this.addDrawableChild(resetBtn);

        GuideButton cancelBtn = GuideButton.of(
            btnStartX + btnWidth + 50 + gap * 2, bottomY, btnWidth, 20,
            Text.literal("Cancel"),
            button -> this.close()
        );
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
        // Dim the world behind the panel (same as GameOverScreen/VictoryChoiceScreen)
        context.fill(0, 0, this.width, this.height, 0xE0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int centerX = this.width / 2;
        int panelL  = panelLeft();
        int panelT  = panelTop();
        int panelW  = panelWidth();
        int panelH  = panelHeight();

        // Parchment panel
        GuideTheme.drawPanel(context, panelL, panelT, panelW, panelH);

        int contentTop = panelT + PANEL_INSET;

        // --- Header ---
        GuideTheme.drawCentered(context, this.textRenderer,
            "RESPEC STATS", centerX, contentTop, GuideTheme.GOLD);

        int totalRefunded = getTotalRefunded();
        String costText = totalRefunded > 0
            ? "Cost: " + totalRefunded + " XP Level" + (totalRefunded != 1 ? "s" : "")
              + " (You have " + playerXpLevels + ")"
            : "Refund points to reallocate them";
        int costColor = totalRefunded > 0 ? 0xFFB02020 : GuideTheme.INK_SOFT;
        GuideTheme.drawCentered(context, this.textRenderer, costText, centerX, contentTop + 12, costColor);

        String unspentText = currentUnspent > 0
            ? currentUnspent + " unspent point" + (currentUnspent != 1 ? "s" : "")
            : "No unspent points";
        int unspentColor = currentUnspent > 0 ? GuideTheme.INK : GuideTheme.INK_FAINT;
        GuideTheme.drawCentered(context, this.textRenderer, unspentText, centerX, contentTop + 24, unspentColor);

        // --- Stat rows ---
        int startY = panelT + PANEL_INSET + HEADER_H;
        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int effective = stat.baseValue + currentStatValues[i];
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);
            int labelX = centerX - CARD_WIDTH / 2 + BTN_SIZE + 8;
            int labelY = y + (CARD_HEIGHT - 8) / 2;

            // Stat name + icon
            String label = stat.icon + " " + stat.displayName;
            GuideTheme.drawInk(context, this.textRenderer, label, labelX, labelY, GuideTheme.INK);

            // Effective value + delta indicator
            int statDelta = currentStatValues[i] - originalStatValues[i];
            String baseVal = String.valueOf(effective);
            String deltaStr = statDelta != 0
                ? " (" + (statDelta > 0 ? "+" : "") + statDelta + ")"
                : "";
            int valueX = centerX + CARD_WIDTH / 2 - BTN_SIZE - 8
                - this.textRenderer.getWidth(baseVal + deltaStr);
            GuideTheme.drawInk(context, this.textRenderer, baseVal, valueX, labelY, GuideTheme.INK);
            if (statDelta != 0) {
                int dxOff = this.textRenderer.getWidth(baseVal);
                int deltaColor = statDelta > 0 ? 0xFF2E7B2E : 0xFFB02020;
                GuideTheme.drawInk(context, this.textRenderer, deltaStr,
                    valueX + dxOff, labelY, deltaColor);
            }

            // Hover description
            if (mouseX >= centerX - CARD_WIDTH / 2 && mouseX <= centerX + CARD_WIDTH / 2
                && mouseY >= y && mouseY <= y + CARD_HEIGHT) {
                int descY = startY + stats.length * (CARD_HEIGHT + CARD_GAP) + 30;
                GuideTheme.drawCentered(context, this.textRenderer,
                    stat.description, centerX, descY, GuideTheme.INK_FAINT);
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
