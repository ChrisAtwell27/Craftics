package com.crackedgames.craftics.client;

import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.network.StatChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Level-up screen shown after completing a biome.
 * Displays all stats with current values and lets the player pick one to upgrade.
 * Polished layout with stat bars, icons, and clear descriptions.
 */
public class LevelUpScreen extends Screen {

    private final int playerLevel;
    private int unspentPoints;
    private final int[] statValues; // current allocated points per stat

    // Layout constants
    private static final int CARD_WIDTH = 320;
    private static final int CARD_HEIGHT = 24;
    private static final int CARD_GAP = 4;

    public LevelUpScreen(int playerLevel, int unspentPoints, String statData) {
        super(Text.literal("Level Up!"));
        this.playerLevel = playerLevel;
        this.unspentPoints = unspentPoints;

        // Parse stat data: "s0:s1:s2:s3:s4:s5:s6:s7"
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

        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int centerX = this.width / 2;
        int totalHeight = stats.length * (CARD_HEIGHT + CARD_GAP);
        int startY = (this.height / 2) - (totalHeight / 2) + 20;

        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int currentPoints = statValues[i];
            int effective = stat.baseValue + currentPoints;
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);

            // Stat upgrade button
            String btnText = unspentPoints > 0
                ? "§a[+] " + stat.icon + " " + stat.displayName
                : "§8" + stat.icon + " " + stat.displayName;

            final int statIndex = i;
            ButtonWidget btn = ButtonWidget.builder(
                Text.literal(btnText),
                button -> {
                    if (unspentPoints > 0) {
                        ClientPlayNetworking.send(new StatChoicePayload(statIndex));
                        statValues[statIndex]++;
                        unspentPoints--;
                        init(); // refresh buttons
                        if (unspentPoints <= 0) {
                            // Small delay then close
                            this.close();
                        }
                    }
                }
            ).dimensions(centerX - CARD_WIDTH / 2, y, CARD_WIDTH, CARD_HEIGHT).build();

            btn.active = unspentPoints > 0;
            this.addDrawableChild(btn);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        int centerX = this.width / 2;
        int totalHeight = stats.length * (CARD_HEIGHT + CARD_GAP);
        int startY = (this.height / 2) - (totalHeight / 2) + 20;

        // Header area
        int headerY = startY - 45;

        // Level badge
        context.drawCenteredTextWithShadow(this.textRenderer,
            "§6§l★ LEVEL UP! ★", centerX, headerY, 0xFFAA00);
        context.drawCenteredTextWithShadow(this.textRenderer,
            "§fLevel §e§l" + playerLevel, centerX, headerY + 14, 0xFFFFFF);

        // Points remaining
        String pointsText = unspentPoints > 0
            ? "§a" + unspentPoints + " point" + (unspentPoints != 1 ? "s" : "") + " to spend"
            : "§7All points spent!";
        context.drawCenteredTextWithShadow(this.textRenderer,
            pointsText, centerX, headerY + 28, 0xAAAAAA);

        // Stat value labels (drawn to the right of each button)
        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int currentPoints = statValues[i];
            int effective = stat.baseValue + currentPoints;
            int y = startY + i * (CARD_HEIGHT + CARD_GAP);

            // Value display to the right
            int labelX = centerX + CARD_WIDTH / 2 + 8;
            String valueStr = "§f" + effective;
            if (currentPoints > 0) {
                valueStr += " §7(+" + currentPoints + ")";
            }

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
                // Draw description below the card
                context.drawCenteredTextWithShadow(this.textRenderer,
                    "§7" + stat.description,
                    centerX, startY + stats.length * (CARD_HEIGHT + CARD_GAP) + 8, 0x888888);
            }
        }

        super.render(context, mouseX, mouseY, delta);
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
