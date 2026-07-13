package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.network.AffinityRespecPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Affinity respec screen: lets the player spend unspent affinity points and
 * refund/reallocate already-allocated ones. The unspent pool is derived from the
 * player's level (one affinity per odd level above 1), so points earned from
 * force-given levels or older saves show up here too. Each refunded point costs
 * 1 XP level, the same rate as the stat respec.
 */
public class AffinityRespecScreen extends Screen {

    private final int[] originalValues;
    private final int[] currentValues;
    private final int originalUnspent;
    private int currentUnspent;
    private int playerXpLevels;

    private static final int CARD_WIDTH = 260;
    private static final int CARD_HEIGHT = 20;
    private static final int CARD_GAP = 2;
    private static final int BTN_SIZE = 20;

    // Panel layout constants
    private static final int PANEL_INSET = 8;
    private static final int HEADER_H    = 36;
    private static final int FOOTER_H    = 20;

    // Text colors. GuideTheme's INK shades are dark brown - they read correctly on the
    // guide book's light parchment, but this screen draws over a DARK overlay
    // (0xE0101010), where brown-on-dark is unreadable. Use light text instead.
    private static final int TEXT        = 0xFFFFFFFF; // primary (labels, values)
    private static final int TEXT_SOFT   = 0xFFC8C8C8; // secondary (subtitle)
    private static final int TEXT_FAINT  = 0xFF9A9A9A; // tertiary (hints, descriptions)

    private ButtonWidget confirmButton;

    public AffinityRespecScreen() {
        super(Text.literal("Respec Affinities"));

        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        this.originalValues = new int[affinities.length];
        this.currentValues = new int[affinities.length];
        int allocated = 0;
        for (int i = 0; i < affinities.length; i++) {
            int pts = CombatState.getAffinityPoints(i);
            originalValues[i] = pts;
            currentValues[i] = pts;
            allocated += pts;
        }
        // Affinity points owed by level (odd levels 3, 5, 7, ...) minus what's
        // already allocated: the pool the player can still spend.
        int expected = Math.max(0, (CombatState.getPlayerLevel() - 1) / 2);
        this.originalUnspent = Math.max(0, expected - allocated);
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
        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int totalHeight = affinities.length * (CARD_HEIGHT + CARD_GAP);
        // inset + header + rows + gap + buttons + inset
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

        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int centerX = this.width / 2;
        int panelT  = panelTop();
        int startY  = panelT + PANEL_INSET + HEADER_H;

        for (int i = 0; i < affinities.length; i++) {
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);
            final int idx = i;

            // [-] refund button: refunds cost 1 XP level each
            boolean canRefund = currentValues[i] > 0 && getTotalRefunded() < playerXpLevels;
            GuideButton minusBtn = GuideButton.of(
                centerX - CARD_WIDTH / 2, y, BTN_SIZE, CARD_HEIGHT,
                Text.literal("[-]"),
                button -> {
                    if (currentValues[idx] > 0 && getTotalRefunded() < playerXpLevels) {
                        currentValues[idx]--;
                        currentUnspent++;
                        init();
                    }
                }
            );
            minusBtn.active = canRefund;
            this.addDrawableChild(minusBtn);

            // [+] allocate button: draws from the unspent pool
            boolean canAllocate = currentUnspent > 0;
            GuideButton plusBtn = GuideButton.of(
                centerX + CARD_WIDTH / 2 - BTN_SIZE, y, BTN_SIZE, CARD_HEIGHT,
                Text.literal("[+]"),
                button -> {
                    if (currentUnspent > 0) {
                        currentValues[idx]++;
                        currentUnspent--;
                        init();
                    }
                }
            );
            plusBtn.active = canAllocate;
            this.addDrawableChild(plusBtn);
        }

        // Bottom row: Confirm, Reset, Cancel
        int totalHeight = affinities.length * (CARD_HEIGHT + CARD_GAP);
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
                    PlayerProgression.Affinity[] a = PlayerProgression.Affinity.values();
                    for (int i = 0; i < a.length; i++) {
                        if (i > 0) sb.append(':');
                        sb.append(currentValues[i] - originalValues[i]);
                    }
                    ClientPlayNetworking.send(new AffinityRespecPayload(sb.toString()));
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
                for (int i = 0; i < originalValues.length; i++) {
                    currentValues[i] = originalValues[i];
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
        for (int i = 0; i < originalValues.length; i++) {
            int delta = originalValues[i] - currentValues[i];
            if (delta > 0) total += delta;
        }
        return total;
    }

    private boolean hasAnyChanges() {
        for (int i = 0; i < originalValues.length; i++) {
            if (currentValues[i] != originalValues[i]) return true;
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
        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int centerX = this.width / 2;
        int panelL  = panelLeft();
        int panelT  = panelTop();
        int panelW  = panelWidth();
        int panelH  = panelHeight();

        // Parchment panel drawn BEFORE super.render so buttons appear on top
        GuideTheme.drawPanel(context, panelL, panelT, panelW, panelH);

        super.render(context, mouseX, mouseY, delta);

        int contentTop = panelT + PANEL_INSET;

        // --- Header ---
        GuideTheme.drawCentered(context, this.textRenderer,
            "RESPEC AFFINITIES", centerX, contentTop, GuideTheme.GOLD);

        int totalRefunded = getTotalRefunded();
        String costText = totalRefunded > 0
            ? "Cost: " + totalRefunded + " XP Level" + (totalRefunded != 1 ? "s" : "")
              + " (You have " + playerXpLevels + ")"
            : "Spend unspent points, or refund to reallocate";
        int costColor = totalRefunded > 0 ? 0xFFFF6B6B : TEXT_SOFT;
        GuideTheme.drawCentered(context, this.textRenderer, costText, centerX, contentTop + 12, costColor);

        String unspentText = currentUnspent > 0
            ? currentUnspent + " affinity point" + (currentUnspent != 1 ? "s" : "") + " to spend"
            : "No unspent affinity points";
        int unspentColor = currentUnspent > 0 ? TEXT : TEXT_FAINT;
        GuideTheme.drawCentered(context, this.textRenderer, unspentText, centerX, contentTop + 24, unspentColor);

        // --- Affinity rows ---
        int startY = panelT + PANEL_INSET + HEADER_H;
        for (int i = 0; i < affinities.length; i++) {
            PlayerProgression.Affinity affinity = affinities[i];
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);
            int labelX = centerX - CARD_WIDTH / 2 + BTN_SIZE + 8;
            int labelY = y + (CARD_HEIGHT - 8) / 2;

            // Affinity name + icon
            String label = affinity.icon + " " + affinity.displayName;
            GuideTheme.drawInk(context, this.textRenderer, label, labelX, labelY, TEXT);

            // Current value + delta indicator
            int affDelta = currentValues[i] - originalValues[i];
            String baseVal = String.valueOf(currentValues[i]);
            String deltaStr = affDelta != 0
                ? " (" + (affDelta > 0 ? "+" : "") + affDelta + ")"
                : "";
            int valueX = centerX + CARD_WIDTH / 2 - BTN_SIZE - 8
                - this.textRenderer.getWidth(baseVal + deltaStr);
            GuideTheme.drawInk(context, this.textRenderer, baseVal, valueX, labelY, TEXT);
            if (affDelta != 0) {
                int dxOff = this.textRenderer.getWidth(baseVal);
                // Brighter green/red so the delta reads on the dark overlay too.
                int deltaColor = affDelta > 0 ? 0xFF5CD65C : 0xFFFF6B6B;
                GuideTheme.drawInk(context, this.textRenderer, deltaStr,
                    valueX + dxOff, labelY, deltaColor);
            }

            // Hover description: drawn just below the last affinity row, above the footer buttons
            if (mouseX >= centerX - CARD_WIDTH / 2 && mouseX <= centerX + CARD_WIDTH / 2
                && mouseY >= y && mouseY <= y + CARD_HEIGHT) {
                // bottomY (button row) = startY + affinities.length*(CARD_HEIGHT+CARD_GAP) + 8
                // place description 2px below the last row, which is 6px above the button row
                int descY = startY + affinities.length * (CARD_HEIGHT + CARD_GAP) - 2;
                GuideTheme.drawCentered(context, this.textRenderer,
                    affinity.description, centerX, descY, TEXT_FAINT);
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
