package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.network.TraderBuyPayload;
import com.crackedgames.craftics.network.TraderDonePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Trading screen shown between levels when a wandering trader appears.
 * Displays trader type, available trades with emerald costs, and a "Done" button.
 */
public class TraderScreen extends Screen {

    private final String traderName;
    private final String traderIcon;
    private final String[] tradeEntries; // "itemId:count:cost:description"
    private int playerEmeralds;

    // Panel layout
    private static final int PANEL_W    = 340;
    private static final int TRADE_H    = 20;
    private static final int TRADE_GAP  = 5;
    private static final int HEADER_H   = 40;  // icon+name, subtitle, emeralds
    private static final int PANEL_PAD  = 12;

    public TraderScreen(String traderName, String traderIcon, String tradeData, int playerEmeralds) {
        super(Text.literal("Wandering Trader"));
        this.traderName    = traderName;
        this.traderIcon    = traderIcon;
        this.tradeEntries  = tradeData.isEmpty() ? new String[0] : tradeData.split("\\|");
        this.playerEmeralds = playerEmeralds;
    }

    public void updateEmeralds(int newEmeralds) {
        this.playerEmeralds = newEmeralds;
    }

    // -------------------------------------------------------------------------
    // Layout helpers
    // -------------------------------------------------------------------------

    private int panelHeight() {
        int listH = tradeEntries.length * (TRADE_H + TRADE_GAP);
        // +TRADE_H for the Done button row + gap before it
        return PANEL_PAD + HEADER_H + listH + TRADE_GAP + TRADE_H + PANEL_PAD;
    }

    private int panelTop() {
        return (this.height - panelHeight()) / 2;
    }

    private int tradesStartY() {
        return panelTop() + PANEL_PAD + HEADER_H;
    }

    // -------------------------------------------------------------------------
    // init
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        this.clearChildren();

        int centerX    = this.width / 2;
        int startY     = tradesStartY();

        for (int i = 0; i < tradeEntries.length; i++) {
            String[] parts = tradeEntries[i].split("~", 4);
            if (parts.length < 4) continue;

            int cost     = Integer.parseInt(parts[2]);
            String desc  = parts[3];
            boolean canAfford = playerEmeralds >= cost;

            String label = desc + " - " + cost + " emeralds";
            final int tradeIndex = i;
            GuideButton btn = GuideButton.of(
                centerX - PANEL_W / 2 + 8, startY + i * (TRADE_H + TRADE_GAP),
                PANEL_W - 16, TRADE_H,
                Text.literal(label),
                button -> ClientPlayNetworking.send(new TraderBuyPayload(tradeIndex))
            );
            btn.active = canAfford;
            this.addDrawableChild(btn);
        }

        int doneY = startY + tradeEntries.length * (TRADE_H + TRADE_GAP) + TRADE_GAP;
        this.addDrawableChild(GuideButton.of(
            centerX - 75, doneY, 150, TRADE_H,
            Text.literal("Done Trading"),
            btn -> {
                ClientPlayNetworking.send(new TraderDonePayload());
                this.close();
            }
        ));
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Parchment panel
        int panelH = panelHeight();
        int panelX = (this.width - PANEL_W) / 2;
        int panelY = panelTop();
        GuideTheme.drawPanel(context, panelX, panelY, PANEL_W, panelH);

        // 2. Buttons
        super.render(context, mouseX, mouseY, delta);

        // 3. Header text
        int centerX = this.width / 2;
        int y       = panelY + PANEL_PAD;

        GuideTheme.drawCentered(context, this.textRenderer,
            traderIcon + " " + traderName, centerX, y, GuideTheme.GOLD);
        GuideTheme.drawCentered(context, this.textRenderer,
            "A wandering trader offers their wares...", centerX, y + 14, GuideTheme.INK_SOFT);
        GuideTheme.drawCentered(context, this.textRenderer,
            "Emeralds: " + playerEmeralds, centerX, y + 28, GuideTheme.INK);

        // 4. Damage affinity panel (already themed - leave as-is)
        com.crackedgames.craftics.client.DamageTypePanel.render(context, this.width, this.height, mouseX, mouseY);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Must click Done
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
