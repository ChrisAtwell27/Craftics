package com.crackedgames.craftics.client;

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
 * 1 XP level — the same rate as the stat respec.
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
        // already allocated — the pool the player can still spend.
        int expected = Math.max(0, (CombatState.getPlayerLevel() - 1) / 2);
        this.originalUnspent = Math.max(0, expected - allocated);
        this.currentUnspent = originalUnspent;
        this.playerXpLevels = -1; // set in init() from client player
    }

    @Override
    protected void init() {
        this.clearChildren();

        if (this.client != null && this.client.player != null) {
            this.playerXpLevels = this.client.player.experienceLevel;
        }

        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int centerX = this.width / 2;
        int totalHeight = affinities.length * (CARD_HEIGHT + CARD_GAP);
        // Center the full content: header(32) + rows + gap(8) + buttons(20)
        int fullHeight = 32 + totalHeight + 8 + 20;
        int contentTop = (this.height - fullHeight) / 2;
        int startY = contentTop + 32;

        for (int i = 0; i < affinities.length; i++) {
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);
            final int idx = i;

            // [-] refund button — refunds cost 1 XP level each
            boolean canRefund = currentValues[i] > 0 && getTotalRefunded() < playerXpLevels;
            ButtonWidget minusBtn = ButtonWidget.builder(
                Text.literal("§c[-]"),
                button -> {
                    if (currentValues[idx] > 0 && getTotalRefunded() < playerXpLevels) {
                        currentValues[idx]--;
                        currentUnspent++;
                        init();
                    }
                }
            ).dimensions(centerX - CARD_WIDTH / 2, y, BTN_SIZE, CARD_HEIGHT).build();
            minusBtn.active = canRefund;
            this.addDrawableChild(minusBtn);

            // [+] allocate button — draws from the unspent pool
            boolean canAllocate = currentUnspent > 0;
            ButtonWidget plusBtn = ButtonWidget.builder(
                Text.literal("§a[+]"),
                button -> {
                    if (currentUnspent > 0) {
                        currentValues[idx]++;
                        currentUnspent--;
                        init();
                    }
                }
            ).dimensions(centerX + CARD_WIDTH / 2 - BTN_SIZE, y, BTN_SIZE, CARD_HEIGHT).build();
            plusBtn.active = canAllocate;
            this.addDrawableChild(plusBtn);
        }

        // Bottom row: Confirm, Reset, Cancel
        int bottomY = startY + totalHeight + 8;
        int btnWidth = 80;
        int gap = 4;
        int totalBtnWidth = btnWidth * 2 + 50 + gap * 2;
        int btnStartX = centerX - totalBtnWidth / 2;

        boolean canConfirm = hasAnyChanges();
        confirmButton = ButtonWidget.builder(
            Text.literal(canConfirm ? "§aConfirm" : "§8Confirm"),
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
        ).dimensions(btnStartX, bottomY, btnWidth, 20).build();
        confirmButton.active = canConfirm;
        this.addDrawableChild(confirmButton);

        ButtonWidget resetBtn = ButtonWidget.builder(
            Text.literal("§eReset"),
            button -> {
                for (int i = 0; i < originalValues.length; i++) {
                    currentValues[i] = originalValues[i];
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
        context.fill(0, 0, this.width, this.height, 0xE0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int centerX = this.width / 2;
        int totalHeight = affinities.length * (CARD_HEIGHT + CARD_GAP);
        int fullHeight = 32 + totalHeight + 8 + 20;
        int contentTop = (this.height - fullHeight) / 2;
        int startY = contentTop + 32;

        // Header
        int headerY = contentTop;
        context.drawCenteredTextWithShadow(this.textRenderer,
            "§6§l✦ RESPEC AFFINITIES ✦", centerX, headerY, 0xFFAA00);

        int totalRefunded = getTotalRefunded();
        String costText = totalRefunded > 0
            ? "§cCost: " + totalRefunded + " XP Level" + (totalRefunded != 1 ? "s" : "")
              + " §7(You have §a" + playerXpLevels + "§7)"
            : "§7Spend unspent points, or refund to reallocate";
        context.drawCenteredTextWithShadow(this.textRenderer, costText, centerX, headerY + 11, 0xAAAAAA);

        String unspentText = currentUnspent > 0
            ? "§a" + currentUnspent + " affinity point" + (currentUnspent != 1 ? "s" : "") + " to spend"
            : "§7No unspent affinity points";
        context.drawCenteredTextWithShadow(this.textRenderer, unspentText, centerX, headerY + 22, 0xAAAAAA);

        // Affinity rows: labels between the [-] and [+] buttons
        for (int i = 0; i < affinities.length; i++) {
            PlayerProgression.Affinity affinity = affinities[i];
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);
            int labelX = centerX - CARD_WIDTH / 2 + BTN_SIZE + 8;
            int labelY = y + (CARD_HEIGHT - 8) / 2;

            // Affinity name + icon
            String label = affinity.icon + " " + affinity.displayName;
            context.drawTextWithShadow(this.textRenderer, label, labelX, labelY, 0xFFFFFF);

            // Current value + delta indicator
            int affDelta = currentValues[i] - originalValues[i];
            String valueStr = "§f" + currentValues[i];
            if (affDelta > 0) {
                valueStr += " §a(+" + affDelta + ")";
            } else if (affDelta < 0) {
                valueStr += " §c(" + affDelta + ")";
            }
            int valueX = centerX + CARD_WIDTH / 2 - BTN_SIZE - 8 - this.textRenderer.getWidth(
                currentValues[i] + (affDelta != 0 ? " (" + (affDelta > 0 ? "+" : "") + affDelta + ")" : ""));
            context.drawTextWithShadow(this.textRenderer, valueStr, valueX, labelY, 0xFFFFFF);

            // Hover description
            if (mouseX >= centerX - CARD_WIDTH / 2 && mouseX <= centerX + CARD_WIDTH / 2
                && mouseY >= y && mouseY <= y + CARD_HEIGHT) {
                context.drawCenteredTextWithShadow(this.textRenderer,
                    "§7" + affinity.description,
                    centerX, startY + affinities.length * (CARD_HEIGHT + CARD_GAP) + 30, 0x888888);
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
