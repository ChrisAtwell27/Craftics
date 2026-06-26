package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.network.AffinityChoicePayload;
import com.crackedgames.craftics.network.StatChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
    private static final int CARD_WIDTH  = 320;
    private static final int CARD_HEIGHT = 24;
    private static final int CARD_GAP    = 4;

    // Panel sizing
    private static final int PANEL_W    = CARD_WIDTH + 32;
    private static final int HEADER_H   = 45;   // lines above the card list
    private static final int FOOTER_H   = 20;   // description tooltip row below cards
    private static final int PANEL_PAD  = 12;   // top/bottom padding inside panel

    public LevelUpScreen(int playerLevel, int unspentPoints, String statData) {
        super(Text.literal("Level Up!"));
        this.playerLevel    = playerLevel;
        this.unspentPoints  = unspentPoints;
        this.phase = (playerLevel % 2 == 0) ? Phase.STAT_CHOICE : Phase.AFFINITY_CHOICE;

        String[] parts = statData.split(":");
        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        this.statValues = new int[stats.length];
        for (int i = 0; i < stats.length && i < parts.length; i++) {
            this.statValues[i] = Integer.parseInt(parts[i]);
        }
    }

    // -------------------------------------------------------------------------
    // Layout helpers (shared between init() and render())
    // -------------------------------------------------------------------------

    private int cardCount() {
        return phase == Phase.STAT_CHOICE
            ? PlayerProgression.Stat.values().length
            : PlayerProgression.Affinity.values().length;
    }

    private int panelHeight() {
        int cards = cardCount();
        int listH = cards * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
        return PANEL_PAD + HEADER_H + listH + FOOTER_H + PANEL_PAD;
    }

    /** Y of the top-left corner of the parchment panel. */
    private int panelTop() {
        return (this.height - panelHeight()) / 2;
    }

    /** Y where the first card/button starts. */
    private int cardsStartY() {
        return panelTop() + PANEL_PAD + HEADER_H;
    }

    // -------------------------------------------------------------------------
    // init
    // -------------------------------------------------------------------------

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
        int startY  = cardsStartY();

        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);

            String btnText = unspentPoints > 0
                ? "[+] " + stat.icon + " " + stat.displayName
                : stat.icon + " " + stat.displayName;

            final int statIndex = i;
            GuideButton btn = GuideButton.of(
                centerX - CARD_WIDTH / 2, y, CARD_WIDTH, CARD_HEIGHT,
                Text.literal(btnText),
                button -> {
                    if (unspentPoints > 0) {
                        ClientPlayNetworking.send(new StatChoicePayload(statIndex));
                        statValues[statIndex]++;
                        unspentPoints--;
                        this.close();
                    }
                }
            );
            btn.active = unspentPoints > 0;
            this.addDrawableChild(btn);
        }
    }

    private void initAffinityChoice() {
        PlayerProgression.Affinity[] affinities = PlayerProgression.Affinity.values();
        int centerX = this.width / 2;
        int startY  = cardsStartY();

        for (int i = 0; i < affinities.length; i++) {
            PlayerProgression.Affinity affinity = affinities[i];
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);

            String btnText = affinity.icon + " " + affinity.displayName + " - " + affinity.description;

            final int affinityIndex = i;
            GuideButton btn = GuideButton.of(
                centerX - CARD_WIDTH / 2, y, CARD_WIDTH, CARD_HEIGHT,
                Text.literal(btnText),
                button -> {
                    ClientPlayNetworking.send(new AffinityChoicePayload(affinityIndex));
                    this.close();
                }
            );
            this.addDrawableChild(btn);
        }
    }

    // -------------------------------------------------------------------------
    // Background (world dim)
    // -------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xE0101010);
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Draw parchment panel behind everything
        int panelH = panelHeight();
        int panelX = (this.width - PANEL_W) / 2;
        int panelY = panelTop();
        GuideTheme.drawPanel(context, panelX, panelY, PANEL_W, panelH);

        // 2. Buttons (stat/affinity cards) - drawn by super.render()
        super.render(context, mouseX, mouseY, delta);

        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int centerX = this.width / 2;
        int startY  = cardsStartY();

        // 3. Header text
        int headerY = panelY + PANEL_PAD;
        if (phase == Phase.STAT_CHOICE) {
            GuideTheme.drawCentered(context, this.textRenderer,
                "★ LEVEL UP! ★", centerX, headerY, GuideTheme.GOLD);
            GuideTheme.drawCentered(context, this.textRenderer,
                "Level " + playerLevel, centerX, headerY + 14, GuideTheme.INK);
            String pointsText = unspentPoints > 0
                ? unspentPoints + " point" + (unspentPoints != 1 ? "s" : "") + " to spend"
                : "Choose a stat to upgrade!";
            GuideTheme.drawCentered(context, this.textRenderer,
                pointsText, centerX, headerY + 28, GuideTheme.INK_SOFT);
        } else {
            GuideTheme.drawCentered(context, this.textRenderer,
                "⚔ CHOOSE AFFINITY ⚔", centerX, headerY, GuideTheme.GOLD);
            GuideTheme.drawCentered(context, this.textRenderer,
                "Permanent +1 damage & special effect boost", centerX, headerY + 14, GuideTheme.INK);
            GuideTheme.drawCentered(context, this.textRenderer,
                "Each point increases damage and unique ability chance",
                centerX, headerY + 28, GuideTheme.INK_SOFT);
        }

        // 4. Stat value labels + bars (stat choice only)
        if (phase == Phase.STAT_CHOICE) {
            for (int i = 0; i < stats.length; i++) {
                PlayerProgression.Stat stat = stats[i];
                int currentPoints = statValues[i];
                int effective = stat.baseValue + currentPoints;
                int y = startY + i * (CARD_HEIGHT + CARD_GAP);

                // Value display to the right of the card
                int labelX = centerX + CARD_WIDTH / 2 + 8;
                int labelY = y + (CARD_HEIGHT - 8) / 2;
                String valueStr = String.valueOf(effective);
                if (currentPoints > 0) valueStr += " (+" + currentPoints + ")";
                GuideTheme.drawInk(context, this.textRenderer, valueStr, labelX, labelY, GuideTheme.INK_SOFT);

                // Stat bar (small colored fill to the left of the card)
                int barX     = centerX - CARD_WIDTH / 2 - 6;
                int barWidth = 4;
                int maxDisplay = 10;
                int barHeight = Math.min(currentPoints, maxDisplay) * 2;
                if (barHeight > 0) {
                    int barColor = getStatColor(stat);
                    context.fill(barX, y + CARD_HEIGHT - barHeight, barX + barWidth, y + CARD_HEIGHT, barColor);
                }

                // Hover description below card list
                if (mouseX >= centerX - CARD_WIDTH / 2 && mouseX <= centerX + CARD_WIDTH / 2
                        && mouseY >= y && mouseY <= y + CARD_HEIGHT) {
                    int descY = startY + stats.length * (CARD_HEIGHT + CARD_GAP) + 4;
                    GuideTheme.drawCentered(context, this.textRenderer,
                        stat.description, centerX, descY, GuideTheme.INK_FAINT);
                }
            }
        }
    }

    private int getStatColor(PlayerProgression.Stat stat) {
        return switch (stat) {
            case SPEED         -> 0xFF55FFFF;
            case AP            -> 0xFFFFFF55;
            case MELEE_POWER   -> 0xFFFF5555;
            case RANGED_POWER  -> 0xFFFF55FF;
            case VITALITY      -> 0xFF55FF55;
            case DEFENSE       -> 0xFF5555FF;
            case LUCK          -> 0xFFFFAA00;
            case RESOURCEFUL   -> 0xFF00AA00;
        };
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return unspentPoints <= 0;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
