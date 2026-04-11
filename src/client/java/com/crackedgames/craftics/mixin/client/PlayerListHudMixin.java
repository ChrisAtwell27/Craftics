package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add power scores next to player names in the TAB player list.
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    /**
     * After the vanilla player list renders, overlay scores next to each name.
     * We draw directly on top of the rendered tab list.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void craftics$renderScores(DrawContext context, int scaledWindowWidth,
                                        net.minecraft.scoreboard.Scoreboard scoreboard,
                                        net.minecraft.scoreboard.ScoreboardObjective objective,
                                        CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;

        java.util.List<CombatState.ScoreboardEntry> scores = CombatState.getScoreboardEntries();
        if (scores.isEmpty()) return;

        TextRenderer textRenderer = client.textRenderer;

        // Collect the visible player entries
        java.util.List<PlayerListEntry> entries = client.getNetworkHandler()
            .getListedPlayerListEntries().stream()
            .sorted(java.util.Comparator.comparing(e -> e.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
            .limit(80)
            .collect(java.util.stream.Collectors.toList());

        if (entries.isEmpty()) return;

        // Calculate layout metrics matching vanilla's tab list
        int maxNameWidth = 0;
        for (PlayerListEntry entry : entries) {
            int w = textRenderer.getWidth(client.getNetworkHandler().getPlayerListEntry(entry.getProfile().getId()) != null
                ? client.inGameHud.getPlayerListHud().getPlayerName(entry) : Text.literal(entry.getProfile().getName()));
            if (w > maxNameWidth) maxNameWidth = w;
        }

        // Score column width
        int scoreColWidth = 0;
        for (CombatState.ScoreboardEntry se : scores) {
            int w = textRenderer.getWidth(" \u2B50" + se.score());
            if (w > scoreColWidth) scoreColWidth = w;
        }

        int columnWidth = maxNameWidth + scoreColWidth + 13; // name + icon + padding + score
        int columns = Math.max(1, Math.min(4, (scaledWindowWidth - 50) / (columnWidth + 8)));
        int rowsPerColumn = (int) Math.ceil((double) entries.size() / columns);

        int totalWidth = columns * columnWidth + (columns - 1) * 5;
        int headerHeight = 10;
        int startX = (scaledWindowWidth - totalWidth) / 2;
        int startY = headerHeight;

        // Draw score for each entry
        for (int i = 0; i < entries.size(); i++) {
            int col = i / rowsPerColumn;
            int row = i % rowsPerColumn;
            int x = startX + col * (columnWidth + 5);
            int y = startY + row * 9;

            PlayerListEntry entry = entries.get(i);
            String name = entry.getProfile().getName();
            int score = CombatState.getPlayerScore(name);
            if (score < 0) continue;

            // Draw the score right-aligned in the score column
            String scoreText = "\u2B50" + score;
            int scoreWidth = textRenderer.getWidth(scoreText);
            int scoreX = x + columnWidth - scoreWidth - 1;

            // Color based on score ranking
            int rank = -1;
            for (int r = 0; r < scores.size(); r++) {
                if (scores.get(r).name().equals(name)) { rank = r; break; }
            }
            int scoreColor;
            if (rank == 0) scoreColor = 0xFFFFD700; // gold for #1
            else if (rank == 1) scoreColor = 0xFFC0C0C0; // silver for #2
            else if (rank == 2) scoreColor = 0xFFCD7F32; // bronze for #3
            else scoreColor = 0xFFAAAAAA; // gray for rest

            context.drawTextWithShadow(textRenderer, Text.literal(scoreText), scoreX, y, scoreColor);
        }
    }
}
