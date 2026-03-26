package com.crackedgames.craftics.client;

import com.crackedgames.craftics.network.TraderBuyPayload;
import com.crackedgames.craftics.network.TraderDonePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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

    public TraderScreen(String traderName, String traderIcon, String tradeData, int playerEmeralds) {
        super(Text.literal("Wandering Trader"));
        this.traderName = traderName;
        this.traderIcon = traderIcon;
        this.tradeEntries = tradeData.isEmpty() ? new String[0] : tradeData.split("\\|");
        this.playerEmeralds = playerEmeralds;
    }

    public void updateEmeralds(int newEmeralds) {
        this.playerEmeralds = newEmeralds;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;

        // Clear existing buttons on re-init
        this.clearChildren();

        // Trade buttons
        for (int i = 0; i < tradeEntries.length; i++) {
            String[] parts = tradeEntries[i].split("~", 4);
            if (parts.length < 4) continue;

            int count = Integer.parseInt(parts[1]);
            int cost = Integer.parseInt(parts[2]);
            String desc = parts[3];

            String label = desc + " §7- §a" + cost + " emeralds";
            boolean canAfford = playerEmeralds >= cost;

            final int tradeIndex = i;
            ButtonWidget btn = ButtonWidget.builder(
                Text.literal((canAfford ? "§f" : "§8") + label),
                button -> {
                    ClientPlayNetworking.send(new TraderBuyPayload(tradeIndex));
                }
            ).dimensions(centerX - 160, startY + 30 + (i * 25), 320, 20).build();

            btn.active = canAfford;
            this.addDrawableChild(btn);
        }

        // Done button
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("§e✓ Done Trading"),
            btn -> {
                ClientPlayNetworking.send(new TraderDonePayload());
                this.close();
            }
        ).dimensions(centerX - 75, startY + 30 + (tradeEntries.length * 25) + 15, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer,
            traderIcon + " §e§l" + traderName, centerX, startY - 10, 0xFFFFFF);

        // Subtitle
        context.drawCenteredTextWithShadow(this.textRenderer,
            "§7A wandering trader offers their wares...", centerX, startY + 5, 0xAAAAAA);

        // Emerald count
        context.drawCenteredTextWithShadow(this.textRenderer,
            "§aEmeralds: " + playerEmeralds, centerX, startY + 18, 0x55FF55);
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
