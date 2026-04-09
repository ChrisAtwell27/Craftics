package com.crackedgames.craftics.client;

import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.network.AffinityChoicePayload;
import com.crackedgames.craftics.network.StatChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Level-up screen shown after completing a biome.
 * Two steps: (1) pick a stat to upgrade, (2) pick a damage affinity to upgrade.
 */
public class LevelUpScreen extends Screen {

    private final int playerLevel;
    private int unspentPoints;
    private final int[] statValues;

    // Alternating flow: even levels = stat choice, odd levels = affinity choice
    private enum Phase { STAT_CHOICE, AFFINITY_CHOICE }
    private Phase phase;

    // Layout constants
    private static final int CARD_WIDTH = 320;
    private static final int CARD_HEIGHT = 24;
    private static final int CARD_GAP = 4;

    public LevelUpScreen(int playerLevel, int unspentPoints, String statData) {
        super(Text.literal("Level Up!"));
        this.playerLevel = playerLevel;
        this.unspentPoints = unspentPoints;
        // Determine which choice to show based on level parity
        this.phase = (playerLevel % 2 == 0) ? Phase.STAT_CHOICE : Phase.AFFINITY_CHOICE;

        String[] parts = statData.split(":");
        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        this.statValues = new int[stats.length];
        for (int i = 0; i < stats.length && i < parts.length; i++) {
            this.statValues[i] = Integer.parseInt(parts[i]);
        }
    }

    @Override
    protected void init() {
        this.clearChildren();

        if (phase == Phase.STAT_CHOICE) {
            initStatChoice();
        } else {
            initAffinityChoice();
        }
    }

    private void initStatChoice() {
        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int centerX = this.width / 2;
        int totalHeight = stats.length * (CARD_HEIGHT + CARD_GAP);
        int startY = (this.height / 2) - (totalHeight / 2);

        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int currentPoints = statValues[i];
            int effective = stat.baseValue + currentPoints;
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);

            String btnText = unspentPoints > 0
                ? "\u00a7a[+] " + stat.icon + " " + stat.displayName
                : "\u00a78" + stat.icon + " " + stat.displayName;

            final int statIndex = i;
            ButtonWidget btn = ButtonWidget.builder(
                Text.literal(btnText),
                button -> {
                    if (unspentPoints > 0) {
                        ClientPlayNetworking.send(new StatChoicePayload(statIndex));
                        statValues[statIndex]++;
                        unspentPoints--;
                        this.close();
                    }
                }
            ).dimensions(centerX - CARD_WIDTH / 2, y, CARD_WIDTH, CARD_HEIGHT).build();

            btn.active = unspentPoints > 0;
            this.addDrawableChild(btn);
        }
    }

    private void initAffinityChoice() {
        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int centerX = this.width / 2;
        int totalHeight = affinities.length * (CARD_HEIGHT + CARD_GAP);
        int startY = (this.height / 2) - (totalHeight / 2);

        for (int i = 0; i < affinities.length; i++) {
            PlayerProgression.Affinity affinity = affinities[i];
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);

            String btnText = affinity.icon + " " + affinity.displayName + " \u00a77" + affinity.description;

            final int affinityIndex = i;
            ButtonWidget btn = ButtonWidget.builder(
                Text.literal(btnText),
                button -> {
                    ClientPlayNetworking.send(new AffinityChoicePayload(affinityIndex));
                    this.close();
                }
            ).dimensions(centerX - CARD_WIDTH / 2, y, CARD_WIDTH, CARD_HEIGHT).build();

            this.addDrawableChild(btn);
        }
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
        int startY = (this.height / 2) - (totalHeight / 2);

        // Header area
        int headerY = startY - 45;

        if (phase == Phase.STAT_CHOICE) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00a76\u00a7l\u2605 LEVEL UP! \u2605", centerX, headerY, 0xFFAA00);
            context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00a7fLevel \u00a7e\u00a7l" + playerLevel, centerX, headerY + 14, 0xFFFFFF);
            String pointsText = unspentPoints > 0
                ? "\u00a7a" + unspentPoints + " point" + (unspentPoints != 1 ? "s" : "") + " to spend"
                : "\u00a77Choose a stat to upgrade!";
            context.drawCenteredTextWithShadow(this.textRenderer,
                pointsText, centerX, headerY + 28, 0xAAAAAA);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00a76\u00a7l\u2694 CHOOSE AFFINITY \u2694", centerX, headerY, 0xFFAA00);
            context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00a7fPermanent +1 damage & special effect boost", centerX, headerY + 14, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00a77Each point increases damage and unique ability chance", centerX, headerY + 28, 0xAAAAAA);
        }

        // Stat value labels (drawn to the right of each button)
        if (phase == Phase.STAT_CHOICE) {
            for (int i = 0; i < stats.length; i++) {
                PlayerProgression.Stat stat = stats[i];
                int currentPoints = statValues[i];
                int effective = stat.baseValue + currentPoints;
                int y = startY + i * (CARD_HEIGHT + CARD_GAP);

                // Value display to the right
                int labelX = centerX + CARD_WIDTH / 2 + 8;
                int labelY = y + (CARD_HEIGHT - 8) / 2;
                String valueStr = "\u00a7f" + effective;
                if (currentPoints > 0) {
                    valueStr += " \u00a77(+" + currentPoints + ")";
                }
                context.drawTextWithShadow(this.textRenderer, valueStr, labelX, labelY, 0xFFFFFF);

                // Draw stat bar (visual indicator of points)
                int barX = centerX - CARD_WIDTH / 2 - 6;
                int barWidth = 4;
                int maxDisplay = 10;
                int barHeight = Math.min(currentPoints, maxDisplay) * 2;
                if (barHeight > 0) {
                    int barColor = getStatColor(stat);
                    context.fill(barX, y + CARD_HEIGHT - barHeight, barX + barWidth, y + CARD_HEIGHT, barColor);
                }

                // Description tooltip on hover
                if (mouseX >= centerX - CARD_WIDTH / 2 && mouseX <= centerX + CARD_WIDTH / 2
                    && mouseY >= y && mouseY <= y + CARD_HEIGHT) {
                    context.drawCenteredTextWithShadow(this.textRenderer,
                        "\u00a77" + stat.description,
                        centerX, startY + stats.length * (CARD_HEIGHT + CARD_GAP) + 8, 0x888888);
                }
            }
        }
    }

    private int getStatColor(PlayerProgression.Stat stat) {
        return switch (stat) {
            case SPEED -> 0xFF55FFFF;
            case AP -> 0xFFFFFF55;
            case MELEE_POWER -> 0xFFFF5555;
            case RANGED_POWER -> 0xFFFF55FF;
            case VITALITY -> 0xFF55FF55;
            case DEFENSE -> 0xFF5555FF;
            case LUCK -> 0xFFFFAA00;
            case RESOURCEFUL -> 0xFF00AA00;
        };
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return unspentPoints <= 0; // Can only close once all points are spent
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
